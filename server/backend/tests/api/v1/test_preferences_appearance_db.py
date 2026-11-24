"""PATCH /v1/preferences/me merges `appearance` in the real JSONB column (needs Postgres)."""
from __future__ import annotations

import uuid
from types import SimpleNamespace

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy import delete, select

from app.adapters.database import AsyncSessionLocal, engine
from app.core.security import get_current_user
from app.main import app
from app.models.core import User, UserPreference

pytestmark = pytest.mark.skipif(
    not __import__("app.core.config", fromlist=["settings"]).settings.database_url,
    reason="needs real Postgres",
)

NOTIFICATION_PREFS = {"enabled": False, "snooze_options": [15]}


@pytest_asyncio.fixture
async def user_with_prefs():
    user_id = uuid.uuid4()
    async with AsyncSessionLocal() as db:
        db.add(
            User(
                id=user_id,
                email=f"prefs-{user_id.hex[:8]}@test.local",
                username=f"prefs_{user_id.hex[:8]}",
                display_name="Prefs Test",
                hashed_password="x",
            )
        )
        await db.flush()
        db.add(
            UserPreference(
                user_id=user_id,
                appearance={"notification_preferences": NOTIFICATION_PREFS},
            )
        )
        await db.commit()

    async def override_user():
        return SimpleNamespace(id=user_id, email="prefs@test.local", hashed_password="x")

    app.dependency_overrides[get_current_user] = override_user
    try:
        yield user_id
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        async with AsyncSessionLocal() as db:
            await db.execute(delete(User).where(User.id == user_id))
            await db.commit()
        await engine.dispose()


async def test_patch_appearance_merges_jsonb_and_visibility_persists(user_with_prefs):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        res = await client.patch(
            "/v1/preferences/me",
            json={"appearance": {"background": "solid"}, "leaderboard_visibility": "global"},
        )
        assert res.status_code == 200
        assert res.json()["data"]["appearance"] == {"background": "solid"}

        again = await client.get("/v1/preferences/me")
        assert again.json()["data"]["leaderboard_visibility"] == "global"

    async with AsyncSessionLocal() as db:
        pref = (
            await db.execute(select(UserPreference).where(UserPreference.user_id == user_with_prefs))
        ).scalar_one()
        assert pref.appearance == {
            "notification_preferences": NOTIFICATION_PREFS,
            "background": "solid",
        }
        assert pref.leaderboard_visibility == "global"
