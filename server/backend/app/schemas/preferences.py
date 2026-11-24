"""Pydantic schemas for user preferences."""
from __future__ import annotations

import re
from typing import Literal, Optional

from pydantic import BaseModel, Field, field_validator

from app.schemas.common import DataResponse
from app.schemas.leaderboard import LeaderboardVisibility

# App background style; mirrors the Android BackgroundStyle enum (lowercased).
BackgroundStyle = Literal["aurora", "soft", "solid"]


class AppearanceData(BaseModel):
    background: BackgroundStyle = "aurora"


class AppearanceUpdate(BaseModel):
    """Partial appearance update; omitted keys keep their stored value."""

    background: Optional[BackgroundStyle] = None


class UserPreferenceData(BaseModel):
    theme_mode: str = "system"
    accent_color: Optional[str] = None
    locale: str = "en"
    appearance: AppearanceData = Field(default_factory=AppearanceData)
    leaderboard_visibility: LeaderboardVisibility = "friends"


class UserPreferenceResponse(DataResponse[UserPreferenceData]):
    ...


class UserPreferenceUpdate(BaseModel):
    theme_mode: Optional[Literal["system", "light", "dark"]] = None
    accent_color: Optional[str] = Field(None, max_length=7)
    locale: Optional[Literal["en", "vi"]] = None
    appearance: Optional[AppearanceUpdate] = None
    leaderboard_visibility: Optional[LeaderboardVisibility] = None

    @field_validator("accent_color", mode="before")
    @classmethod
    def validate_accent_color(cls, v: object) -> object:
        if v is None:
            return v
        if not isinstance(v, str) or not re.match(r"^#[0-9A-Fa-f]{6}$", v):
            raise ValueError("accent_color must be a valid hex color (#RRGGBB)")
        return v.upper()
