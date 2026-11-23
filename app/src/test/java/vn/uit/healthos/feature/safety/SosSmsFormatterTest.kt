package vn.uit.healthos.feature.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SosSmsFormatterTest {

    private val saigon = ZoneId.of("Asia/Saigon")

    @Test
    fun fallMessageCarriesLocalTimeLocationAndCard() {
        val msg = SosSmsFormatter.formatMessage(
            userName = "Nguyễn Văn An",
            trigger = SosTrigger.FALL.apiValue,
            occurredAt = Instant.parse("2026-11-23T05:30:00Z"),
            location = SosLocation(10.8700, 106.8031, 8.0f),
            cardUrl = "https://healthos.example.com/e/abc123token",
            zone = saigon,
        )
        assertTrue(msg.startsWith("[SOS HealthOS] Nguyễn Văn An phát hiện té ngã lúc 12:30 23/11."))
        assertTrue(msg.contains("maps.google.com/?q=10.87,106.8031"))
        assertTrue(msg.contains("https://healthos.example.com/e/abc123token"))
    }

    @Test
    fun manualMessageWithoutLocationOrCardFallsBackToDefaultName() {
        val msg = SosSmsFormatter.formatMessage(
            userName = " ",
            trigger = SosTrigger.MANUAL.apiValue,
            occurredAt = Instant.parse("2026-11-23T05:30:00Z"),
            location = null,
            cardUrl = null,
            zone = saigon,
        )
        assertEquals("[SOS HealthOS] Người dùng kích hoạt SOS khẩn cấp lúc 12:30 23/11.", msg)
        assertFalse(msg.contains("Vị trí"))
    }
}
