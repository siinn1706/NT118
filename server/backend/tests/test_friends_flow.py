"""Integration tests for the friends service against a real Postgres.

Covers request/accept/list, reverse-pending auto-accept, the unordered-pair
unique index, declined re-send, cancel/unfriend, direct conversations between
friends skipping the stranger flow, and people search exclusions.
"""
from __future__ import annotations

import datetime
import uuid

import pytest
import pytest_asyncio
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.adapters.database import AsyncSessionLocal, engine
from app.exceptions import ApiException
from app.models.core import Conversation, ConversationMember, User
from app.models.social import Friendship
from app.services import chat as chat_svc
from app.services import friends as friends_svc

pytestmark = pytest.mark.skipif(
    not __import__("app.core.config", fromlist=["settings"]).settings.database_url,
    reason="needs real Postgres",
)


def _token() -> str:
    return uuid.uuid4().hex[:10]


async def _create_user(
    db: AsyncSession,
    ids: dict[str, list[uuid.UUID]],
    *,
    username: str | None = None,
    **extra,
) -> User:
    tag = _token()
    user = User(
        email=f"friends-{tag}@test.local",
        username=username or f"fr{tag}",
        display_name=f"Friend {tag}",
        hashed_password="x",
        has_password=True,
        **extra,
    )
    db.add(user)
    await db.flush()
    ids["users"].append(user.id)
    return user


@pytest_asyncio.fixture
async def pg_db():
    ids: dict[str, list[uuid.UUID]] = {"users": []}
    async with AsyncSessionLocal() as db:
        yield db, ids
    if ids["users"]:
        async with AsyncSessionLocal() as cleanup:
            conv_ids = (
                await cleanup.execute(
                    select(ConversationMember.conversation_id).where(
                        ConversationMember.user_id.in_(ids["users"])
                    )
                )
            ).scalars().all()
            if conv_ids:
                await cleanup.execute(delete(Conversation).where(Conversation.id.in_(conv_ids)))
            await cleanup.execute(delete(User).where(User.id.in_(ids["users"])))
            await cleanup.commit()
    await engine.dispose()


async def _members(db: AsyncSession, conversation_id: uuid.UUID) -> dict[uuid.UUID, bool]:
    rows = await db.execute(
        select(ConversationMember.user_id, ConversationMember.is_accepted).where(
            ConversationMember.conversation_id == conversation_id
        )
    )
    return {user_id: accepted for user_id, accepted in rows.all()}


async def test_request_accept_and_list_friends(pg_db):
    db, ids = pg_db
    alice = await _create_user(db, ids)
    bob = await _create_user(db, ids)
    await db.commit()

    friendship, auto = await friends_svc.send_request(db, alice.id, username=bob.username.upper())
    await db.commit()
    assert auto is False
    assert friendship.status == "pending"
    assert (friendship.requester_id, friendship.addressee_id) == (alice.id, bob.id)

    incoming = await friends_svc.list_requests(db, bob.id, "incoming")
    outgoing = await friends_svc.list_requests(db, alice.id, "outgoing")
    assert [r.id for r in incoming] == [friendship.id]
    assert incoming[0].direction == "incoming" and incoming[0].user.id == alice.id
    assert [r.id for r in outgoing] == [friendship.id]
    assert outgoing[0].direction == "outgoing" and outgoing[0].user.id == bob.id

    accepted, conversation_id = await friends_svc.accept_request(db, friendship.id, bob.id)
    await db.commit()
    assert accepted.status == "accepted"
    assert accepted.responded_at is not None
    assert await friends_svc.are_friends(db, alice.id, bob.id)
    assert await friends_svc.are_friends(db, bob.id, alice.id)

    friends_of_alice = await friends_svc.list_friends(db, alice.id, {str(bob.id): True})
    assert [f.user.id for f in friends_of_alice] == [bob.id]
    assert friends_of_alice[0].user.is_online is True
    assert friends_of_alice[0].direct_conversation_id == conversation_id
    assert await _members(db, conversation_id) == {alice.id: True, bob.id: True}

    assert await friends_svc.list_requests(db, bob.id, "incoming") == []


async def test_pending_requests_hide_presence_until_accepted(pg_db, monkeypatch):
    from app.api.v1.endpoints import friends as friends_ep

    db, ids = pg_db
    alice = await _create_user(db, ids)
    bob = await _create_user(db, ids)
    await db.commit()
    online = {str(alice.id): True, str(bob.id): True}
    monkeypatch.setattr(friends_ep.ws_manager, "get_presence_map", lambda: online)

    friendship, _ = await friends_svc.send_request(db, alice.id, user_id=bob.id)
    await db.commit()
    sent = await friends_svc.build_request_dto(db, friendship, alice.id, online)
    assert sent.user.id == bob.id and sent.user.is_online is False

    outgoing = await friends_ep.list_requests(current_user=alice, db=db, direction="outgoing")
    assert [(r.user.id, r.user.is_online) for r in outgoing.data] == [(bob.id, False)]
    incoming = await friends_ep.list_requests(current_user=bob, db=db, direction="incoming")
    assert [(r.user.id, r.user.is_online) for r in incoming.data] == [(alice.id, False)]

    accepted, _ = await friends_svc.accept_request(db, friendship.id, bob.id)
    await db.commit()
    now_friends = await friends_svc.build_request_dto(db, accepted, alice.id, online)
    assert now_friends.user.is_online is True


async def test_only_addressee_can_accept_or_decline(pg_db):
    db, ids = pg_db
    alice = await _create_user(db, ids)
    bob = await _create_user(db, ids)
    await db.commit()
    friendship, _ = await friends_svc.send_request(db, alice.id, user_id=bob.id)
    await db.commit()

    for action in (friends_svc.accept_request, friends_svc.decline_request):
        with pytest.raises(ApiException) as exc:
            await action(db, friendship.id, alice.id)
        assert exc.value.status_code == 404
        assert exc.value.detail["code"] == "FRIEND_REQUEST_NOT_FOUND"


async def test_reverse_pending_request_auto_accepts(pg_db):
    db, ids = pg_db
    alice = await _create_user(db, ids)
    bob = await _create_user(db, ids)
    await db.commit()

    first, _ = await friends_svc.send_request(db, alice.id, user_id=bob.id)
    await db.commit()
    second, auto = await friends_svc.send_request(db, bob.id, user_id=alice.id)
    await db.commit()

    assert auto is True
    assert second.id == first.id
    assert second.status == "accepted"
    rows = (
        await db.execute(select(Friendship).where(Friendship.id == first.id))
    ).scalars().all()
    assert len(rows) == 1
    assert await friends_svc.are_friends(db, alice.id, bob.id)


async def test_duplicate_self_and_already_friends_errors(pg_db):
    db, ids = pg_db
    alice = await _create_user(db, ids)
    bob = await _create_user(db, ids)
    await db.commit()

    await friends_svc.send_request(db, alice.id, user_id=bob.id)
    await db.commit()
    with pytest.raises(ApiException) as dup:
        await friends_svc.send_request(db, alice.id, username=bob.username)
    assert dup.value.status_code == 409
    assert dup.value.detail["code"] == "FRIEND_REQUEST_EXISTS"

    with pytest.raises(ApiException) as self_err:
        await friends_svc.send_request(db, alice.id, username=alice.username)
    assert self_err.value.status_code == 400
    assert self_err.value.detail["code"] == "FRIEND_SELF"

    await friends_svc.send_request(db, bob.id, user_id=alice.id)  # auto-accept
    await db.commit()
    with pytest.raises(ApiException) as already:
        await friends_svc.send_request(db, alice.id, user_id=bob.id)
    assert already.value.status_code == 409
    assert already.value.detail["code"] == "FRIEND_ALREADY"

    with pytest.raises(ApiException) as missing:
        await friends_svc.send_request(db, alice.id, username=f"missing{_token()}")
    assert missing.value.status_code == 404
    assert missing.value.detail["code"] == "USER_NOT_FOUND"


async def test_unique_pair_index_blocks_reverse_duplicate(pg_db):
    db, ids = pg_db
    alice_id = (await _create_user(db, ids)).id
    bob_id = (await _create_user(db, ids)).id
    await db.commit()

    db.add(Friendship(requester_id=alice_id, addressee_id=bob_id))
    await db.commit()
    db.add(Friendship(requester_id=bob_id, addressee_id=alice_id))
    with pytest.raises(IntegrityError):
        await db.flush()
    await db.rollback()

    db.add(Friendship(requester_id=alice_id, addressee_id=alice_id))
    with pytest.raises(IntegrityError):  # ck_friendships_not_self
        await db.flush()
    await db.rollback()


async def test_declined_request_can_be_resent(pg_db):
    db, ids = pg_db
    alice = await _create_user(db, ids)
    bob = await _create_user(db, ids)
    await db.commit()

    first, _ = await friends_svc.send_request(db, alice.id, user_id=bob.id)
    await db.commit()
    await friends_svc.decline_request(db, first.id, bob.id)
    await db.commit()
    assert await friends_svc.list_requests(db, bob.id, "incoming") == []

    resent, auto = await friends_svc.send_request(db, alice.id, user_id=bob.id)
    await db.commit()
    assert auto is False
    assert resent.id == first.id
    assert resent.status == "pending"
    assert resent.responded_at is None

    await friends_svc.decline_request(db, first.id, bob.id)
    await db.commit()
    # The decliner may also reach out later; the row flips direction.
    reversed_req, _ = await friends_svc.send_request(db, bob.id, user_id=alice.id)
    await db.commit()
    assert reversed_req.id == first.id
    assert (reversed_req.requester_id, reversed_req.addressee_id) == (bob.id, alice.id)


async def test_cancel_and_unfriend(pg_db):
    db, ids = pg_db
    alice = await _create_user(db, ids)
    bob = await _create_user(db, ids)
    await db.commit()

    request, _ = await friends_svc.send_request(db, alice.id, user_id=bob.id)
    await db.commit()
    with pytest.raises(ApiException):
        await friends_svc.cancel_request(db, request.id, bob.id)  # only the sender cancels
    await friends_svc.cancel_request(db, request.id, alice.id)
    await db.commit()
    with pytest.raises(ApiException) as gone:
        await friends_svc.cancel_request(db, request.id, alice.id)
    assert gone.value.status_code == 404

    request, _ = await friends_svc.send_request(db, alice.id, user_id=bob.id)
    await friends_svc.accept_request(db, request.id, bob.id)
    await db.commit()
    await friends_svc.unfriend(db, bob.id, alice.id)
    await db.commit()
    assert not await friends_svc.are_friends(db, alice.id, bob.id)
    with pytest.raises(ApiException) as not_friend:
        await friends_svc.unfriend(db, alice.id, bob.id)
    assert not_friend.value.detail["code"] == "FRIEND_NOT_FOUND"


async def test_direct_conversation_between_friends_is_accepted(pg_db):
    db, ids = pg_db
    alice = await _create_user(db, ids)
    bob = await _create_user(db, ids)
    carol = await _create_user(db, ids)
    db.add(Friendship(requester_id=alice.id, addressee_id=bob.id, status="accepted"))
    await db.commit()

    with_friend = await chat_svc.create_direct_conversation(db, alice.id, bob.id)
    await db.commit()
    assert await _members(db, with_friend.id) == {alice.id: True, bob.id: True}

    # Strangers keep the web stranger-request flow.
    with_stranger = await chat_svc.create_direct_conversation(db, alice.id, carol.id)
    await db.commit()
    assert await _members(db, with_stranger.id) == {alice.id: True, carol.id: False}

    # Becoming friends later: reusing the old conversation accepts it.
    db.add(Friendship(requester_id=carol.id, addressee_id=alice.id, status="accepted"))
    await db.commit()
    reused = await chat_svc.create_direct_conversation(db, carol.id, alice.id)
    await db.commit()
    assert reused.id == with_stranger.id
    assert await _members(db, with_stranger.id) == {alice.id: True, carol.id: True}


async def test_search_excludes_hidden_accounts_and_escapes_wildcards(pg_db):
    db, ids = pg_db
    prefix = f"s{_token()}"
    now = datetime.datetime.now(datetime.timezone.utc)
    me = await _create_user(db, ids, username=f"{prefix}_me")
    normal = await _create_user(db, ids, username=f"{prefix}_normal")
    friend = await _create_user(db, ids, username=f"{prefix}_friend")
    expired_ban = await _create_user(
        db, ids, username=f"{prefix}_oldban",
        banned_at=now - datetime.timedelta(days=10),
        banned_until=now - datetime.timedelta(days=1),
    )
    await _create_user(db, ids, username=f"{prefix}_bot", is_system=True)
    await _create_user(db, ids, username=f"{prefix}_deleted", deleted_at=now)
    await _create_user(db, ids, username=f"{prefix}_banned", banned_at=now)
    wildcard_twin = await _create_user(db, ids, username=f"{prefix}x")
    db.add(Friendship(requester_id=me.id, addressee_id=friend.id, status="accepted"))
    pending = Friendship(requester_id=normal.id, addressee_id=me.id)
    db.add(pending)
    await db.commit()

    results = await friends_svc.search_users(db, me.id, f"{prefix}_", limit=20)
    by_name = {r.username: r for r in results}
    assert set(by_name) == {normal.username, friend.username, expired_ban.username}
    assert wildcard_twin.username not in by_name  # "_" matched literally
    assert by_name[friend.username].friendship_status == "friends"
    assert by_name[normal.username].friendship_status == "incoming"
    assert by_name[normal.username].request_id == pending.id
    assert by_name[expired_ban.username].friendship_status is None
    assert all("email" not in r.model_dump() for r in results)

    twin_only = await friends_svc.search_users(db, me.id, f"{prefix}x", limit=5)
    assert [r.username for r in twin_only] == [wildcard_twin.username]

    # "%" is literal too: no username contains it.
    assert await friends_svc.search_users(db, me.id, f"{prefix}%", limit=5) == []


async def test_lookup_users_is_hardened_but_keeps_shape(pg_db):
    db, ids = pg_db
    prefix = f"l{_token()}"
    now = datetime.datetime.now(datetime.timezone.utc)
    me = await _create_user(db, ids, username=f"{prefix}_me")
    visible = await _create_user(db, ids, username=f"{prefix}_vis")
    visible.display_name = f"{prefix}_Visible"
    bot = await _create_user(db, ids, username=f"{prefix}_bot", is_system=True)
    bot.display_name = f"{prefix}_Bot"
    deleted = await _create_user(db, ids, username=f"{prefix}_del", deleted_at=now)
    deleted.display_name = f"{prefix}_Deleted"
    twin = await _create_user(db, ids, username=f"{prefix}_twin")
    twin.display_name = f"{prefix}XVisible"
    await db.commit()

    results = await chat_svc.lookup_users(db, f"{prefix}_", me.id, limit=20)
    assert [r.id for r in results] == [visible.id]
    # The email field stays in the contract but is null for a name match.
    assert results[0].email is None
