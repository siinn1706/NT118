"""Steps leaderboard.

How a score is computed (PostgreSQL):
  1. Step rows (`health_metrics`, metric_type=steps, not tombstoned) are bucketed
     by the *local* day of `recorded_at` in LEADERBOARD_TZ.
  2. Within a day, rows are summed per producer = (source, device, source_app),
     giving that producer's daily total.
  3. The day's value is the MAX across producers (the phone counter, other apps
     writing to Health Connect, Google Health... report overlapping walks, so
     summing them would double count), capped at DAILY_STEP_CAP.
  4. The period score is the sum of daily values (day = today, week = ISO week
     from Monday through today).

Privacy (`user_preferences.leaderboard_visibility`, missing row = 'friends'):
  * 'off'     — never shown to anyone (the viewer still sees the board, `me` is null).
  * 'friends' — shown on friends boards only.
  * 'global'  — shown on friends boards and the global board.
"""
from __future__ import annotations

import datetime
import uuid
from dataclasses import dataclass
from typing import Callable, Sequence, TypeVar
from zoneinfo import ZoneInfo

from sqlalchemy import func, literal, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.core import HealthMetric, MetricTypeEnum, User, UserPreference
from app.schemas.leaderboard import (
    LeaderboardDTO,
    LeaderboardEntryDTO,
    LeaderboardPeriod,
    LeaderboardScope,
)
from app.services import friends as friends_svc
from app.services.user_visibility import discoverable_user_clause

LEADERBOARD_TZ = "Asia/Ho_Chi_Minh"
# Mirrors the Android StepDeltaCalculator daily cap; protects the board from
# corrupted or hostile uploads.
DAILY_STEP_CAP = 200_000
DEFAULT_VISIBILITY = "friends"
_NIL_UUID = uuid.UUID(int=0)

T = TypeVar("T")


# ──────────────────────────────────────────────────────────────────────────────
# Pure helpers (no DB)
# ──────────────────────────────────────────────────────────────────────────────

def period_bounds(period: LeaderboardPeriod, today_local: datetime.date) -> tuple[datetime.date, datetime.date]:
    """Inclusive local date range: day = today; week = ISO Monday .. today."""
    if period == "day":
        return today_local, today_local
    if period == "week":
        return today_local - datetime.timedelta(days=today_local.weekday()), today_local
    raise ValueError(f"Unsupported period: {period!r}")


def utc_window(
    date_from: datetime.date,
    date_to: datetime.date,
    tz: datetime.tzinfo,
) -> tuple[datetime.datetime, datetime.datetime]:
    """[start, end) in UTC covering local days date_from..date_to inclusive."""
    start_local = datetime.datetime.combine(date_from, datetime.time.min, tzinfo=tz)
    end_local = datetime.datetime.combine(
        date_to + datetime.timedelta(days=1), datetime.time.min, tzinfo=tz
    )
    return start_local.astimezone(datetime.timezone.utc), end_local.astimezone(datetime.timezone.utc)


def competition_ranks(items: Sequence[T], score: Callable[[T], int]) -> list[tuple[int, T]]:
    """Standard competition ranking ("1224") over items already sorted by score desc."""
    ranked: list[tuple[int, T]] = []
    previous: int | None = None
    current_rank = 0
    for index, item in enumerate(items, start=1):
        value = score(item)
        if value != previous:
            current_rank = index
            previous = value
        ranked.append((current_rank, item))
    return ranked


@dataclass(frozen=True)
class _Row:
    user: User
    steps: int


def _sort_rows(rows: list[_Row]) -> list[_Row]:
    return sorted(rows, key=lambda r: (-r.steps, (r.user.display_name or "").lower(), str(r.user.id)))


def _entry(rank: int, row: _Row, viewer_id: uuid.UUID) -> LeaderboardEntryDTO:
    profile = getattr(row.user, "profile", None)
    return LeaderboardEntryDTO(
        rank=rank,
        user_id=row.user.id,
        username=row.user.username,
        display_name=row.user.display_name,
        avatar_url=profile.avatar_url if profile else None,
        steps=row.steps,
        is_me=row.user.id == viewer_id,
    )


# ──────────────────────────────────────────────────────────────────────────────
# SQL
# ──────────────────────────────────────────────────────────────────────────────

def _visibility_expr():
    return func.coalesce(UserPreference.leaderboard_visibility, DEFAULT_VISIBILITY)


def _totals_subquery(start_utc: datetime.datetime, end_utc: datetime.datetime, user_filter):
    """(user_id, steps) for the window. Each stage groups on plain columns of the
    previous subquery so bound parameters never appear inside GROUP BY."""
    base = (
        select(
            HealthMetric.user_id.label("user_id"),
            func.date(func.timezone(LEADERBOARD_TZ, HealthMetric.recorded_at)).label("day"),
            HealthMetric.source.label("source"),
            func.coalesce(HealthMetric.device_id, literal(_NIL_UUID)).label("device_key"),
            func.coalesce(HealthMetric.source_app, "").label("app_key"),
            HealthMetric.value.label("value"),
        )
        .where(
            HealthMetric.metric_type == MetricTypeEnum.STEPS,
            HealthMetric.is_deleted.is_(False),
            HealthMetric.recorded_at >= start_utc,
            HealthMetric.recorded_at < end_utc,
            user_filter,
        )
        .subquery("lb_base")
    )
    per_producer = (
        select(base.c.user_id, base.c.day, func.sum(base.c.value).label("steps"))
        .group_by(base.c.user_id, base.c.day, base.c.source, base.c.device_key, base.c.app_key)
        .subquery("lb_producer")
    )
    per_day = (
        select(
            per_producer.c.user_id,
            per_producer.c.day,
            func.least(func.max(per_producer.c.steps), DAILY_STEP_CAP).label("steps"),
        )
        .group_by(per_producer.c.user_id, per_producer.c.day)
        .subquery("lb_day")
    )
    return (
        select(per_day.c.user_id, func.sum(per_day.c.steps).label("steps"))
        .group_by(per_day.c.user_id)
        .subquery("lb_totals")
    )


def _as_int(value: object) -> int:
    return int(round(float(value or 0)))


async def _viewer_visibility(db: AsyncSession, viewer_id: uuid.UUID) -> str:
    result = await db.execute(
        select(UserPreference.leaderboard_visibility).where(UserPreference.user_id == viewer_id)
    )
    return result.scalar_one_or_none() or DEFAULT_VISIBILITY


async def _friends_board(
    db: AsyncSession,
    viewer_id: uuid.UUID,
    start_utc: datetime.datetime,
    end_utc: datetime.datetime,
) -> list[_Row]:
    candidates = [*await friends_svc.friend_ids(db, viewer_id), viewer_id]
    result = await db.execute(
        select(User)
        .outerjoin(UserPreference, UserPreference.user_id == User.id)
        .options(selectinload(User.profile))
        .where(
            User.id.in_(candidates),
            discoverable_user_clause(),
            _visibility_expr() != "off",
        )
    )
    users = list(result.scalars().unique().all())
    if not users:
        return []
    totals = _totals_subquery(start_utc, end_utc, HealthMetric.user_id.in_([u.id for u in users]))
    steps_by_user = {
        user_id: _as_int(steps)
        for user_id, steps in (await db.execute(select(totals.c.user_id, totals.c.steps))).all()
    }
    return _sort_rows([_Row(user=u, steps=steps_by_user.get(u.id, 0)) for u in users])


def _global_members():
    return (
        select(User.id)
        .join(UserPreference, UserPreference.user_id == User.id)
        .where(UserPreference.leaderboard_visibility == "global", discoverable_user_clause())
    )


async def _global_board(
    db: AsyncSession,
    viewer_id: uuid.UUID,
    viewer_visibility: str,
    start_utc: datetime.datetime,
    end_utc: datetime.datetime,
    limit: int,
) -> tuple[list[tuple[int, _Row]], tuple[int, _Row] | None]:
    totals = _totals_subquery(start_utc, end_utc, HealthMetric.user_id.in_(_global_members()))
    result = await db.execute(
        select(User, totals.c.steps)
        .join(totals, totals.c.user_id == User.id)
        .options(selectinload(User.profile))
        .where(totals.c.steps > 0)
        .order_by(totals.c.steps.desc(), func.lower(User.display_name), User.id)
        .limit(limit)
    )
    top = _sort_rows([_Row(user=u, steps=_as_int(s)) for u, s in result.all()])
    # Ranks inside the top-N are exact: everyone scoring higher is also in it.
    ranked = competition_ranks(top, lambda r: r.steps)

    me: tuple[int, _Row] | None = None
    if viewer_visibility == "global":
        me = next(((rank, row) for rank, row in ranked if row.user.id == viewer_id), None)
        if me is None:
            viewer = (
                await db.execute(
                    select(User)
                    .options(selectinload(User.profile))
                    .where(User.id == viewer_id, discoverable_user_clause())
                )
            ).scalar_one_or_none()
            if viewer is not None:
                my_steps = _as_int(
                    (
                        await db.execute(
                            select(totals.c.steps).where(totals.c.user_id == viewer_id)
                        )
                    ).scalar_one_or_none()
                )
                ahead = (
                    await db.execute(
                        select(func.count()).select_from(totals).where(totals.c.steps > my_steps)
                    )
                ).scalar_one()
                me = (int(ahead) + 1, _Row(user=viewer, steps=my_steps))
    return ranked, me


async def steps_leaderboard(
    db: AsyncSession,
    viewer_id: uuid.UUID,
    period: LeaderboardPeriod,
    scope: LeaderboardScope,
    limit: int = 50,
    *,
    now: datetime.datetime | None = None,
) -> LeaderboardDTO:
    tz = ZoneInfo(LEADERBOARD_TZ)
    generated_at = now or datetime.datetime.now(datetime.timezone.utc)
    today_local = generated_at.astimezone(tz).date()
    date_from, date_to = period_bounds(period, today_local)
    start_utc, end_utc = utc_window(date_from, date_to, tz)
    viewer_visibility = await _viewer_visibility(db, viewer_id)

    if scope == "friends":
        ranked = competition_ranks(await _friends_board(db, viewer_id, start_utc, end_utc), lambda r: r.steps)
        me = next(((rank, row) for rank, row in ranked if row.user.id == viewer_id), None)
        ranked = ranked[:limit]
    else:
        ranked, me = await _global_board(db, viewer_id, viewer_visibility, start_utc, end_utc, limit)

    return LeaderboardDTO(
        period=period,
        scope=scope,
        timezone=LEADERBOARD_TZ,
        range_start=date_from,
        range_end=date_to,
        entries=[_entry(rank, row, viewer_id) for rank, row in ranked],
        me=_entry(me[0], me[1], viewer_id) if me else None,
        generated_at=generated_at,
    )
