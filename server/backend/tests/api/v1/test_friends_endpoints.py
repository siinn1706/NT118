"""Endpoint tests for /v1/friends (no live Postgres required).

Auth, validation and response shapes are checked with dependency overrides,
an in-memory Redis for the rate limiter and a monkeypatched service layer.
"""
from __future__ import annotations

import datetime
import uuid
from types import SimpleNamespace

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app.adapters.database import get_db
from app.adapters.redis_client import get_redis
from app.core.security import get_current_user
from app.main import app
from app.schemas.social import FriendDTO, FriendRequestDTO, FriendSearchResult, FriendUserDTO
from app.services import friends as friends_svc

NOW = datetime.datetime(2026, 11, 24, 8, 0, tzinfo=datetime.timezone.utc)


class _MemoryRedis:
    def __init__(self) -> None:
        self.values: dict[str, str] = {}

    async def eval(self, _script: str, _numkeys: int, key: str, _window_seconds: str):
        count = int(self.values.get(key, "0")) + 1
        self.values[key] = str(count)
        return count


class _FakeSession:
    def __init__(self) -> None:
        self.commits = 0

    async def commit(self) -> None:
        self.commits += 1

    async def rollback(self) -> None:
        return None


@pytest.fixture
def me():
    return SimpleNamespace(id=uuid.uuid4(), email="friends@example.com", hashed_password="x")


@pytest_asyncio.fixture
async def client(me):
    session = _FakeSession()
    redis = _MemoryRedis()

    async def override_user():
        return me

    async def override_db():
        yield session

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[get_redis] = lambda: redis
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        c.session = session  # type: ignore[attr-defined]
        yield c
    app.dependency_overrides.clear()


def _user_dto(**overrides) -> FriendUserDTO:
    data = {
        "id": uuid.uuid4(),
        "username": "lan_nguyen",
        "display_name": "Lan Nguyen",
        "avatar_url": None,
        "is_online": True,
    }
    data.update(overrides)
    return FriendUserDTO(**data)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("method", "path"),
    [
        ("get", "/v1/friends"),
        ("get", "/v1/friends/requests"),
        ("post", "/v1/friends/requests"),
        ("get", "/v1/friends/search?q=lan"),
        ("delete", f"/v1/friends/{uuid.uuid4()}"),
    ],
)
async def test_friends_routes_require_auth(method: str, path: str):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        if method == "post":
            res = await c.post(path, json={"username": "someone"})
        else:
            res = await c.request(method.upper(), path)
    assert res.status_code == 401


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "body",
    [
        {},
        {"username": "lan_nguyen", "user_id": str(uuid.uuid4())},
        {"username": "ab"},
        {"user_id": "not-a-uuid"},
    ],
)
async def test_send_request_validates_target(client, body):
    res = await client.post("/v1/friends/requests", json=body)
    assert res.status_code == 422


@pytest.mark.asyncio
async def test_send_request_to_self_is_400(client, me):
    res = await client.post("/v1/friends/requests", json={"user_id": str(me.id)})
    assert res.status_code == 400
    assert res.json()["error"]["code"] == "FRIEND_SELF"


@pytest.mark.asyncio
async def test_send_request_is_rate_limited(client, me, monkeypatch):
    calls = 0

    async def fake_send(*_args, **_kwargs):
        nonlocal calls
        calls += 1
        raise friends_svc._self_error()

    monkeypatch.setattr(friends_svc, "send_request", fake_send)
    statuses = [
        (await client.post("/v1/friends/requests", json={"username": "lan_nguyen"})).status_code
        for _ in range(21)
    ]
    assert statuses[:20] == [400] * 20
    assert statuses[20] == 429
    assert calls == 20


@pytest.mark.asyncio
async def test_list_requests_rejects_unknown_direction(client):
    res = await client.get("/v1/friends/requests", params={"direction": "sideways"})
    assert res.status_code == 422


@pytest.mark.asyncio
@pytest.mark.parametrize("params", [{}, {"q": ""}, {"q": "x" * 51}, {"q": "lan", "limit": 0}])
async def test_search_validates_query(client, params):
    res = await client.get("/v1/friends/search", params=params)
    assert res.status_code == 422


@pytest.mark.asyncio
async def test_list_friends_empty(client, monkeypatch):
    async def fake_list(_db, _me, _presence=None):
        return []

    monkeypatch.setattr(friends_svc, "list_friends", fake_list)
    res = await client.get("/v1/friends")
    assert res.status_code == 200
    assert res.json() == {"data": []}


@pytest.mark.asyncio
async def test_list_friends_shape(client, monkeypatch):
    conversation_id = uuid.uuid4()
    friend = FriendDTO(user=_user_dto(), since=NOW, direct_conversation_id=conversation_id)

    async def fake_list(_db, _me, _presence=None):
        return [friend]

    monkeypatch.setattr(friends_svc, "list_friends", fake_list)
    res = await client.get("/v1/friends")
    assert res.status_code == 200
    item = res.json()["data"][0]
    assert set(item) == {"user", "since", "direct_conversation_id"}
    assert set(item["user"]) == {"id", "username", "display_name", "avatar_url", "is_online"}
    assert item["direct_conversation_id"] == str(conversation_id)
    assert item["user"]["is_online"] is True


@pytest.mark.asyncio
async def test_send_request_created_notifies_addressee(client, me, monkeypatch):
    friendship = SimpleNamespace(id=uuid.uuid4(), requester_id=me.id, addressee_id=uuid.uuid4())
    seen: list[str] = []

    async def fake_send(_db, requester_id, *, username=None, user_id=None):
        assert requester_id == me.id and username == "lan_nguyen" and user_id is None
        return friendship, False

    async def fake_build(_db, _friendship, viewer_id, _presence=None):
        return FriendRequestDTO(
            id=friendship.id, status="pending", direction="outgoing", user=_user_dto(), created_at=NOW
        )

    async def fake_received(_db, fr):
        seen.append(f"received:{fr.id}")

    async def fake_accepted(_db, fr, _conversation_id=None):
        seen.append(f"accepted:{fr.id}")

    monkeypatch.setattr(friends_svc, "send_request", fake_send)
    monkeypatch.setattr(friends_svc, "build_request_dto", fake_build)
    monkeypatch.setattr(friends_svc, "notify_request_received", fake_received)
    monkeypatch.setattr(friends_svc, "notify_request_accepted", fake_accepted)

    res = await client.post("/v1/friends/requests", json={"username": "lan_nguyen"})
    assert res.status_code == 201
    data = res.json()["data"]
    assert set(data) == {"id", "status", "direction", "user", "created_at"}
    assert data["status"] == "pending" and data["direction"] == "outgoing"
    assert seen == [f"received:{friendship.id}"]
    assert client.session.commits == 1


@pytest.mark.asyncio
async def test_send_request_auto_accept_returns_200(client, me, monkeypatch):
    friendship = SimpleNamespace(id=uuid.uuid4(), requester_id=uuid.uuid4(), addressee_id=me.id)
    seen: list[str] = []

    async def fake_send(_db, _requester_id, *, username=None, user_id=None):
        return friendship, True

    async def fake_build(_db, _friendship, _viewer_id, _presence=None):
        return FriendRequestDTO(
            id=friendship.id, status="accepted", direction="incoming", user=_user_dto(), created_at=NOW
        )

    async def fake_received(_db, fr):
        seen.append("received")

    async def fake_accepted(_db, fr, _conversation_id=None):
        seen.append("accepted")

    monkeypatch.setattr(friends_svc, "send_request", fake_send)
    monkeypatch.setattr(friends_svc, "build_request_dto", fake_build)
    monkeypatch.setattr(friends_svc, "notify_request_received", fake_received)
    monkeypatch.setattr(friends_svc, "notify_request_accepted", fake_accepted)

    res = await client.post("/v1/friends/requests", json={"user_id": str(friendship.requester_id)})
    assert res.status_code == 200
    assert res.json()["data"]["status"] == "accepted"
    assert seen == ["accepted"]


@pytest.mark.asyncio
async def test_accept_request_returns_friend(client, me, monkeypatch):
    request_id = uuid.uuid4()
    conversation_id = uuid.uuid4()
    friendship = SimpleNamespace(id=request_id, requester_id=uuid.uuid4(), addressee_id=me.id)
    notified: list[uuid.UUID | None] = []

    async def fake_accept(_db, rid, viewer_id):
        assert rid == request_id and viewer_id == me.id
        return friendship, conversation_id

    async def fake_build(_db, _friendship, _viewer_id, _presence=None):
        return FriendDTO(user=_user_dto(), since=NOW, direct_conversation_id=conversation_id)

    async def fake_accepted(_db, _fr, conv_id=None):
        notified.append(conv_id)

    monkeypatch.setattr(friends_svc, "accept_request", fake_accept)
    monkeypatch.setattr(friends_svc, "build_friend_dto", fake_build)
    monkeypatch.setattr(friends_svc, "notify_request_accepted", fake_accepted)

    res = await client.post(f"/v1/friends/requests/{request_id}/accept")
    assert res.status_code == 200
    assert res.json()["data"]["direct_conversation_id"] == str(conversation_id)
    assert notified == [conversation_id]


@pytest.mark.asyncio
async def test_accept_unknown_request_is_404(client, monkeypatch):
    async def fake_accept(_db, _rid, _viewer_id):
        raise friends_svc._not_found_request()

    monkeypatch.setattr(friends_svc, "accept_request", fake_accept)
    res = await client.post(f"/v1/friends/requests/{uuid.uuid4()}/accept")
    assert res.status_code == 404
    assert res.json()["error"]["code"] == "FRIEND_REQUEST_NOT_FOUND"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("method", "path_tpl", "service_name"),
    [
        ("post", "/v1/friends/requests/{id}/decline", "decline_request"),
        ("delete", "/v1/friends/requests/{id}", "cancel_request"),
        ("delete", "/v1/friends/{id}", "unfriend"),
    ],
)
async def test_mutations_return_204_and_commit(client, me, monkeypatch, method, path_tpl, service_name):
    target = uuid.uuid4()
    calls: list[tuple] = []

    async def fake(_db, *args):
        calls.append(args)

    monkeypatch.setattr(friends_svc, service_name, fake)
    res = await client.request(method.upper(), path_tpl.format(id=target))
    assert res.status_code == 204
    assert client.session.commits == 1
    assert len(calls) == 1 and target in calls[0] and me.id in calls[0]


@pytest.mark.asyncio
async def test_search_shape_has_no_email(client, monkeypatch):
    request_id = uuid.uuid4()

    async def fake_search(_db, _me, q, limit):
        assert q == "lan" and limit == 5
        return [
            FriendSearchResult(
                id=uuid.uuid4(),
                username="lan_nguyen",
                display_name="Lan Nguyen",
                friendship_status="incoming",
                request_id=request_id,
            )
        ]

    monkeypatch.setattr(friends_svc, "search_users", fake_search)
    res = await client.get("/v1/friends/search", params={"q": "lan", "limit": 5})
    assert res.status_code == 200
    item = res.json()["data"][0]
    assert set(item) == {"id", "username", "display_name", "avatar_url", "friendship_status", "request_id"}
    assert "email" not in item
    assert item["friendship_status"] == "incoming"
    assert item["request_id"] == str(request_id)
