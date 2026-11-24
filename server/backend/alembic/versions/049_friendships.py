"""Add friendships table.

Revision ID: 049_friendships
Revises: 048_uploaded_by_fk_indexes
Create Date: 2026-11-24

One row per unordered user pair; the LEAST/GREATEST expression index makes
(a, b) and (b, a) collide so a reverse request can never create a duplicate.
Status is a plain String(16) like messages.status to avoid PG enum migrations.
"""
from __future__ import annotations

from typing import Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import UUID

revision: str = "049_friendships"
down_revision: Union[str, None] = "048_uploaded_by_fk_indexes"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "friendships",
        sa.Column("id", UUID(as_uuid=True), nullable=False),
        sa.Column("requester_id", UUID(as_uuid=True), nullable=False),
        sa.Column("addressee_id", UUID(as_uuid=True), nullable=False),
        sa.Column("status", sa.String(16), server_default=sa.text("'pending'"), nullable=False),
        sa.Column("responded_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["requester_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["addressee_id"], ["users.id"], ondelete="CASCADE"),
        sa.CheckConstraint("requester_id <> addressee_id", name="ck_friendships_not_self"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_friendships_requester_id", "friendships", ["requester_id"])
    op.create_index("ix_friendships_addressee_id", "friendships", ["addressee_id"])
    op.create_index("ix_friendships_addressee_status", "friendships", ["addressee_id", "status"])
    op.execute(
        "CREATE UNIQUE INDEX uq_friendships_pair ON friendships "
        "(LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))"
    )


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS uq_friendships_pair")
    op.drop_index("ix_friendships_addressee_status", table_name="friendships")
    op.drop_index("ix_friendships_addressee_id", table_name="friendships")
    op.drop_index("ix_friendships_requester_id", table_name="friendships")
    op.drop_table("friendships")
