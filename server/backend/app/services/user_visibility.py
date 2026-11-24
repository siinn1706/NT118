"""Shared SQL helpers for user-facing people search.

Kept free of other service imports so both `chat` and `friends` can use it
without an import cycle.
"""
from __future__ import annotations

from sqlalchemy import ColumnElement, and_, func, or_

from app.models.core import User

LIKE_ESCAPE_CHAR = "\\"


def escape_like(term: str) -> str:
    """Escape LIKE/ILIKE wildcards so user input only matches literally.

    Use together with ``escape=LIKE_ESCAPE_CHAR`` on the ``like``/``ilike`` call.
    """
    return (
        term.replace(LIKE_ESCAPE_CHAR, LIKE_ESCAPE_CHAR * 2)
        .replace("%", f"{LIKE_ESCAPE_CHAR}%")
        .replace("_", f"{LIKE_ESCAPE_CHAR}_")
    )


def discoverable_user_clause() -> ColumnElement[bool]:
    """Rows other people may find: real (non-bot), not deleted, not actively banned.

    A ban is active when ``banned_at`` is set and ``banned_until`` is either
    open-ended or still in the future (same rule as ``account_status``).
    """
    return and_(
        User.is_system.is_(False),
        User.deleted_at.is_(None),
        or_(
            User.banned_at.is_(None),
            and_(User.banned_until.is_not(None), User.banned_until <= func.now()),
        ),
    )
