package vn.uit.healthos.core.data.outbox

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.multibindings.Multibinds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.uit.healthos.core.data.session.TokenStore

@HiltWorker
class OutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: OutboxDao,
    private val handlers: Map<String, @JvmSuppressWildcards OutboxHandler>,
    private val tokenStore: TokenStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val processor = OutboxProcessor(
            store = dao,
            handlers = handlers,
            sessionUserId = { tokenStore.current()?.userId },
            log = { Log.w(TAG, it) },
        )
        return when (processor.run()) {
            OutboxRunResult.SUCCESS -> Result.success()
            OutboxRunResult.RETRY -> Result.retry()
            // Held items wait for the next sign-in, which schedules a new run.
            OutboxRunResult.HELD -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "outbox-sync"
        private const val TAG = "OutboxWorker"
    }
}

/** Declares the handler map so it can be empty until features register their handlers. */
@Module
@InstallIn(SingletonComponent::class)
interface OutboxHandlersModule {
    @Multibinds
    fun outboxHandlers(): Map<String, OutboxHandler>
}
