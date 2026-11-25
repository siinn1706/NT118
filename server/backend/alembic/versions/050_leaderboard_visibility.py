"""Add user_preferences.leaderboard_visibility.

Revision ID: 050_leaderboard_visibility
Revises: 049_friendships
Create Date: 2026-11-24

Values: 'off' | 'friends' | 'global'. Default 'friends' so existing users
appear to their friends but never on the global board without opting in.
Users without a preferences row are treated as 'friends' (COALESCE in query).
"""
from __future__ import annotations

from typing import Union

import sqlalchemy as sa
from alembic import op

revision: str = "050_leaderboard_visibility"
down_revision: Union[str, None] = "049_friendships"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "user_preferences",
        sa.Column(
            "leaderboard_visibility",
            sa.String(10),
            server_default=sa.text("'friends'"),
            nullable=False,
        ),
    )
    op.create_check_constraint(
        "ck_user_preferences_leaderboard_visibility",
        "user_preferences",
        "leaderboard_visibility IN ('off', 'friends', 'global')",
    )


def downgrade() -> None:
    op.drop_constraint(
        "ck_user_preferences_leaderboard_visibility",
        "user_preferences",
        type_="check",
    )
    op.drop_column("user_preferences", "leaderboard_visibility")
