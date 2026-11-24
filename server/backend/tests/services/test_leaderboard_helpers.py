"""Pure unit tests for leaderboard period math, ranking and LIKE escaping (no DB)."""
from __future__ import annotations

import datetime
from zoneinfo import ZoneInfo

import pytest

from app.services.leaderboard import LEADERBOARD_TZ, competition_ranks, period_bounds, utc_window
from app.services.user_visibility import escape_like

TZ = ZoneInfo(LEADERBOARD_TZ)
UTC = datetime.timezone.utc


def test_period_day_is_today_only():
    today = datetime.date(2026, 11, 26)  # Thursday
    assert period_bounds("day", today) == (today, today)


@pytest.mark.parametrize(
    ("today", "monday"),
    [
        (datetime.date(2026, 11, 23), datetime.date(2026, 11, 23)),  # Monday
        (datetime.date(2026, 11, 26), datetime.date(2026, 11, 23)),  # Thursday
        (datetime.date(2026, 11, 29), datetime.date(2026, 11, 23)),  # Sunday
        (datetime.date(2027, 1, 1), datetime.date(2026, 12, 28)),  # crosses a year
    ],
)
def test_period_week_starts_on_monday(today, monday):
    assert period_bounds("week", today) == (monday, today)


def test_period_rejects_unknown():
    with pytest.raises(ValueError):
        period_bounds("month", datetime.date(2026, 11, 24))  # type: ignore[arg-type]


def test_utc_window_uses_local_midnights():
    start, end = utc_window(datetime.date(2026, 11, 24), datetime.date(2026, 11, 24), TZ)
    assert start == datetime.datetime(2026, 11, 23, 17, 0, tzinfo=UTC)
    assert end == datetime.datetime(2026, 11, 24, 17, 0, tzinfo=UTC)


def test_utc_window_early_morning_belongs_to_local_day():
    # 05:00 local on the 24th is 22:00 UTC on the 23rd and must fall inside the 24th.
    start, end = utc_window(datetime.date(2026, 11, 24), datetime.date(2026, 11, 24), TZ)
    early = datetime.datetime(2026, 11, 23, 22, 0, tzinfo=UTC)
    assert start <= early < end
    assert early.astimezone(TZ).date() == datetime.date(2026, 11, 24)


def test_utc_window_week_spans_seven_days():
    start, end = utc_window(datetime.date(2026, 11, 23), datetime.date(2026, 11, 29), TZ)
    assert end - start == datetime.timedelta(days=7)


def test_competition_ranks_shares_ties_and_skips():
    scores = [9000, 7000, 7000, 5000, 0, 0]
    ranked = competition_ranks(scores, lambda s: s)
    assert [rank for rank, _ in ranked] == [1, 2, 2, 4, 5, 5]


def test_competition_ranks_empty_and_single():
    assert competition_ranks([], lambda s: s) == []
    assert competition_ranks([42], lambda s: s) == [(1, 42)]


@pytest.mark.parametrize(
    ("raw", "escaped"),
    [
        ("lan", "lan"),
        ("100%", "100\\%"),
        ("a_b", "a\\_b"),
        ("back\\slash", "back\\\\slash"),
        ("%_\\", "\\%\\_\\\\"),
    ],
)
def test_escape_like(raw, escaped):
    assert escape_like(raw) == escaped
