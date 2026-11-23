package vn.uit.healthos.feature.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTokenPolicyTest {

    private val now = QrTokenPolicy.parseInstantMs("2026-11-23T08:00:00Z")!!
    private val stored = StoredQrToken(id = "t1", shareUrl = "https://healthos.example/e/abc", expiresAtMs = null)

    private fun token(
        id: String,
        active: Boolean = true,
        expiresAt: String? = null,
        label: String = QrTokenPolicy.APP_TOKEN_LABEL,
        createdAt: String = "2026-11-01T00:00:00Z",
    ) = EmergencyShareTokenDto(id = id, label = label, createdAt = createdAt, expiresAt = expiresAt, isActive = active)

    @Test
    fun noStoredTokenMustMint() {
        assertFalse(QrTokenPolicy.isReusable(null, listOf(token("t1")), now))
        assertFalse(QrTokenPolicy.isReusable(null, null, now))
    }

    @Test
    fun storedTokenStillActiveOnServerIsReused() {
        assertTrue(QrTokenPolicy.isReusable(stored, listOf(token("t0"), token("t1")), now))
    }

    @Test
    fun revokedOrUnknownTokenMustMint() {
        assertFalse(QrTokenPolicy.isReusable(stored, listOf(token("t1", active = false)), now))
        assertFalse(QrTokenPolicy.isReusable(stored, listOf(token("t2")), now))
        assertFalse(QrTokenPolicy.isReusable(stored, emptyList(), now))
    }

    @Test
    fun expiryIsHonouredLocallyAndOnServer() {
        val expiredLocally = stored.copy(expiresAtMs = now)
        assertFalse(QrTokenPolicy.isReusable(expiredLocally, listOf(token("t1")), now))
        assertFalse(QrTokenPolicy.isReusable(expiredLocally, null, now))

        assertFalse(QrTokenPolicy.isReusable(stored, listOf(token("t1", expiresAt = "2026-11-23T07:59:59Z")), now))
        assertTrue(QrTokenPolicy.isReusable(stored, listOf(token("t1", expiresAt = "2026-11-24T00:00:00Z")), now))
    }

    @Test
    fun offlineTrustsStoredTokenUntilItsOwnExpiry() {
        assertTrue(QrTokenPolicy.isReusable(stored, null, now))
        assertTrue(QrTokenPolicy.isReusable(stored.copy(expiresAtMs = now + 1), null, now))
    }

    @Test
    fun urlSavedWithoutIdIsReplacedOnceTheServerIsReachable() {
        val legacy = stored.copy(id = null)
        assertFalse(QrTokenPolicy.isReusable(legacy, listOf(token("t1")), now))
        assertTrue(QrTokenPolicy.isReusable(legacy, null, now))
    }

    @Test
    fun revokeCandidateIsOldestActiveAppTokenOtherThanTheKeptOne() {
        val tokens = listOf(
            token("new", createdAt = "2026-11-20T00:00:00.123456Z"),
            token("old", createdAt = "2026-11-02T00:00:00Z"),
            token("oldest-revoked", active = false, createdAt = "2026-10-01T00:00:00Z"),
            token("web", label = "Wallet card", createdAt = "2026-09-01T00:00:00Z"),
        )
        assertEquals("old", QrTokenPolicy.revokeCandidate(tokens, keepId = null))
        assertEquals("new", QrTokenPolicy.revokeCandidate(tokens, keepId = "old"))
    }

    @Test
    fun tokensFromOtherClientsAreNeverRevoked() {
        val tokens = listOf(token("web", label = "Wallet card"), token("mine", active = false))
        assertNull(QrTokenPolicy.revokeCandidate(tokens, keepId = null))
    }

    @Test
    fun timestampsWithOffsetMicrosOrNoZoneParse() {
        assertEquals(now, QrTokenPolicy.parseInstantMs("2026-11-23T15:00:00+07:00"))
        assertEquals(now, QrTokenPolicy.parseInstantMs("2026-11-23T08:00:00.000000+00:00"))
        assertEquals(now, QrTokenPolicy.parseInstantMs("2026-11-23T08:00:00"))
        assertNull(QrTokenPolicy.parseInstantMs("not a date"))
        assertNull(QrTokenPolicy.parseInstantMs(null))
    }
}
