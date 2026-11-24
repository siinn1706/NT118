package vn.uit.healthos.feature.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.uit.healthos.R
import javax.inject.Inject
import javax.inject.Singleton

/** A tile of the Home bento grid. [id] is what is persisted, so it never changes; [span] is 1 (half) or 2 (full). */
enum class HomeTile(val id: String, val span: Int, @param:StringRes val label: Int) {
    VITAL("vital", 1, R.string.home_vital_score_label),
    STEPS("steps", 1, R.string.home_metric_steps),
    CALORIES("calories", 1, R.string.home_metric_calories),
    HEART_RATE("heart_rate", 1, R.string.home_metric_hr),
    WATER("water", 2, R.string.home_hydration_title),
    NEXT_DOSE("next_dose", 2, R.string.home_next_dose_title),
    AI_TIP("ai_tip", 2, R.string.home_ai_advice_section),
    CLIMATE("climate", 2, R.string.climate_title),
    EXERCISES("exercises", 2, R.string.home_exercises_section),
    WEEKLY_CHART("weekly_chart", 2, R.string.home_trend_title),
    ;

    companion object {
        fun fromId(id: String): HomeTile? = entries.firstOrNull { it.id == id }
    }
}

/** Order and visibility of the Home tiles, chosen in "Sửa bố cục". */
data class HomeLayout(
    val order: List<HomeTile> = HomeTile.entries,
    val hidden: Set<HomeTile> = emptySet(),
) {
    val visible: List<HomeTile> get() = order.filterNot { it in hidden }

    fun isHidden(tile: HomeTile) = tile in hidden

    fun toggled(tile: HomeTile): HomeLayout = copy(hidden = if (tile in hidden) hidden - tile else hidden + tile)

    /** Moves [tile] by [delta] places (negative is up), clamped to the list. */
    fun moved(tile: HomeTile, delta: Int): HomeLayout {
        val from = order.indexOf(tile)
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return this
        val list = order.toMutableList()
        list.removeAt(from)
        list.add(to, tile)
        return copy(order = list)
    }

    fun encodeOrder(): String = order.joinToString(SEPARATOR) { it.id }

    fun encodeHidden(): String = hidden.sortedBy { it.ordinal }.joinToString(SEPARATOR) { it.id }

    companion object {
        private const val SEPARATOR = ","

        /**
         * Restores a stored layout: unknown ids (from a newer or older build) are dropped, duplicates ignored, and
         * tiles the stored order does not know yet are appended in their default position order.
         */
        fun decode(order: String?, hidden: String?): HomeLayout {
            val stored = order.orEmpty().split(SEPARATOR).mapNotNull { HomeTile.fromId(it.trim()) }.distinct()
            val full = stored + HomeTile.entries.filterNot { it in stored }
            val hiddenTiles = hidden.orEmpty().split(SEPARATOR).mapNotNull { HomeTile.fromId(it.trim()) }.toSet()
            return HomeLayout(order = full, hidden = hiddenTiles)
        }

        /**
         * Rows of the two-column grid: two consecutive half tiles share a row; a full tile, or a half tile without a
         * half neighbour, takes a whole row.
         */
        fun rows(tiles: List<HomeTile>): List<List<HomeTile>> {
            val rows = mutableListOf<List<HomeTile>>()
            var pendingHalf: HomeTile? = null
            for (tile in tiles) {
                if (tile.span == 1) {
                    val half = pendingHalf
                    if (half == null) {
                        pendingHalf = tile
                    } else {
                        rows += listOf(half, tile)
                        pendingHalf = null
                    }
                } else {
                    pendingHalf?.let { rows += listOf(it) }
                    pendingHalf = null
                    rows += listOf(tile)
                }
            }
            pendingHalf?.let { rows += listOf(it) }
            return rows
        }
    }
}

private val Context.homeLayoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "healthos_home_layout")

/** Persists the Home layout on this device. */
@Singleton
class HomeLayoutStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dataStore: DataStore<Preferences> get() = context.homeLayoutDataStore

    val layout: Flow<HomeLayout> = dataStore.data.map { prefs ->
        HomeLayout.decode(prefs[KEY_ORDER], prefs[KEY_HIDDEN])
    }

    suspend fun save(layout: HomeLayout) {
        dataStore.edit { prefs ->
            prefs[KEY_ORDER] = layout.encodeOrder()
            prefs[KEY_HIDDEN] = layout.encodeHidden()
        }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_ORDER = stringPreferencesKey("tile_order")
        val KEY_HIDDEN = stringPreferencesKey("hidden_tiles")
    }
}
