"""Social graph models: friendships between users.

One row per unordered user pair. The pair is unique regardless of who sent
the request (expression index on LEAST/GREATEST), so a reverse request reuses
the existing row instead of creating a second one.
"""
from __future__ import annotations

import datetime
import uuid
from enum import Enum

from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Index, String, func, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.core import Base


class FriendshipStatusEnum(str, Enum):
    PENDING = "pending"
    ACCEPTED = "accepted"
    DECLINED = "declined"
    BLOCKED = "blocked"


class Friendship(Base):
    __tablename__ = "friendships"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )
    requester_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    addressee_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    # Plain string (not a PG enum) so new states never need an enum migration.
    status: Mapped[str] = mapped_column(
        String(16),
        nullable=False,
        default=FriendshipStatusEnum.PENDING.value,
        server_default=text("'pending'"),
    )
    responded_at: Mapped[datetime.datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    created_at: Mapped[datetime.datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )
    updated_at: Mapped[datetime.datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    __table_args__ = (
        CheckConstraint("requester_id <> addressee_id", name="ck_friendships_not_self"),
        Index("ix_friendships_addressee_status", "addressee_id", "status"),
    )


# Unordered pair uniqueness: (a, b) and (b, a) collide.
Index(
    "uq_friendships_pair",
    func.least(Friendship.requester_id, Friendship.addressee_id),
    func.greatest(Friendship.requester_id, Friendship.addressee_id),
    unique=True,
)
