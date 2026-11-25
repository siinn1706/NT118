"""Realtime + in-app notification fanout for friend requests (no DB)."""
from __future__ import annotations

import datetime
import uuid
from types import SimpleNamespace

import pytest

from app.services import friends as friends_svc

NOW = datetime.datetime(2026, 11, 24, 8, 0, tzinfo=datetime.timezone.utc)


def _user(name: str) -> SimpleNamespace:
    return SimpleNamespace(
        id=uuid.uuid4(),
        username=name.lower(),
        display_name=name,
        profile=SimpleNamespace(avatar_url=f"https://cdn.test/{name}.png"),
    )


class _Recorder:
    def __init__(self) -> None:
        self.frames: list[tuple[str, dict]] = []
        self.notifications: list[dict] = []

    async def send_to_user(self, user_id: str, message: dict, **_kwargs) -> None:
        self.frames.append((user_id, message))

    async def enqueue(self, _db, user_id, **kwargs):
        self.notifications.append({"user_id": user_id, **kwargs})


@pytest.fixture
def wired(monkeypatch):
    requester, addressee = _user("Minh"), _user("Lan")
    users = {requester.id: requester, addressee.id: addressee}
    recorder = _Recorder()

    async def fake_load_user(_db, user_id):
        return users.get(user_id)

    async def fake_locale(_db, user_id):
        return "vi" if user_id == addressee.id else "en"

    async def fake_conversations(_db, _me, _others):
        return {}

    monkeypatch.setattr(friends_svc, "_load_user", fake_load_user)
    monkeypatch.setattr(friends_svc, "_recipient_locale", fake_locale)
    monkeypatch.setattr(friends_svc, "_direct_conversation_ids", fake_conversations)
    monkeypatch.setattr(friends_svc.ws_manager, "send_to_user", recorder.send_to_user)
    monkeypatch.setattr(friends_svc.ws_manager, "get_presence_map", lambda: {str(requester.id): True})
    monkeypatch.setattr(friends_svc.notifications_svc, "enqueue", recorder.enqueue)
    friendship = SimpleNamespace(
        id=uuid.uuid4(),
        requester_id=requester.id,
        addressee_id=addressee.id,
        status="pending",
        created_at=NOW,
        updated_at=NOW,
        responded_at=None,
    )
    return SimpleNamespace(requester=requester, addressee=addressee, friendship=friendship, rec=recorder)


@pytest.mark.asyncio
async def test_request_received_frame_and_inbox(wired):
    await friends_svc.notify_request_received(SimpleNamespace(), wired.friendship)

    assert len(wired.rec.frames) == 1
    user_id, frame = wired.rec.frames[0]
    assert user_id == str(wired.addressee.id)
    assert frame["event"] == "friend.request.received"
    payload = frame["payload"]
    assert payload["id"] == str(wired.friendship.id)
    assert payload["direction"] == "incoming"
    assert payload["status"] == "pending"
    assert payload["user"]["id"] == str(wired.requester.id)
    # The requester is online, but the addressee is not a friend yet.
    assert payload["user"]["is_online"] is False
    assert "timestamp" in frame

    assert len(wired.rec.notifications) == 1
    note = wired.rec.notifications[0]
    assert note["user_id"] == wired.addressee.id
    assert note["kind"] == "social"
    assert note["reference_id"] == wired.friendship.id
    assert note["commit"] is True
    assert note["title"] == "Lời mời kết bạn mới"
    assert "Minh" in note["body"]


@pytest.mark.parametrize(("status", "expected"), [("pending", False), ("declined", False), ("accepted", True)])
def test_request_dto_shows_presence_only_once_accepted(wired, status, expected):
    wired.friendship.status = status
    presence = {str(wired.addressee.id): True, str(wired.requester.id): True}

    dto = friends_svc._request_dto(wired.friendship, wired.requester.id, wired.addressee, presence)

    assert dto.direction == "outgoing"
    assert dto.user.is_online is expected


@pytest.mark.asyncio
async def test_request_accepted_frame_and_inbox(wired):
    wired.friendship.status = "accepted"
    wired.friendship.responded_at = NOW
    conversation_id = uuid.uuid4()

    await friends_svc.notify_request_accepted(SimpleNamespace(), wired.friendship, conversation_id)

    user_id, frame = wired.rec.frames[0]
    assert user_id == str(wired.requester.id)
    assert frame["event"] == "friend.request.accepted"
    assert frame["payload"]["request_id"] == str(wired.friendship.id)
    friend = frame["payload"]["friend"]
    assert friend["user"]["id"] == str(wired.addressee.id)
    assert friend["direct_conversation_id"] == str(conversation_id)

    note = wired.rec.notifications[0]
    assert note["user_id"] == wired.requester.id
    assert note["title"] == "Friend request accepted"
    assert "Lan" in note["body"]


@pytest.mark.asyncio
async def test_inbox_failure_does_not_raise(wired, monkeypatch):
    rolled_back: list[bool] = []

    async def boom(*_args, **_kwargs):
        raise RuntimeError("db down")

    async def rollback():
        rolled_back.append(True)

    monkeypatch.setattr(friends_svc.notifications_svc, "enqueue", boom)
    await friends_svc.notify_request_received(SimpleNamespace(rollback=rollback), wired.friendship)

    assert wired.rec.frames and wired.rec.frames[0][1]["event"] == "friend.request.received"
    assert rolled_back == [True]
