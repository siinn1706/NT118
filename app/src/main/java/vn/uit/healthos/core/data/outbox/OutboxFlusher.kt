package vn.uit.healthos.core.data.outbox

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.uit.healthos.core.data.session.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends one type's pending items right away, in the caller's coroutine, for screens that need the server to have the
 * latest local write before they read (the leaderboard needs today's step total). Uses the same rules as the worker;
 * handlers are idempotent on the server, so a concurrent worker run cannot duplicate a write. Anything still pending
 * afterwards is left for [OutboxWorker].
 */
@Singleton
class OutboxFlusher @Inject constructor(
    private val dao: OutboxDao,
    private val handlers: Map<String, @JvmSuppressWildcards OutboxHandler>,
    private val tokenStore: TokenStore,
) {
    private val mutex = Mutex()

    suspend fun flush(type: String): OutboxRunResult = mutex.withLock {
        OutboxProcessor(
            store = TypedOutboxStore(dao, type),
            handlers = handlers,
            sessionUserId = { tokenStore.current()?.userId },
            log = { Log.w(TAG, it) },
        ).run()
    }

    private companion object {
        const val TAG = "OutboxFlusher"
    }
}
