package vn.uit.healthos.feature.climate

import org.junit.Assert.assertEquals
import org.junit.Test

class ClimateCoachTest {

    @Test
    fun hotDayAdds500MlHydrationBonus() {
        val conditions = ClimateConditions(
            temperatureC = 33.0,
            apparentTempC = 35.5,
            uvIndex = 6.0,
            usAqi = 80,
            pm25 = 20.0,
            fetchedAtMs = 1_000L,
        )
        // 70 kg * 35 = 2450 ml + 500 = 2950 ml
        val advice = ClimateCoach.evaluate(conditions, weightKg = 70.0)
        assertEquals(2450, advice.baseHydrationMl)
        assertEquals(500, advice.heatBonusMl)
        assertEquals(2950, advice.totalHydrationTargetMl)
        assertEquals(OutdoorWarning.NONE, advice.outdoorWarning)
    }

    @Test
    fun highAqiTriggersOutdoorWarning() {
        val conditions = ClimateConditions(27.0, 28.0, uvIndex = 4.0, usAqi = 165, pm25 = 82.0, fetchedAtMs = 1L)
        val advice = ClimateCoach.evaluate(conditions, weightKg = 60.0)
        assertEquals(0, advice.heatBonusMl)
        assertEquals(OutdoorWarning.HIGH_AQI, advice.outdoorWarning)
    }

    @Test
    fun highUvAndHighAqiTriggersCombinedWarning() {
        val conditions = ClimateConditions(31.0, 32.0, uvIndex = 9.5, usAqi = 180, pm25 = 95.0, fetchedAtMs = 1L)
        val advice = ClimateCoach.evaluate(conditions, weightKg = 60.0)
        assertEquals(500, advice.heatBonusMl)
        assertEquals(OutdoorWarning.HIGH_AQI_AND_UV, advice.outdoorWarning)
    }

    @Test
    fun missingAqiAndUvReadingsNeverWarn() {
        val conditions = ClimateConditions(30.0, 31.0, uvIndex = null, usAqi = null, pm25 = null, fetchedAtMs = 1L)
        assertEquals(OutdoorWarning.NONE, ClimateCoach.evaluate(conditions, weightKg = 60.0).outdoorWarning)
    }

    @Test
    fun nullConditionsUsesBaseWeightRuleWithoutWarning() {
        val advice = ClimateCoach.evaluate(null, weightKg = 60.0)
        assertEquals(2100, advice.totalHydrationTargetMl)
        assertEquals(OutdoorWarning.NONE, advice.outdoorWarning)
    }
}
