"""Pydantic schemas for the activity leaderboard."""
from __future__ import annotations

import datetime
import uuid
from typing import Literal, Optional

from pydantic import BaseModel

from app.schemas.common import DataResponse

LeaderboardPeriod = Literal["day", "week"]
LeaderboardScope = Literal["friends", "global"]
LeaderboardVisibility = Literal["off", "friends", "global"]


class LeaderboardEntryDTO(BaseModel):
    rank: int
    user_id: uuid.UUID
    username: str
    display_name: str
    avatar_url: Optional[str] = None
    steps: int
    is_me: bool = False


class LeaderboardDTO(BaseModel):
    metric: Literal["steps"] = "steps"
    period: LeaderboardPeriod
    scope: LeaderboardScope
    timezone: str
    range_start: datetime.date
    range_end: datetime.date
    entries: list[LeaderboardEntryDTO]
    me: Optional[LeaderboardEntryDTO] = None
    generated_at: datetime.datetime


class LeaderboardResponse(DataResponse[LeaderboardDTO]):
    ...
