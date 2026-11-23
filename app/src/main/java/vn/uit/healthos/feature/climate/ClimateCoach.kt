package vn.uit.healthos.feature.climate

import kotlin.math.roundToInt

data class ClimateConditions(
    val temperatureC: Double,
    val apparentTempC: Double,
    /** Null when the provider returned no reading; never shown or evaluated as 0. */
    val uvIndex: Double?,
    val usAqi: Int?,
    val pm25: Double?,
    val fetchedAtMs: Long,
)

enum class OutdoorWarning {
    NONE,
    HIGH_AQI,
    HIGH_UV,
    HIGH_AQI_AND_UV,
}

data class ClimateAdvice(
    val baseHydrationMl: Int,
    val heatBonusMl: Int,
    val totalHydrationTargetMl: Int,
    val outdoorWarning: OutdoorWarning,
)

/**
 * Pure Climate Coach rule engine:
 * - Base hydration: 35 ml/kg (defaults to 65 kg when profile weight is unknown), rounded to 50 ml.
 * - Heat bonus: +500 ml when apparent temperature >= 32 °C.
 * - Outdoor exercise warning when US AQI > 150 or UV index >= 8.0.
 */
object ClimateCoach {
    const val DEFAULT_WEIGHT_KG = 65.0
    const val HEAT_THRESHOLD_C = 32.0
    const val HEAT_BONUS_ML = 500
    const val AQI_WARNING_THRESHOLD = 150
    const val UV_WARNING_THRESHOLD = 8.0

    fun evaluate(conditions: ClimateConditions?, weightKg: Double?): ClimateAdvice {
        val safeWeight = (weightKg ?: DEFAULT_WEIGHT_KG).coerceIn(30.0, 200.0)
        val rawBase = safeWeight * 35.0
        val baseMl = ((rawBase / 50.0).roundToInt() * 50).coerceAtLeast(1500)
        if (conditions == null) {
            return ClimateAdvice(
                baseHydrationMl = baseMl,
                heatBonusMl = 0,
                totalHydrationTargetMl = baseMl,
                outdoorWarning = OutdoorWarning.NONE,
            )
        }
        val heatBonus = if (conditions.apparentTempC >= HEAT_THRESHOLD_C) HEAT_BONUS_ML else 0
        val highAqi = conditions.usAqi != null && conditions.usAqi > AQI_WARNING_THRESHOLD
        val highUv = conditions.uvIndex != null && conditions.uvIndex >= UV_WARNING_THRESHOLD
        val warning = when {
            highAqi && highUv -> OutdoorWarning.HIGH_AQI_AND_UV
            highAqi -> OutdoorWarning.HIGH_AQI
            highUv -> OutdoorWarning.HIGH_UV
            else -> OutdoorWarning.NONE
        }
        return ClimateAdvice(
            baseHydrationMl = baseMl,
            heatBonusMl = heatBonus,
            totalHydrationTargetMl = baseMl + heatBonus,
            outdoorWarning = warning,
        )
    }
}
