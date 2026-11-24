package vn.uit.healthos.feature.steps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import vn.uit.healthos.core.data.session.TokenStore
import javax.inject.Inject

/** Resumes step counting after a reboot or an app update, for a signed-in user who granted the permission. */
@AndroidEntryPoint
class StepBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var tokenStore: TokenStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val signedIn = tokenStore.tokens.first() != null
                if (StepServicePolicy.shouldRun(StepServicePolicy.hasActivityRecognition(context), signedIn)) {
                    StepSensorService.start(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not resume step counting", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "StepBootReceiver"
    }
}
