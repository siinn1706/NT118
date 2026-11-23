package vn.uit.healthos.feature.safety

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * The QR share token kept on this device. The backend returns the plaintext share URL only once, at mint time,
 * so this copy is the only way to show the same QR again. [id] is null for a URL saved before ids were stored.
 */
data class StoredQrToken(
    val id: String?,
    val shareUrl: String,
    val expiresAtMs: Long?,
)

/**
 * Decides when the stored QR token can be shown again and which token to revoke when the backend's
 * active-token cap (`TOO_MANY_ACTIVE_TOKENS`) blocks a new mint.
 */
object QrTokenPolicy {
    /** Label given to every token minted by this app; only tokens with it are ever revoked automatically. */
    const val APP_TOKEN_LABEL = "HealthOS Android"

    /**
     * True when [stored] can be shown instead of minting. [serverTokens] is the owner's token list, or null when
     * the server could not be reached: offline, the stored token is trusted until its own expiry.
     */
    fun isReusable(stored: StoredQrToken?, serverTokens: List<EmergencyShareTokenDto>?, nowMs: Long): Boolean {
        if (stored == null || isExpired(stored.expiresAtMs, nowMs)) return false
        if (serverTokens == null) return true
        val server = serverTokens.firstOrNull { it.id == stored.id } ?: return false
        return server.isActive && !isExpired(parseInstantMs(server.expiresAt), nowMs)
    }

    /**
     * The oldest active token this app minted whose secret is lost (any token other than [keepId]), or null when
     * every active token belongs to another client and must not be touched.
     */
    fun revokeCandidate(serverTokens: List<EmergencyShareTokenDto>, keepId: String?): String? =
        serverTokens
            .filter { it.isActive && it.label == APP_TOKEN_LABEL && it.id != keepId }
            .minByOrNull { parseInstantMs(it.createdAt) ?: Long.MAX_VALUE }
            ?.id

    /** Epoch millis of an ISO-8601 timestamp; a value without an offset is read as UTC. */
    fun parseInstantMs(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatchingParse { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            ?: runCatchingParse { LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli() }
    }

    private inline fun runCatchingParse(parse: () -> Long): Long? = try {
        parse()
    } catch (e: DateTimeParseException) {
        null
    }

    private fun isExpired(expiresAtMs: Long?, nowMs: Long): Boolean = expiresAtMs != null && expiresAtMs <= nowMs
}
