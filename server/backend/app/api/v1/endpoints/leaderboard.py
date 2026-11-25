"""Leaderboard endpoints — GET /v1/leaderboard/steps."""
from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.adapters.database import get_db
from app.core.security import get_current_user
from app.models.core import User
from app.schemas.common import ErrorResponse
from app.schemas.leaderboard import LeaderboardPeriod, LeaderboardResponse, LeaderboardScope
from app.services import leaderboard as leaderboard_svc

router = APIRouter(prefix="/leaderboard", tags=["Leaderboard"])


@router.get(
    "/steps",
    response_model=LeaderboardResponse,
    responses={401: {"model": ErrorResponse}},
    summary="Steps leaderboard for today or this week, among friends or globally",
)
async def steps_leaderboard(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
    period: Annotated[LeaderboardPeriod, Query()] = "day",
    scope: Annotated[LeaderboardScope, Query()] = "friends",
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
) -> LeaderboardResponse:
    """Days are local days in Asia/Ho_Chi_Minh; a week starts on Monday.

    Users who set `leaderboard_visibility` to `off` are hidden (the caller still
    sees the board, with `me` null). The global board only lists users who opted
    in with `global`.
    """
    board = await leaderboard_svc.steps_leaderboard(db, current_user.id, period, scope, limit)
    return LeaderboardResponse(data=board)
