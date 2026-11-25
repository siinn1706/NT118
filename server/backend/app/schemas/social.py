"""Pydantic schemas for friends (social graph) endpoints."""
from __future__ import annotations

import datetime
import uuid
from typing import Literal, Optional

from pydantic import BaseModel, Field, model_validator

from app.schemas.common import DataResponse

FriendshipStatus = Literal["pending", "accepted", "declined", "blocked"]
FriendRequestDirection = Literal["incoming", "outgoing"]
# Relationship of a search result to the viewer; null means "no relationship,
# a request can be sent" (also used after a declined request).
SearchFriendshipStatus = Literal["friends", "outgoing", "incoming"]


class FriendUserDTO(BaseModel):
    id: uuid.UUID
    username: str
    display_name: str
    avatar_url: Optional[str] = None
    is_online: bool = False


class FriendRequestDTO(BaseModel):
    id: uuid.UUID
    status: FriendshipStatus
    direction: FriendRequestDirection
    user: FriendUserDTO
    created_at: datetime.datetime


class FriendDTO(BaseModel):
    user: FriendUserDTO
    since: datetime.datetime
    direct_conversation_id: Optional[uuid.UUID] = None


class SendFriendRequestBody(BaseModel):
    """Exactly one of `username` or `user_id` identifies the target."""

    username: Optional[str] = Field(None, min_length=3, max_length=50)
    user_id: Optional[uuid.UUID] = None

    @model_validator(mode="after")
    def _exactly_one_target(self) -> "SendFriendRequestBody":
        if (self.username is None) == (self.user_id is None):
            raise ValueError("Provide exactly one of username or user_id.")
        return self


class FriendSearchResult(BaseModel):
    id: uuid.UUID
    username: str
    display_name: str
    avatar_url: Optional[str] = None
    friendship_status: Optional[SearchFriendshipStatus] = None
    # Pending request id when friendship_status is "incoming" or "outgoing".
    request_id: Optional[uuid.UUID] = None


class FriendListResponse(DataResponse[list[FriendDTO]]):
    ...


class FriendResponse(DataResponse[FriendDTO]):
    ...


class FriendRequestListResponse(DataResponse[list[FriendRequestDTO]]):
    ...


class FriendRequestResponse(DataResponse[FriendRequestDTO]):
    ...


class FriendSearchResponse(DataResponse[list[FriendSearchResult]]):
    ...
