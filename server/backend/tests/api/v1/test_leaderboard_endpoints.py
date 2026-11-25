"""Endpoint tests for GET /v1/leaderboard/steps (no live Postgres required)."""
from __future__ import annotations

import datetime
import uuid
from types import SimpleNamespace

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app.adapters.database import get_db
from app.core.security import get_current_user
from app.main import app
from app.schemas.leaderboard import LeaderboardDTO, LeaderboardEntryDTO
from app.services import leaderboard as leaderboard_svc


@pytest.fixture
def me():
    return SimpleNamespace(id=uuid.uuid4(), email="lb@example.com", hashed_password="x")


@pytest_asyncio.fixture
async def client(me):
    async def override_user():
        return me

    async def override_db():
        yield SimpleNamespace()

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_leaderboard_requires_auth():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        res = await c.get("/v1/leaderboard/steps")
    assert res.status_code == 401


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "params",
    [
        {"period": "month"},
        {"scope": "everyone"},
        {"limit": 0},
        {"limit": 101},
    ],
)
async def test_leaderboard_validates_query(client, params):
    res = await client.get("/v1/leaderboard/steps", params=params)
    assert res.status_code == 422


@pytest.mark.asyncio
async def test_leaderboard_shape_and_defaults(client, me, monkeypatch):
    captured: dict = {}
    friend_id = uuid.uuid4()

    async def fake_board(_db, viewer_id, period, scope, limit):
        captured.update(viewer_id=viewer_id, period=period, scope=scope, limit=limit)
        me_entry = LeaderboardEntryDTO(
            rank=2, user_id=me.id, username="me", display_name="Me", steps=4200, is_me=True
        )
        return LeaderboardDTO(
            period=period,
            scope=scope,
            timezone=leaderboard_svc.LEADERBOARD_TZ,
            range_start=datetime.date(2026, 11, 24),
            range_end=datetime.date(2026, 11, 24),
            entries=[
                LeaderboardEntryDTO(
                    rank=1, user_id=friend_id, username="lan", display_name="Lan", steps=9000
                ),
                me_entry,
            ],
            me=me_entry,
            generated_at=datetime.datetime(2026, 11, 24, 8, 0, tzinfo=datetime.timezone.utc),
        )

    monkeypatch.setattr(leaderboard_svc, "steps_leaderboard", fake_board)
    res = await client.get("/v1/leaderboard/steps")
    assert res.status_code == 200
    assert captured == {"viewer_id": me.id, "period": "day", "scope": "friends", "limit": 50}

    data = res.json()["data"]
    assert set(data) == {
        "metric", "period", "scope", "timezone", "range_start", "range_end",
        "entries", "me", "generated_at",
    }
    assert data["metric"] == "steps"
    assert data["timezone"] == "Asia/Ho_Chi_Minh"
    assert data["range_start"] == "2026-11-24"
    assert set(data["entries"][0]) == {
        "rank", "user_id", "username", "display_name", "avatar_url", "steps", "is_me",
    }
    assert [e["rank"] for e in data["entries"]] == [1, 2]
    assert data["me"]["is_me"] is True


@pytest.mark.asyncio
async def test_leaderboard_passes_week_global(client, monkeypatch):
    captured: dict = {}

    async def fake_board(_db, _viewer_id, period, scope, limit):
        captured.update(period=period, scope=scope, limit=limit)
        return LeaderboardDTO(
            period=period,
            scope=scope,
            timezone=leaderboard_svc.LEADERBOARD_TZ,
            range_start=datetime.date(2026, 11, 23),
            range_end=datetime.date(2026, 11, 24),
            entries=[],
            me=None,
            generated_at=datetime.datetime(2026, 11, 24, 8, 0, tzinfo=datetime.timezone.utc),
        )

    monkeypatch.setattr(leaderboard_svc, "steps_leaderboard", fake_board)
    res = await client.get(
        "/v1/leaderboard/steps", params={"period": "week", "scope": "global", "limit": 100}
    )
    assert res.status_code == 200
    assert captured == {"period": "week", "scope": "global", "limit": 100}
    assert res.json()["data"]["me"] is None
