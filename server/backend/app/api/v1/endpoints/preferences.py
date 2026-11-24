"""User preferences endpoints — GET/PATCH /preferences/me."""
from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.adapters.database import get_db
from app.core.security import get_current_user
from app.models.core import User, UserPreference
from app.schemas.preferences import (
    AppearanceData,
    UserPreferenceData,
    UserPreferenceResponse,
    UserPreferenceUpdate,
)

router = APIRouter(prefix="/preferences", tags=["Preferences"])

# NOT NULL columns: an explicit null in PATCH means "leave unchanged".
_NON_NULLABLE_FIELDS = frozenset({"theme_mode", "locale", "leaderboard_visibility"})
_LEADERBOARD_VISIBILITIES = ("off", "friends", "global")
_BACKGROUND_STYLES = ("aurora", "soft", "solid")


def _default_prefs() -> UserPreferenceData:
    return UserPreferenceData(theme_mode="system", accent_color=None, locale="en")


def _appearance_to_data(raw: object) -> AppearanceData:
    # The `appearance` JSONB column also holds other features' keys (e.g.
    # notification_preferences); only the documented keys are exposed here.
    stored = raw if isinstance(raw, dict) else {}
    background = stored.get("background")
    if background not in _BACKGROUND_STYLES:
        return AppearanceData()
    return AppearanceData(background=background)


def _pref_to_data(pref: UserPreference) -> UserPreferenceData:
    # ORM defaults (e.g. theme_mode, locale) may be unset on a row that was
    # constructed in-memory before refresh/commit.
    theme = pref.theme_mode or "system"
    raw_loc = getattr(pref, "locale", None) or "en"
    loc = raw_loc if raw_loc in ("en", "vi") else "en"
    raw_visibility = getattr(pref, "leaderboard_visibility", None)
    visibility = raw_visibility if raw_visibility in _LEADERBOARD_VISIBILITIES else "friends"
    return UserPreferenceData(
        theme_mode=theme,
        accent_color=pref.accent_color,
        locale=loc,
        appearance=_appearance_to_data(getattr(pref, "appearance", None)),
        leaderboard_visibility=visibility,
    )


@router.get("/me", response_model=UserPreferenceResponse)
async def get_preferences(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> UserPreferenceResponse:
    """Return current user preferences. Returns defaults if no row exists."""
    result = await db.execute(
        select(UserPreference).where(UserPreference.user_id == current_user.id)
    )
    pref = result.scalar_one_or_none()
    data = _pref_to_data(pref) if pref else _default_prefs()
    return UserPreferenceResponse(data=data)


@router.patch("/me", response_model=UserPreferenceResponse)
async def update_preferences(
    body: UserPreferenceUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> UserPreferenceResponse:
    """Upsert user preferences. Only provided fields are updated."""
    result = await db.execute(
        select(UserPreference).where(UserPreference.user_id == current_user.id)
    )
    pref = result.scalar_one_or_none()

    if pref is None:
        pref = UserPreference(user_id=current_user.id)
        db.add(pref)

    update_data = body.model_dump(exclude_unset=True)
    appearance_update = update_data.pop("appearance", None) or {}
    for field, value in update_data.items():
        if value is None and field in _NON_NULLABLE_FIELDS:
            continue
        setattr(pref, field, value)

    appearance_changes = {k: v for k, v in appearance_update.items() if v is not None}
    if appearance_changes:
        # Merge instead of overwrite: other keys share this JSONB column.
        # Assign a new dict so SQLAlchemy detects the change.
        current = pref.appearance if isinstance(pref.appearance, dict) else {}
        pref.appearance = {**current, **appearance_changes}

    await db.commit()
    await db.refresh(pref)
    return UserPreferenceResponse(data=_pref_to_data(pref))
