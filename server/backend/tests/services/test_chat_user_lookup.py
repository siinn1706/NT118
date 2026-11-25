"""User lookup for starting chats must not leak other users' email addresses.

Email only matches on an exact, case-insensitive full address, and a result
carries its email only when the query was exactly that address.
"""
from __future__ import annotations

import uuid
from types import SimpleNamespace

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy import delete
from sqlalchemy.dialects import postgresql

from app.adapters.database import AsyncSessionLocal, engine, get_db
from app.core.config import settings
from app.core.security import get_current_user
from app.main import app
from app.models.core import User
from app.schemas.chat import UserLookupResult
from app.services import chat as chat_svc

needs_pg = pytest.mark.skipif(not settings.database_url, reason="needs real Postgres")


def _fake_user(display_name: str, email: str) -> SimpleNamespace:
    return SimpleNamespace(id=uuid.uuid4(), display_name=display_name, email=email, profile=None)


class _CapturingSession:
    def __init__(self, users: list[SimpleNamespace]) -> None:
        self.users = users
        self.statements: list = []

    async def execute(self, stmt):
        self.statements.append(stmt)
        users = self.users
        return SimpleNamespace(scalars=lambda: SimpleNamespace(all=lambda: users))


def _sql(stmt) -> str:
    return str(stmt.compile(dialect=postgresql.dialect())).lower()


@pytest.mark.asyncio
async def test_email_is_matched_exactly_not_by_substring():
    db = _CapturingSession([])

    await chat_svc.lookup_users(db, "@gmail", uuid.uuid4())

    sql = _sql(db.statements[0])
    assert "lower(users.email) =" in sql
    assert "users.email ilike" not in sql
    assert "users.display_name ilike" in sql


@pytest.mark.asyncio
async def test_partial_query_never_returns_email():
    lan = _fake_user("Lan Nguyen", "lan@example.com")
    db = _CapturingSession([lan])

    results = await chat_svc.lookup_users(db, "lan", uuid.uuid4())

    assert [r.id for r in results] == [lan.id]
    assert results[0].email is None


@pytest.mark.asyncio
async def test_exact_email_query_echoes_only_that_address():
    lan = _fake_user("Lan Nguyen", "Lan@Example.com")
    other = _fake_user("lan@example.com fan club", "fan@example.com")
    db = _CapturingSession([lan, other])

    results = await chat_svc.lookup_users(db, "  LAN@example.COM ", uuid.uuid4())

    by_id = {r.id: r.email for r in results}
    assert by_id == {lan.id: "Lan@Example.com", other.id: None}


@pytest.mark.asyncio
async def test_blank_query_returns_nothing_without_querying():
    db = _CapturingSession([_fake_user("Anyone", "anyone@example.com")])

    assert await chat_svc.lookup_users(db, "   ", uuid.uuid4()) == []
    assert db.statements == []


@pytest.mark.asyncio
@pytest.mark.parametrize("path", ["/v1/users/lookup", "/v1/chat/users/search"])
async def test_lookup_endpoints_keep_email_field_as_null(monkeypatch, path):
    hit = UserLookupResult(id=uuid.uuid4(), display_name="Lan Nguyen", email=None, avatar_url=None)

    async def fake_lookup(*_args, **_kwargs):
        return [hit]

    async def override_db():
        yield SimpleNamespace()

    monkeypatch.setattr(chat_svc, "lookup_users", fake_lookup)
    app.dependency_overrides[get_current_user] = lambda: SimpleNamespace(id=uuid.uuid4())
    app.dependency_overrides[get_db] = override_db
    try:
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            res = await client.get(path, params={"q": "lan"})
    finally:
        app.dependency_overrides.clear()

    assert res.status_code == 200
    item = res.json()["data"][0]
    assert set(item) == {"id", "display_name", "email", "avatar_url"}
    assert item["email"] is None


# ── Real Postgres ─────────────────────────────────────────────────────────────


@pytest_asyncio.fixture
async def pg_users():
    created: list[uuid.UUID] = []
    tag = uuid.uuid4().hex[:10]
    async with AsyncSessionLocal() as db:
        users = {}
        for name in ("caller", "bob", "carol"):
            user = User(
                email=f"lookup-{name}-{tag}@test.local",
                username=f"lk{name}{tag}",
                display_name=f"{name.title()} {tag}",
                hashed_password="x",
                has_password=True,
            )
            db.add(user)
            await db.flush()
            created.append(user.id)
            users[name] = user
        await db.commit()
        yield db, tag, users
    async with AsyncSessionLocal() as cleanup:
        await cleanup.execute(delete(User).where(User.id.in_(created)))
        await cleanup.commit()
    await engine.dispose()


@needs_pg
@pytest.mark.asyncio
async def test_pg_email_substring_finds_nobody(pg_users):
    db, tag, users = pg_users
    mine = {users["bob"].id, users["carol"].id}

    for query in (f"-{tag}@test", "@test.local", "lookup-bob"):
        results = await chat_svc.lookup_users(db, query, users["caller"].id, limit=20)
        assert not mine & {r.id for r in results}, query


@needs_pg
@pytest.mark.asyncio
async def test_pg_exact_email_finds_user_and_echoes_only_that_email(pg_users):
    db, _tag, users = pg_users
    bob = users["bob"]

    results = await chat_svc.lookup_users(db, bob.email.upper(), users["caller"].id)

    assert [(r.id, r.email) for r in results] == [(bob.id, bob.email)]


@needs_pg
@pytest.mark.asyncio
async def test_pg_display_name_match_hides_email(pg_users):
    db, tag, users = pg_users

    results = await chat_svc.lookup_users(db, tag, users["caller"].id, limit=20)

    assert {r.id for r in results} == {users["bob"].id, users["carol"].id}
    assert all(r.email is None for r in results)
