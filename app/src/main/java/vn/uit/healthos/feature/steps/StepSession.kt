package vn.uit.healthos.feature.steps

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps step counting tied to the signed-in account. */
@Singleton
class StepSession @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: StepCounterRepository,
) {
    /** A different account signed in: start its count from zero (see [StepCounterRepository.resetForNewAccount]). */
    suspend fun onAccountChanged() = repository.resetForNewAccount()

    /** Nobody is signed in, so the phone stops counting until the next sign-in opens Home again. */
    fun onSignedOut() = StepSensorService.stop(context)
}
