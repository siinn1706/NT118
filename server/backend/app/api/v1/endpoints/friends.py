"""Friends endpoints — requests, friend list and people search under /v1/friends.

Realtime events (sent over the main `/ws` socket after commit):
  * `friend.request.received` to the addressee, payload = FriendRequestDTO.
  * `friend.request.accepted` to the original requester,
    payload = {"request_id": str, "friend": FriendDTO}.
Both also create an in-app notification (kind "social").
"""
from __future__ import annotations

import uuid
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Query, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.adapters.database import get_db
from app.core.rate_limit import rate_limit_friend_request
from app.core.security import get_current_user
from app.models.core import User
from app.schemas.common import ErrorResponse
from app.schemas.social import (
    FriendListResponse,
    FriendRequestListResponse,
    FriendRequestResponse,
    FriendResponse,
    FriendSearchResponse,
    SendFriendRequestBody,
)
from app.services import friends as friends_svc
from app.ws.handlers import manager as ws_manager

router = APIRouter(prefix="/friends", tags=["Friends"])

_ERRORS = {
    400: {"model": ErrorResponse},
    401: {"model": ErrorResponse},
    404: {"model": ErrorResponse},
}


@router.get(
    "",
    response_model=FriendListResponse,
    responses={401: {"model": ErrorResponse}},
    summary="List accepted friends with presence and direct conversation id",
)
async def list_friends(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> FriendListResponse:
    friends = await friends_svc.list_friends(db, current_user.id, ws_manager.get_presence_map())
    return FriendListResponse(data=friends)


@router.get(
    "/requests",
    response_model=FriendRequestListResponse,
    responses={401: {"model": ErrorResponse}},
    summary="List pending friend requests (incoming or outgoing)",
)
async def list_requests(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
    direction: Annotated[Literal["incoming", "outgoing"], Query()] = "incoming",
) -> FriendRequestListResponse:
    requests = await friends_svc.list_requests(db, current_user.id, direction)
    return FriendRequestListResponse(data=requests)


@router.post(
    "/requests",
    response_model=FriendRequestResponse,
    status_code=status.HTTP_201_CREATED,
    responses={
        200: {
            "model": FriendRequestResponse,
            "description": "The target already had a pending request to you; it was accepted.",
        },
        403: {"model": ErrorResponse},
        409: {"model": ErrorResponse},
        **_ERRORS,
    },
    summary="Send a friend request by username or user_id",
    dependencies=[Depends(rate_limit_friend_request)],
)
async def send_request(
    body: SendFriendRequestBody,
    response: Response,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> FriendRequestResponse:
    friendship, auto_accepted = await friends_svc.send_request(
        db, current_user.id, username=body.username, user_id=body.user_id
    )
    dto = await friends_svc.build_request_dto(
        db, friendship, current_user.id, ws_manager.get_presence_map()
    )
    await db.commit()
    if auto_accepted:
        response.status_code = status.HTTP_200_OK
        await friends_svc.notify_request_accepted(db, friendship)
    else:
        await friends_svc.notify_request_received(db, friendship)
    return FriendRequestResponse(data=dto)


@router.post(
    "/requests/{request_id}/accept",
    response_model=FriendResponse,
    responses=_ERRORS,
    summary="Accept an incoming friend request",
)
async def accept_request(
    request_id: uuid.UUID,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> FriendResponse:
    friendship, conversation_id = await friends_svc.accept_request(db, request_id, current_user.id)
    dto = await friends_svc.build_friend_dto(
        db, friendship, current_user.id, ws_manager.get_presence_map()
    )
    await db.commit()
    await friends_svc.notify_request_accepted(db, friendship, conversation_id)
    return FriendResponse(data=dto)


@router.post(
    "/requests/{request_id}/decline",
    status_code=status.HTTP_204_NO_CONTENT,
    responses=_ERRORS,
    summary="Decline an incoming friend request",
)
async def decline_request(
    request_id: uuid.UUID,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> None:
    await friends_svc.decline_request(db, request_id, current_user.id)
    await db.commit()


@router.delete(
    "/requests/{request_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    responses=_ERRORS,
    summary="Cancel a friend request you sent",
)
async def cancel_request(
    request_id: uuid.UUID,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> None:
    await friends_svc.cancel_request(db, request_id, current_user.id)
    await db.commit()


@router.get(
    "/search",
    response_model=FriendSearchResponse,
    responses={401: {"model": ErrorResponse}},
    summary="Find people by username (exact or prefix match)",
)
async def search_users(
    q: Annotated[str, Query(min_length=1, max_length=50, description="Username or prefix")],
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
    limit: Annotated[int, Query(ge=1, le=friends_svc.SEARCH_LIMIT_MAX)] = 10,
) -> FriendSearchResponse:
    results = await friends_svc.search_users(db, current_user.id, q, limit)
    return FriendSearchResponse(data=results)


@router.delete(
    "/{user_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    responses=_ERRORS,
    summary="Remove a friend",
)
async def unfriend(
    user_id: uuid.UUID,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> None:
    await friends_svc.unfriend(db, current_user.id, user_id)
    await db.commit()
