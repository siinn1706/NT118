"""Integration tests for the steps leaderboard against a real Postgres.

All rows live in March 2001 (and `now` is pinned there) so data from other
users of a shared dev database cannot leak into the boards under test.
Thursday 2001-03-15 is "today"; Monday 2001-03-12 starts the ISO week.
"""
from __future__ import annotations

import datetime
import uuid

import pytest
import pytest_asyncio
from sqlalchemy import delete
from sqlalchemy.ext.asyncio import AsyncSession

from app.adapters.database import AsyncSessionLocal, engine
from app.models.core import HealthMetric, MetricTypeEnum, User, UserPreference, WearableSourceEnum
from app.models.social import Friendship
from app.services import leaderboard as lb

pytestmark = pytest.mark.skipif(
    not __import__("app.core.config", fromlist=["settings"]).settings.database_url,
    reason="needs real Postgres",
)

UTC = datetime.timezone.utc
NOW = datetime.datetime(2001, 3, 15, 10, 0, tzinfo=UTC)  # 17:00 local, Thursday


def _local(day: int, hour: int) -> datetime.datetime:
    """A local (UTC+7) wall-clock time on 2001-03-<day> as an aware UTC datetime."""
    return datetime.datetime(2001, 3, day, hour, 0, tzinfo=datetime.timezone(datetime.timedelta(hours=7))).astimezone(UTC)


@pytest_asyncio.fixture
async def pg_db():
    ids: list[uuid.UUID] = []
    async with AsyncSessionLocal() as db:
        yield db, ids
    if ids:
        async with AsyncSessionLocal() as cleanup:
            await cleanup.execute(delete(User).where(User.id.in_(ids)))
            await cleanup.commit()
    await engine.dispose()


async def _user(db: AsyncSession, ids: list[uuid.UUID], name: str, visibility: str | None = None) -> User:
    tag = uuid.uuid4().hex[:8]
    user = User(
        email=f"lb-{tag}@test.local",
        username=f"lb{name.lower()}{tag}",
        display_name=f"{name} {tag}",
        hashed_password="x",
        has_password=True,
    )
    db.add(user)
    await db.flush()
    ids.append(user.id)
    if visibility is not None:
        db.add(UserPreference(user_id=user.id, leaderboard_visibility=visibility))
    return user


def _steps(
    user: User,
    value: float,
    at: datetime.datetime,
    source: WearableSourceEnum = WearableSourceEnum.HEALTH_CONNECT,
    *,
    is_deleted: bool = False,
    source_app: str | None = None,
) -> HealthMetric:
    return HealthMetric(
        user_id=user.id,
        metric_type=MetricTypeEnum.STEPS,
        value=value,
        unit="count",
        recorded_at=at,
        source=source,
        source_app=source_app,
        is_deleted=is_deleted,
    )


def _befriend(a: User, b: User) -> Friendship:
    return Friendship(requester_id=a.id, addressee_id=b.id, status="accepted")


def _steps_by_user(board) -> dict[uuid.UUID, int]:
    return {e.user_id: e.steps for e in board.entries}


async def test_max_across_sources_cap_and_tombstones(pg_db):
    db, ids = pg_db
    me = await _user(db, ids, "Me")
    capped = await _user(db, ids, "Capped")
    db.add(_befriend(me, capped))
    db.add_all([
        # One producer reporting in chunks: 3000 + 4000 = 7000.
        _steps(me, 3000, _local(15, 8)),
        _steps(me, 4000, _local(15, 12)),
        # Another producer saw the same walk as 8000: MAX wins, no double count.
        _steps(me, 8000, _local(15, 13), WearableSourceEnum.GOOGLE_HEALTH),
        # Tombstoned rows never count.
        _steps(me, 50000, _local(15, 14), WearableSourceEnum.MANUAL, is_deleted=True),
        _steps(capped, 250000, _local(15, 9)),
    ])
    await db.commit()

    board = await lb.steps_leaderboard(db, me.id, "day", "friends", now=NOW)
    assert board.range_start == board.range_end == datetime.date(2001, 3, 15)
    steps = _steps_by_user(board)
    assert steps[me.id] == 8000
    assert steps[capped.id] == lb.DAILY_STEP_CAP
    assert board.me is not None and board.me.is_me and board.me.rank == 2


async def test_health_connect_apps_are_separate_producers(pg_db):
    db, ids = pg_db
    me = await _user(db, ids, "Me")
    db.add_all([
        # Two apps writing into Health Connect saw the same walk: MAX, not 11000.
        _steps(me, 5000, _local(15, 8), source_app="com.sec.android.app.shealth"),
        _steps(me, 6000, _local(15, 9), source_app="vn.uit.healthos"),
        # The same app reporting in chunks still sums: 6000 + 500.
        _steps(me, 500, _local(15, 16), source_app="vn.uit.healthos"),
    ])
    await db.commit()

    board = await lb.steps_leaderboard(db, me.id, "day", "friends", now=NOW)
    assert board.me is not None and board.me.steps == 6500


async def test_local_day_boundaries(pg_db):
    db, ids = pg_db
    me = await _user(db, ids, "Me")
    db.add_all([
        # 05:00 local on the 15th = 22:00 UTC on the 14th: belongs to the 15th.
        _steps(me, 1200, _local(15, 5)),
        # 23:00 local on the 14th: yesterday.
        _steps(me, 9999, _local(14, 23)),
        # 00:30 local on the 16th: tomorrow (still in the future at NOW).
        _steps(me, 7777, _local(16, 0) + datetime.timedelta(minutes=30)),
    ])
    await db.commit()

    board = await lb.steps_leaderboard(db, me.id, "day", "friends", now=NOW)
    assert board.me is not None
    assert board.me.steps == 1200


async def test_week_starts_on_monday(pg_db):
    db, ids = pg_db
    me = await _user(db, ids, "Me")
    db.add_all([
        _steps(me, 1000, _local(11, 20)),  # Sunday of the previous week
        _steps(me, 2000, _local(12, 7)),   # Monday
        _steps(me, 3000, _local(14, 18)),  # Wednesday
        _steps(me, 4000, _local(15, 9)),   # Thursday (today)
    ])
    await db.commit()

    week = await lb.steps_leaderboard(db, me.id, "week", "friends", now=NOW)
    assert (week.range_start, week.range_end) == (datetime.date(2001, 3, 12), datetime.date(2001, 3, 15))
    assert week.me is not None and week.me.steps == 9000

    day = await lb.steps_leaderboard(db, me.id, "day", "friends", now=NOW)
    assert day.me is not None and day.me.steps == 4000


async def test_friends_board_visibility_ties_and_zero_steps(pg_db):
    db, ids = pg_db
    me = await _user(db, ids, "Me", "friends")
    tie_a = await _user(db, ids, "Anh", "friends")
    tie_b = await _user(db, ids, "Binh")  # no preferences row = friends
    hidden = await _user(db, ids, "Hidden", "off")
    idle = await _user(db, ids, "Idle", "global")
    stranger = await _user(db, ids, "Stranger", "global")
    db.add_all([_befriend(me, tie_a), _befriend(tie_b, me), _befriend(me, hidden), _befriend(idle, me)])
    db.add_all([
        _steps(me, 7000, _local(15, 9)),
        _steps(tie_a, 5000, _local(15, 9)),
        _steps(tie_b, 5000, _local(15, 10)),
        _steps(hidden, 9000, _local(15, 9)),
        _steps(stranger, 10000, _local(15, 9)),
    ])
    await db.commit()

    board = await lb.steps_leaderboard(db, me.id, "day", "friends", now=NOW)
    ranks = {e.user_id: e.rank for e in board.entries}
    assert ranks == {me.id: 1, tie_a.id: 2, tie_b.id: 2, idle.id: 4}
    assert _steps_by_user(board)[idle.id] == 0
    assert board.me is not None and board.me.rank == 1

    limited = await lb.steps_leaderboard(db, me.id, "day", "friends", limit=1, now=NOW)
    assert [e.user_id for e in limited.entries] == [me.id]


async def test_viewer_off_is_hidden_but_sees_friends(pg_db):
    db, ids = pg_db
    me = await _user(db, ids, "Me", "off")
    friend = await _user(db, ids, "Friend", "friends")
    db.add(_befriend(me, friend))
    db.add_all([_steps(me, 9000, _local(15, 9)), _steps(friend, 3000, _local(15, 9))])
    await db.commit()

    board = await lb.steps_leaderboard(db, me.id, "day", "friends", now=NOW)
    assert board.me is None
    assert [(e.user_id, e.rank) for e in board.entries] == [(friend.id, 1)]

    # The friend does not see the hidden viewer either.
    theirs = await lb.steps_leaderboard(db, friend.id, "day", "friends", now=NOW)
    assert [e.user_id for e in theirs.entries] == [friend.id]


async def test_global_board_only_lists_opted_in_users(pg_db):
    db, ids = pg_db
    me = await _user(db, ids, "Me", "global")
    friends_only = await _user(db, ids, "FriendsOnly", "friends")
    leader = await _user(db, ids, "Leader", "global")
    db.add(_befriend(me, friends_only))
    db.add_all([
        _steps(me, 7000, _local(15, 9)),
        _steps(friends_only, 8000, _local(15, 9)),
        _steps(leader, 10000, _local(15, 9)),
    ])
    await db.commit()

    board = await lb.steps_leaderboard(db, me.id, "day", "global", now=NOW)
    ranks = {e.user_id: e.rank for e in board.entries}
    assert friends_only.id not in ranks
    assert ranks[leader.id] == 1 and ranks[me.id] == 2
    assert board.me is not None and board.me.rank == 2

    # Outside the top-N the viewer still gets an exact rank.
    top1 = await lb.steps_leaderboard(db, me.id, "day", "global", limit=1, now=NOW)
    assert [e.user_id for e in top1.entries] == [leader.id]
    assert top1.me is not None and top1.me.rank == 2 and top1.me.steps == 7000

    # A friends-only viewer browses the global board without appearing on it.
    other = await lb.steps_leaderboard(db, friends_only.id, "day", "global", now=NOW)
    assert other.me is None
    assert friends_only.id not in {e.user_id for e in other.entries}
