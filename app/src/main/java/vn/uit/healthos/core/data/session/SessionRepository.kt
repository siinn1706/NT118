package vn.uit.healthos.core.data.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Emits when the server rejects the refresh token, so the UI can route back to sign-in. */
@Singleton
class SessionEvents @Inject constructor() {
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    fun notifyExpired() {
        _expired.tryEmit(Unit)
    }
}

@Singleton
class SessionRepository @Inject constructor(
    private val tokenStore: TokenStore,
    private val events: SessionEvents,
) {
    val isSignedIn: Flow<Boolean> = tokenStore.tokens.map { it != null }.distinctUntilChanged()
    val expired: SharedFlow<Unit> = events.expired

    suspend fun signIn(tokens: SessionTokens) = tokenStore.save(tokens)

    suspend fun signOut() = tokenStore.clear()
}
