package vn.uit.healthos.core.network

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import vn.uit.healthos.BuildConfig
import vn.uit.healthos.core.data.session.SessionRepository
import vn.uit.healthos.feature.auth.AuthApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealtimeModule {

    /**
     * Connected while signed in and while any activity is started. The `/ws` endpoint sits at the server root next to
     * `v1/`, so it resolves against the same base URL (http becomes ws, https becomes wss).
     */
    @Provides
    @Singleton
    fun provideRealtimeSocket(
        @BareClient bare: OkHttpClient,
        json: Json,
        authApi: AuthApi,
        sessionRepository: SessionRepository,
    ): RealtimeSocket {
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
        return RealtimeSocket(
            client = bare.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .build(),
            url = BuildConfig.HEALTHOS_BASE_URL.toHttpUrl().resolve("ws")!!,
            json = json,
            ticketProvider = { apiCall(json) { authApi.wsTicket().data.wsTicket } },
            active = combine(sessionRepository.isSignedIn, foreground) { signedIn, visible -> signedIn && visible },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    private const val PING_INTERVAL_SECONDS = 25L
}
