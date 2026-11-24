"""Tests for GET/PATCH /v1/preferences/me including locale (no live Postgres required)."""

import uuid
from typing import Any

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from pydantic import ValidationError

from app.adapters.database import get_db
from app.core.security import get_current_user
from app.main import app
from app.models.core import UserPreference
from app.schemas.preferences import UserPreferenceData, UserPreferenceUpdate


@pytest.fixture
def authenticated_user():
    return type(
        "User",
        (),
        {
            "id": uuid.uuid4(),
            "email": "prefs@example.com",
            "hashed_password": "fake",
        },
    )()


@pytest_asyncio.fixture
async def authenticated_client(authenticated_user):
    async def override_get_current_user():
        return authenticated_user

    app.dependency_overrides[get_current_user] = override_get_current_user
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_preferences_me_requires_auth():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        res = await client.get("/v1/preferences/me")
    assert res.status_code == 401


def test_user_preference_data_includes_locale_default():
    d = UserPreferenceData()
    assert d.locale == "en"


def test_user_preference_update_rejects_invalid_locale():
    with pytest.raises(ValidationError):
        UserPreferenceUpdate(locale="fr")  # type: ignore[arg-type]


@pytest.mark.asyncio
async def test_get_preferences_includes_locale(authenticated_user, authenticated_client):
    class _Result:
        def __init__(self, pref: Any):
            self._pref = pref

        def scalar_one_or_none(self):
            return self._pref

    class _FakeSession:
        def __init__(self):
            self.pref: UserPreference | None = None

        async def execute(self, _stmt: object):
            return _Result(self.pref)

        def add(self, obj: UserPreference) -> None:
            self.pref = obj

        async def commit(self) -> None:
            return None

        async def refresh(self, _obj: UserPreference) -> None:
            return None

    session = _FakeSession()

    async def override_db():
        yield session

    app.dependency_overrides[get_db] = override_db
    try:
        res = await authenticated_client.get("/v1/preferences/me")
        assert res.status_code == 200
        body = res.json()["data"]
        assert body["theme_mode"] == "system"
        assert body["locale"] == "en"
    finally:
        app.dependency_overrides.pop(get_db, None)


@pytest.mark.asyncio
async def test_patch_preferences_accepts_locale(authenticated_user, authenticated_client):
    class _Result:
        def __init__(self, pref: Any):
            self._pref = pref

        def scalar_one_or_none(self):
            return self._pref

    class _FakeSession:
        def __init__(self):
            self.pref: UserPreference | None = None

        async def execute(self, _stmt: object):
            return _Result(self.pref)

        def add(self, obj: UserPreference) -> None:
            self.pref = obj

        async def commit(self) -> None:
            return None

        async def refresh(self, _obj: UserPreference) -> None:
            return None

    session = _FakeSession()

    async def override_db():
        yield session

    app.dependency_overrides[get_db] = override_db
    try:
        res = await authenticated_client.patch("/v1/preferences/me", json={"locale": "vi"})
        assert res.status_code == 200
        assert res.json()["data"]["locale"] == "vi"
        assert session.pref is not None
        assert session.pref.locale == "vi"
    finally:
        app.dependency_overrides.pop(get_db, None)


@pytest.mark.asyncio
async def test_notifications_preferences_defaults(authenticated_client):
    class _Result:
        def __init__(self, pref: Any):
            self._pref = pref

        def scalar_one_or_none(self):
            return self._pref

    class _FakeSession:
        def __init__(self):
            self.pref: UserPreference | None = None

        async def execute(self, _stmt: object):
            return _Result(self.pref)

        def add(self, obj: UserPreference) -> None:
            self.pref = obj

        async def flush(self) -> None:
            return None

        async def commit(self) -> None:
            return None

    session = _FakeSession()

    async def override_db():
        yield session

    app.dependency_overrides[get_db] = override_db
    try:
        res = await authenticated_client.get("/v1/notifications/preferences")
        assert res.status_code == 200
        payload = res.json()["data"]
        assert payload["enabled"] is True
        assert payload["categories"]["med"] is True
        assert payload["channels"]["push"] is True
        assert payload["quiet_hours"]["start"] == "22:00"
        assert payload["snooze_options"] == [5, 10, 30]
    finally:
        app.dependency_overrides.pop(get_db, None)


@pytest.mark.asyncio
async def test_notifications_preferences_patch_sanitizes_snooze_options(authenticated_client):
    class _Result:
        def __init__(self, pref: Any):
            self._pref = pref

        def scalar_one_or_none(self):
            return self._pref

    class _FakeSession:
        def __init__(self):
            self.pref: UserPreference | None = None

        async def execute(self, _stmt: object):
            return _Result(self.pref)

        def add(self, obj: UserPreference) -> None:
            self.pref = obj

        async def flush(self) -> None:
            return None

        async def commit(self) -> None:
            return None

    session = _FakeSession()

    async def override_db():
        yield session

    app.dependency_overrides[get_db] = override_db
    try:
        res = await authenticated_client.patch(
            "/v1/notifications/preferences",
            json={
                "enabled": True,
                "snooze_options": [15, 0, -1, 15, 9999],
            },
        )
        assert res.status_code == 200
        payload = res.json()["data"]
        assert payload["snooze_options"] == [15]
        assert session.pref is not None
        assert isinstance(session.pref.appearance, dict)
        assert session.pref.appearance["notification_preferences"]["snooze_options"] == [15]
    finally:
        app.dependency_overrides.pop(get_db, None)


class _PrefResult:
    def __init__(self, pref: Any):
        self._pref = pref

    def scalar_one_or_none(self):
        return self._pref


class _PrefSession:
    def __init__(self, pref: UserPreference | None = None):
        self.pref = pref

    async def execute(self, _stmt: object):
        return _PrefResult(self.pref)

    def add(self, obj: UserPreference) -> None:
        self.pref = obj

    async def commit(self) -> None:
        return None

    async def refresh(self, _obj: UserPreference) -> None:
        return None


@pytest_asyncio.fixture
async def pref_session(authenticated_client):
    session = _PrefSession()

    async def override_db():
        yield session

    app.dependency_overrides[get_db] = override_db
    try:
        yield session
    finally:
        app.dependency_overrides.pop(get_db, None)


def test_user_preference_data_appearance_and_visibility_defaults():
    d = UserPreferenceData()
    assert d.appearance.background == "aurora"
    assert d.leaderboard_visibility == "friends"


@pytest.mark.parametrize(
    "payload",
    [
        {"appearance": {"background": "ocean"}},
        {"leaderboard_visibility": "public"},
    ],
)
def test_user_preference_update_rejects_invalid_appearance_and_visibility(payload):
    with pytest.raises(ValidationError):
        UserPreferenceUpdate(**payload)


@pytest.mark.asyncio
async def test_get_preferences_defaults_include_appearance(authenticated_client, pref_session):
    res = await authenticated_client.get("/v1/preferences/me")
    assert res.status_code == 200
    body = res.json()["data"]
    assert body["appearance"] == {"background": "aurora"}
    assert body["leaderboard_visibility"] == "friends"


@pytest.mark.asyncio
async def test_get_preferences_hides_other_appearance_keys(
    authenticated_user, authenticated_client, pref_session
):
    pref_session.pref = UserPreference(
        user_id=authenticated_user.id,
        theme_mode="dark",
        locale="vi",
        leaderboard_visibility="global",
        appearance={"background": "solid", "notification_preferences": {"enabled": False}},
    )
    res = await authenticated_client.get("/v1/preferences/me")
    body = res.json()["data"]
    assert body["appearance"] == {"background": "solid"}
    assert body["leaderboard_visibility"] == "global"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "payload",
    [
        {"appearance": {"background": "ocean"}},
        {"leaderboard_visibility": "everyone"},
    ],
)
async def test_patch_preferences_rejects_invalid_values(authenticated_client, pref_session, payload):
    res = await authenticated_client.patch("/v1/preferences/me", json=payload)
    assert res.status_code == 422


@pytest.mark.asyncio
async def test_patch_appearance_merges_and_keeps_unknown_keys(
    authenticated_user, authenticated_client, pref_session
):
    notification_prefs = {"enabled": False, "snooze_options": [15]}
    pref_session.pref = UserPreference(
        user_id=authenticated_user.id,
        theme_mode="system",
        locale="en",
        appearance={"notification_preferences": notification_prefs, "future_key": 1},
    )
    original = pref_session.pref.appearance

    res = await authenticated_client.patch(
        "/v1/preferences/me",
        json={"appearance": {"background": "soft"}, "leaderboard_visibility": "off"},
    )
    assert res.status_code == 200
    body = res.json()["data"]
    assert body["appearance"] == {"background": "soft"}
    assert body["leaderboard_visibility"] == "off"
    stored = pref_session.pref.appearance
    assert stored == {
        "notification_preferences": notification_prefs,
        "future_key": 1,
        "background": "soft",
    }
    # A new dict is assigned so the JSONB change is tracked.
    assert stored is not original


@pytest.mark.asyncio
async def test_patch_null_appearance_and_visibility_leave_values(
    authenticated_user, authenticated_client, pref_session
):
    pref_session.pref = UserPreference(
        user_id=authenticated_user.id,
        theme_mode="system",
        locale="en",
        leaderboard_visibility="global",
        appearance={"background": "solid"},
    )
    res = await authenticated_client.patch(
        "/v1/preferences/me",
        json={"appearance": None, "leaderboard_visibility": None, "accent_color": "#1965b3"},
    )
    assert res.status_code == 200
    body = res.json()["data"]
    assert body["appearance"] == {"background": "solid"}
    assert body["leaderboard_visibility"] == "global"
    assert body["accent_color"] == "#1965B3"
