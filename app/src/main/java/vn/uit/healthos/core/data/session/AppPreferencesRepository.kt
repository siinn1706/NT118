package vn.uit.healthos.core.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "healthos_app_preferences",
)

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val appLockEnabled: Boolean = false,
    val onboardingStatus: String? = null,
    val dailyCalorieTarget: Int? = null,
    val targetWeightKg: Float? = null,
    val activityLevel: String? = null,
    val goalDirection: String? = null,
    val stepGoal: Int = 8_000,
)

@Singleton
class AppPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dataStore: DataStore<Preferences> get() = context.appPreferencesDataStore

    val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            appLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false,
            onboardingStatus = prefs[KEY_ONBOARDING_STATUS],
            dailyCalorieTarget = prefs[KEY_DAILY_CALORIE_TARGET],
            targetWeightKg = prefs[KEY_TARGET_WEIGHT_KG],
            activityLevel = prefs[KEY_ACTIVITY_LEVEL],
            goalDirection = prefs[KEY_GOAL_DIRECTION],
            stepGoal = prefs[KEY_STEP_GOAL] ?: 8_000,
        )
    }

    val themeMode: Flow<ThemeMode> = preferences.map { it.themeMode }
    val dynamicColor: Flow<Boolean> = preferences.map { it.dynamicColor }
    val appLockEnabled: Flow<Boolean> = preferences.map { it.appLockEnabled }
    val onboardingStatus: Flow<String?> = preferences.map { it.onboardingStatus }
    val dailyCalorieTarget: Flow<Int?> = preferences.map { it.dailyCalorieTarget }

    suspend fun current(): AppPreferences = preferences.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setOnboardingStatus(status: String) {
        dataStore.edit { it[KEY_ONBOARDING_STATUS] = status }
    }

    suspend fun setNutritionTargets(
        dailyCalorieTarget: Int,
        targetWeightKg: Float?,
        activityLevel: String,
        goalDirection: String,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_CALORIE_TARGET] = dailyCalorieTarget
            if (targetWeightKg != null) {
                prefs[KEY_TARGET_WEIGHT_KG] = targetWeightKg
            } else {
                prefs.remove(KEY_TARGET_WEIGHT_KG)
            }
            prefs[KEY_ACTIVITY_LEVEL] = activityLevel
            prefs[KEY_GOAL_DIRECTION] = goalDirection
        }
    }

    suspend fun setStepGoal(goal: Int) {
        dataStore.edit { it[KEY_STEP_GOAL] = goal.coerceIn(1_000, 50_000) }
    }

    /**
     * Clears what only makes sense while signed in. Nutrition targets stay: they belong to [localDataOwner]
     * and are dropped by [clearNutritionTargets] only when a different account signs in.
     */
    suspend fun clearSessionScoped() {
        dataStore.edit { it.remove(KEY_ONBOARDING_STATUS) }
    }

    suspend fun clearNutritionTargets() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_DAILY_CALORIE_TARGET)
            prefs.remove(KEY_TARGET_WEIGHT_KG)
            prefs.remove(KEY_ACTIVITY_LEVEL)
            prefs.remove(KEY_GOAL_DIRECTION)
        }
    }

    /** User id that the on-device data (caches, outbox, targets, alarms) belongs to. */
    suspend fun localDataOwner(): String? = dataStore.data.first()[KEY_LOCAL_DATA_OWNER]

    suspend fun setLocalDataOwner(userId: String) {
        dataStore.edit { it[KEY_LOCAL_DATA_OWNER] = userId }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_ONBOARDING_STATUS = stringPreferencesKey("onboarding_status")
        val KEY_DAILY_CALORIE_TARGET = intPreferencesKey("daily_calorie_target")
        val KEY_TARGET_WEIGHT_KG = floatPreferencesKey("target_weight_kg")
        val KEY_ACTIVITY_LEVEL = stringPreferencesKey("activity_level")
        val KEY_GOAL_DIRECTION = stringPreferencesKey("goal_direction")
        val KEY_STEP_GOAL = intPreferencesKey("step_goal")
        val KEY_LOCAL_DATA_OWNER = stringPreferencesKey("local_data_owner")
    }
}
