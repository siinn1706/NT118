"""Friends service: friend requests, friend list, people search and realtime fanout.

Business rules:
  * One `friendships` row per unordered user pair (unique LEAST/GREATEST index).
  * Sending a request when the other side already has a pending request to you
    accepts it instead of creating a second one.
  * A declined request may be re-sent: the same row flips back to pending with
    the new sender as requester.
  * Accepting a request ensures a direct conversation exists with both members
    accepted, so friends never go through the stranger-request flow.
"""
from __future__ import annotations

import datetime
import logging
import uuid
from typing import Literal

from fastapi import status
from sqlalchemy import and_, case, delete, func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.exceptions import ApiException
from app.models.core import (
    Conversation,
    ConversationMember,
    ConversationTypeEnum,
    User,
    UserPreference,
)
from app.models.social import Friendship, FriendshipStatusEnum
from app.schemas.social import (
    FriendDTO,
    FriendRequestDTO,
    FriendSearchResult,
    FriendUserDTO,
)
from app.services import chat as chat_svc
from app.services import notifications as notifications_svc
from app.services.user_visibility import LIKE_ESCAPE_CHAR, discoverable_user_clause, escape_like
from app.ws.handlers import manager as ws_manager

logger = logging.getLogger(__name__)

PENDING = FriendshipStatusEnum.PENDING.value
ACCEPTED = FriendshipStatusEnum.ACCEPTED.value
DECLINED = FriendshipStatusEnum.DECLINED.value
BLOCKED = FriendshipStatusEnum.BLOCKED.value

NOTIFICATION_KIND_SOCIAL = "social"
SEARCH_LIMIT_MAX = 20

_NOTIFICATION_TEXT = {
    "en": {
        "received_title": "New friend request",
        "received_body": "{name} sent you a friend request.",
        "accepted_title": "Friend request accepted",
        "accepted_body": "{name} accepted your friend request.",
    },
    "vi": {
        "received_title": "Lời mời kết bạn mới",
        "received_body": "{name} đã gửi cho bạn lời mời kết bạn.",
        "accepted_title": "Đã chấp nhận lời mời kết bạn",
        "accepted_body": "{name} đã chấp nhận lời mời kết bạn của bạn.",
    },
}


# ──────────────────────────────────────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────────────────────────────────────

def _now() -> datetime.datetime:
    return datetime.datetime.now(datetime.timezone.utc)


def _pair_clause(a: uuid.UUID, b: uuid.UUID):
    return or_(
        and_(Friendship.requester_id == a, Friendship.addressee_id == b),
        and_(Friendship.requester_id == b, Friendship.addressee_id == a),
    )


def _other_id_expr(me: uuid.UUID):
    return case(
        (Friendship.requester_id == me, Friendship.addressee_id),
        else_=Friendship.requester_id,
    )


def _user_dto(user: User, presence: dict[str, bool] | None = None) -> FriendUserDTO:
    profile = getattr(user, "profile", None)
    return FriendUserDTO(
        id=user.id,
        username=user.username,
        display_name=user.display_name,
        avatar_url=profile.avatar_url if profile else None,
        is_online=bool((presence or {}).get(str(user.id), False)),
    )


def _request_dto(
    friendship: Friendship,
    viewer_id: uuid.UUID,
    other: User,
    presence: dict[str, bool] | None = None,
) -> FriendRequestDTO:
    return FriendRequestDTO(
        id=friendship.id,
        status=friendship.status,
        direction="outgoing" if friendship.requester_id == viewer_id else "incoming",
        user=_user_dto(other, presence),
        created_at=friendship.created_at,
    )


def _not_found_request() -> ApiException:
    return ApiException(
        status_code=status.HTTP_404_NOT_FOUND,
        code="FRIEND_REQUEST_NOT_FOUND",
        message="Friend request not found.",
    )


async def _get_pair(db: AsyncSession, a: uuid.UUID, b: uuid.UUID) -> Friendship | None:
    result = await db.execute(select(Friendship).where(_pair_clause(a, b)))
    return result.scalar_one_or_none()


async def _load_user(db: AsyncSession, user_id: uuid.UUID) -> User | None:
    result = await db.execute(
        select(User).options(selectinload(User.profile)).where(User.id == user_id)
    )
    return result.scalar_one_or_none()


async def _resolve_target(
    db: AsyncSession,
    *,
    username: str | None,
    user_id: uuid.UUID | None,
) -> User:
    stmt = select(User).options(selectinload(User.profile)).where(discoverable_user_clause())
    if user_id is not None:
        stmt = stmt.where(User.id == user_id)
    else:
        stmt = stmt.where(func.lower(User.username) == (username or "").strip().lower())
    result = await db.execute(stmt)
    target = result.scalar_one_or_none()
    if target is None:
        raise ApiException(
            status_code=status.HTTP_404_NOT_FOUND,
            code="USER_NOT_FOUND",
            message="User not found.",
        )
    return target


async def _direct_conversation_ids(
    db: AsyncSession,
    me: uuid.UUID,
    other_ids: list[uuid.UUID],
) -> dict[uuid.UUID, uuid.UUID]:
    """Map friend id -> direct conversation id shared with `me` (same lookup as chat)."""
    if not other_ids:
        return {}
    mine = ConversationMember.__table__.alias("mine")
    theirs = ConversationMember.__table__.alias("theirs")
    result = await db.execute(
        select(theirs.c.user_id, Conversation.id)
        .select_from(Conversation)
        .join(mine, and_(mine.c.conversation_id == Conversation.id, mine.c.user_id == me))
        .join(theirs, theirs.c.conversation_id == Conversation.id)
        .where(
            Conversation.type == ConversationTypeEnum.DIRECT,
            theirs.c.user_id.in_(other_ids),
        )
        .order_by(Conversation.created_at)
    )
    mapping: dict[uuid.UUID, uuid.UUID] = {}
    for other_id, conversation_id in result.all():
        mapping.setdefault(other_id, conversation_id)
    return mapping


async def _accept(db: AsyncSession, friendship: Friendship, accepter_id: uuid.UUID) -> uuid.UUID:
    """Mark accepted and make sure both users share an accepted direct conversation."""
    friendship.status = ACCEPTED
    friendship.responded_at = _now()
    await db.flush()
    other_id = (
        friendship.requester_id if friendship.addressee_id == accepter_id else friendship.addressee_id
    )
    conversation = await chat_svc.create_direct_conversation(db, accepter_id, other_id)
    return conversation.id


# ──────────────────────────────────────────────────────────────────────────────
# Queries
# ──────────────────────────────────────────────────────────────────────────────

async def are_friends(db: AsyncSession, a: uuid.UUID, b: uuid.UUID) -> bool:
    if a == b:
        return False
    result = await db.execute(
        select(Friendship.id).where(_pair_clause(a, b), Friendship.status == ACCEPTED).limit(1)
    )
    return result.scalar_one_or_none() is not None


async def friend_ids(db: AsyncSession, me: uuid.UUID) -> list[uuid.UUID]:
    """Ids of accepted friends (no visibility filtering)."""
    other = _other_id_expr(me)
    result = await db.execute(
        select(other).where(
            or_(Friendship.requester_id == me, Friendship.addressee_id == me),
            Friendship.status == ACCEPTED,
        )
    )
    return [row[0] for row in result.all()]


async def list_friends(
    db: AsyncSession,
    me: uuid.UUID,
    presence: dict[str, bool] | None = None,
) -> list[FriendDTO]:
    other = _other_id_expr(me)
    result = await db.execute(
        select(Friendship, User)
        .join(User, User.id == other)
        .options(selectinload(User.profile))
        .where(
            or_(Friendship.requester_id == me, Friendship.addressee_id == me),
            Friendship.status == ACCEPTED,
            discoverable_user_clause(),
        )
        .order_by(func.lower(User.display_name), User.id)
    )
    rows = result.all()
    conversations = await _direct_conversation_ids(db, me, [user.id for _, user in rows])
    return [
        FriendDTO(
            user=_user_dto(user, presence),
            since=friendship.responded_at or friendship.updated_at or friendship.created_at,
            direct_conversation_id=conversations.get(user.id),
        )
        for friendship, user in rows
    ]


async def list_requests(
    db: AsyncSession,
    me: uuid.UUID,
    direction: Literal["incoming", "outgoing"],
    presence: dict[str, bool] | None = None,
) -> list[FriendRequestDTO]:
    if direction == "incoming":
        mine, other_col = Friendship.addressee_id, Friendship.requester_id
    else:
        mine, other_col = Friendship.requester_id, Friendship.addressee_id
    result = await db.execute(
        select(Friendship, User)
        .join(User, User.id == other_col)
        .options(selectinload(User.profile))
        .where(mine == me, Friendship.status == PENDING, discoverable_user_clause())
        .order_by(Friendship.created_at.desc(), Friendship.id)
    )
    return [_request_dto(friendship, me, user, presence) for friendship, user in result.all()]


async def search_users(
    db: AsyncSession,
    me: uuid.UUID,
    query: str,
    limit: int = 10,
) -> list[FriendSearchResult]:
    """Username exact-or-prefix search (case-insensitive). Never matches or returns email."""
    term = query.strip().lower()
    if not term:
        return []
    limit = max(1, min(limit, SEARCH_LIMIT_MAX))
    username_lower = func.lower(User.username)
    result = await db.execute(
        select(User)
        .options(selectinload(User.profile))
        .where(
            User.id != me,
            discoverable_user_clause(),
            username_lower.like(f"{escape_like(term)}%", escape=LIKE_ESCAPE_CHAR),
        )
        .order_by((username_lower == term).desc(), username_lower)
        .limit(limit)
    )
    users = result.scalars().all()
    if not users:
        return []

    ids = [u.id for u in users]
    rel_result = await db.execute(
        select(Friendship).where(
            or_(
                and_(Friendship.requester_id == me, Friendship.addressee_id.in_(ids)),
                and_(Friendship.addressee_id == me, Friendship.requester_id.in_(ids)),
            )
        )
    )
    relations: dict[uuid.UUID, Friendship] = {}
    for friendship in rel_result.scalars().all():
        other_id = friendship.addressee_id if friendship.requester_id == me else friendship.requester_id
        relations[other_id] = friendship

    out: list[FriendSearchResult] = []
    for user in users:
        friendship = relations.get(user.id)
        rel_status: str | None = None
        request_id: uuid.UUID | None = None
        if friendship is not None:
            if friendship.status == ACCEPTED:
                rel_status = "friends"
            elif friendship.status == PENDING:
                rel_status = "outgoing" if friendship.requester_id == me else "incoming"
                request_id = friendship.id
        profile = user.profile
        out.append(
            FriendSearchResult(
                id=user.id,
                username=user.username,
                display_name=user.display_name,
                avatar_url=profile.avatar_url if profile else None,
                friendship_status=rel_status,
                request_id=request_id,
            )
        )
    return out


# ──────────────────────────────────────────────────────────────────────────────
# Commands (caller commits)
# ──────────────────────────────────────────────────────────────────────────────

async def send_request(
    db: AsyncSession,
    requester_id: uuid.UUID,
    *,
    username: str | None = None,
    user_id: uuid.UUID | None = None,
) -> tuple[Friendship, bool]:
    """Send (or re-send) a friend request.

    Returns ``(friendship, auto_accepted)``. ``auto_accepted`` is
    True when the target already had a pending request to the requester.
    """
    if user_id is not None and user_id == requester_id:
        raise _self_error()
    target = await _resolve_target(db, username=username, user_id=user_id)
    if target.id == requester_id:
        raise _self_error()

    existing = await _get_pair(db, requester_id, target.id)
    if existing is None:
        friendship = Friendship(
            requester_id=requester_id,
            addressee_id=target.id,
            status=PENDING,
        )
        try:
            async with db.begin_nested():
                db.add(friendship)
                await db.flush()
        except IntegrityError:
            # Lost a race with a concurrent request for the same pair.
            existing = await _get_pair(db, requester_id, target.id)
            if existing is None:
                raise
        else:
            await db.refresh(friendship)
            return friendship, False

    if existing.status == ACCEPTED:
        raise ApiException(
            status_code=status.HTTP_409_CONFLICT,
            code="FRIEND_ALREADY",
            message="You are already friends.",
        )
    if existing.status == BLOCKED:
        raise ApiException(
            status_code=status.HTTP_403_FORBIDDEN,
            code="FRIEND_BLOCKED",
            message="You cannot send a friend request to this user.",
        )
    if existing.status == PENDING:
        if existing.requester_id == requester_id:
            raise ApiException(
                status_code=status.HTTP_409_CONFLICT,
                code="FRIEND_REQUEST_EXISTS",
                message="A friend request is already pending.",
            )
        await _accept(db, existing, requester_id)
        await db.refresh(existing)
        return existing, True

    # Declined earlier: re-open the same row with the new sender as requester.
    existing.requester_id = requester_id
    existing.addressee_id = target.id
    existing.status = PENDING
    existing.responded_at = None
    existing.created_at = _now()
    await db.flush()
    await db.refresh(existing)
    return existing, False


def _self_error() -> ApiException:
    return ApiException(
        status_code=status.HTTP_400_BAD_REQUEST,
        code="FRIEND_SELF",
        message="You cannot send a friend request to yourself.",
    )


async def accept_request(
    db: AsyncSession,
    request_id: uuid.UUID,
    me: uuid.UUID,
) -> tuple[Friendship, uuid.UUID]:
    """Addressee accepts a pending request. Returns (friendship, direct_conversation_id)."""
    result = await db.execute(
        select(Friendship).where(
            Friendship.id == request_id,
            Friendship.addressee_id == me,
            Friendship.status == PENDING,
        )
    )
    friendship = result.scalar_one_or_none()
    if friendship is None:
        raise _not_found_request()
    conversation_id = await _accept(db, friendship, me)
    await db.refresh(friendship)
    return friendship, conversation_id


async def decline_request(db: AsyncSession, request_id: uuid.UUID, me: uuid.UUID) -> None:
    result = await db.execute(
        select(Friendship).where(
            Friendship.id == request_id,
            Friendship.addressee_id == me,
            Friendship.status == PENDING,
        )
    )
    friendship = result.scalar_one_or_none()
    if friendship is None:
        raise _not_found_request()
    friendship.status = DECLINED
    friendship.responded_at = _now()
    await db.flush()


async def cancel_request(db: AsyncSession, request_id: uuid.UUID, me: uuid.UUID) -> None:
    result = await db.execute(
        delete(Friendship)
        .where(
            Friendship.id == request_id,
            Friendship.requester_id == me,
            Friendship.status == PENDING,
        )
        .returning(Friendship.id)
    )
    if result.scalar_one_or_none() is None:
        raise _not_found_request()


async def unfriend(db: AsyncSession, me: uuid.UUID, other_id: uuid.UUID) -> None:
    result = await db.execute(
        delete(Friendship)
        .where(_pair_clause(me, other_id), Friendship.status == ACCEPTED)
        .returning(Friendship.id)
    )
    if result.scalar_one_or_none() is None:
        raise ApiException(
            status_code=status.HTTP_404_NOT_FOUND,
            code="FRIEND_NOT_FOUND",
            message="This user is not in your friend list.",
        )


async def build_friend_dto(
    db: AsyncSession,
    friendship: Friendship,
    viewer_id: uuid.UUID,
    presence: dict[str, bool] | None = None,
) -> FriendDTO:
    other_id = (
        friendship.addressee_id if friendship.requester_id == viewer_id else friendship.requester_id
    )
    other = await _load_user(db, other_id)
    if other is None:  # pragma: no cover - FK cascade removes the row first
        raise _not_found_request()
    conversations = await _direct_conversation_ids(db, viewer_id, [other_id])
    return FriendDTO(
        user=_user_dto(other, presence),
        since=friendship.responded_at or friendship.updated_at or friendship.created_at,
        direct_conversation_id=conversations.get(other_id),
    )


async def build_request_dto(
    db: AsyncSession,
    friendship: Friendship,
    viewer_id: uuid.UUID,
    presence: dict[str, bool] | None = None,
) -> FriendRequestDTO:
    other_id = (
        friendship.addressee_id if friendship.requester_id == viewer_id else friendship.requester_id
    )
    other = await _load_user(db, other_id)
    if other is None:  # pragma: no cover - FK cascade removes the row first
        raise _not_found_request()
    return _request_dto(friendship, viewer_id, other, presence)


# ──────────────────────────────────────────────────────────────────────────────
# Realtime + in-app notifications (call after the main transaction commits)
# ──────────────────────────────────────────────────────────────────────────────

def _frame(event: str, payload: dict) -> dict:
    return {
        "event": event,
        "payload": payload,
        "timestamp": _now().isoformat(),
    }


async def _recipient_locale(db: AsyncSession, user_id: uuid.UUID) -> str:
    result = await db.execute(
        select(UserPreference.locale).where(UserPreference.user_id == user_id)
    )
    locale = result.scalar_one_or_none()
    return locale if locale in _NOTIFICATION_TEXT else "en"


async def _notify_in_app(
    db: AsyncSession,
    recipient_id: uuid.UUID,
    *,
    kind_key: Literal["received", "accepted"],
    actor_name: str,
    reference_id: uuid.UUID,
) -> None:
    try:
        text = _NOTIFICATION_TEXT[await _recipient_locale(db, recipient_id)]
        await notifications_svc.enqueue(
            db,
            recipient_id,
            title=text[f"{kind_key}_title"],
            body=text[f"{kind_key}_body"].format(name=actor_name),
            kind=NOTIFICATION_KIND_SOCIAL,
            reference_id=reference_id,
            commit=True,
        )
    except Exception as exc:  # best effort: the friendship is already committed
        await db.rollback()
        logger.warning("friend in-app notification failed: %s", type(exc).__name__)


async def notify_request_received(db: AsyncSession, friendship: Friendship) -> None:
    """Tell the addressee about a new (or re-sent) request: WS frame + inbox item."""
    addressee_id = friendship.addressee_id
    requester = await _load_user(db, friendship.requester_id)
    if requester is None:
        return
    presence = ws_manager.get_presence_map()
    dto = _request_dto(friendship, addressee_id, requester, presence)
    try:
        await ws_manager.send_to_user(
            str(addressee_id),
            _frame("friend.request.received", dto.model_dump(mode="json")),
        )
    except Exception as exc:
        logger.debug("friend.request.received fanout failed: %s", type(exc).__name__)
    await _notify_in_app(
        db,
        addressee_id,
        kind_key="received",
        actor_name=requester.display_name,
        reference_id=friendship.id,
    )


async def notify_request_accepted(
    db: AsyncSession,
    friendship: Friendship,
    direct_conversation_id: uuid.UUID | None = None,
) -> None:
    """Tell the original requester that the addressee accepted: WS frame + inbox item."""
    requester_id = friendship.requester_id
    accepter = await _load_user(db, friendship.addressee_id)
    if accepter is None:
        return
    if direct_conversation_id is None:
        conversations = await _direct_conversation_ids(db, requester_id, [accepter.id])
        direct_conversation_id = conversations.get(accepter.id)
    presence = ws_manager.get_presence_map()
    friend = FriendDTO(
        user=_user_dto(accepter, presence),
        since=friendship.responded_at or friendship.updated_at,
        direct_conversation_id=direct_conversation_id,
    )
    try:
        await ws_manager.send_to_user(
            str(requester_id),
            _frame(
                "friend.request.accepted",
                {"request_id": str(friendship.id), "friend": friend.model_dump(mode="json")},
            ),
        )
    except Exception as exc:
        logger.debug("friend.request.accepted fanout failed: %s", type(exc).__name__)
    await _notify_in_app(
        db,
        requester_id,
        kind_key="accepted",
        actor_name=accepter.display_name,
        reference_id=friendship.id,
    )
