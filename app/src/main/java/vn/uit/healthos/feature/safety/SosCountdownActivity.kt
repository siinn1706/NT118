package vn.uit.healthos.feature.safety

import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.designsystem.component.AdaptiveColumns
import vn.uit.healthos.core.designsystem.component.GlassSpinner
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import com.kyant.shapes.Capsule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.core.designsystem.component.SecondaryButton
import vn.uit.healthos.core.designsystem.theme.tabular
import javax.inject.Inject

/**
 * Lock-screen-visible SOS screen. It only observes [SosController] state and offers Cancel / Send now;
 * recreating it (rotation, theme change, process restart) never restarts the countdown or re-sends anything.
 */
@AndroidEntryPoint
class SosCountdownActivity : ComponentActivity() {

    @Inject
    lateinit var sosController: SosController

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()

        if (savedInstanceState == null) handleIntent(intent) else sosController.restore()

        setContent {
            val event by sosController.event.collectAsStateWithLifecycle()
            // The aurora turns red while the SOS is live and back to the user's own once it is done.
            SafetyGlassRoot(preferencesRepository, alert = event?.phase != SosPhase.DONE) {
                SosCountdownContent(
                    event = event,
                    onCancel = sosController::cancel,
                    onSendNow = sosController::sendNow,
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Arms a manual SOS when asked to (never when relaunched from Recents), then hosts any active SOS. */
    private fun handleIntent(intent: Intent) {
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        val event = if (intent.getBooleanExtra(EXTRA_ARM_MANUAL, false) && !fromHistory) {
            intent.removeExtra(EXTRA_ARM_MANUAL)
            sosController.arm(SosTrigger.MANUAL)
        } else {
            sosController.restore()
        }
        if (event?.isActive == true) SosAlertService.host(this)
    }

    companion object {
        private const val EXTRA_ARM_MANUAL = "extra_sos_arm_manual"

        /** Opens the screen and arms a manual SOS (5 s countdown). */
        fun manualSosIntent(context: Context): Intent =
            Intent(context, SosCountdownActivity::class.java)
                .putExtra(EXTRA_ARM_MANUAL, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens the screen on the current SOS without arming a new one. */
        fun openIntent(context: Context): Intent =
            Intent(context, SosCountdownActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun SosCountdownContent(
    event: SosEvent?,
    onCancel: (String) -> Unit,
    onSendNow: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (event == null || event.phase == SosPhase.CANCELLED) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val view = LocalView.current
    DisposableEffect(event.isActive) {
        view.keepScreenOn = event.isActive
        onDispose { view.keepScreenOn = false }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(event.id, event.phase) {
        while (event.phase == SosPhase.COUNTDOWN) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            when (event.phase) {
                SosPhase.COUNTDOWN -> CountdownBody(
                    event = event,
                    remainingSeconds = SosEvents.remainingSeconds(event, now),
                    onCancel = { onCancel(event.id) },
                    onSendNow = { onSendNow(event.id) },
                )

                SosPhase.DISPATCHING -> {
                    val dispatching = stringResource(R.string.safety_sos_dispatching)
                    Box(
                        modifier = Modifier
                            .liquidGlass(Capsule(), GlassStyle.Chrome)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlassSpinner(size = 32.dp, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        text = dispatching,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    DispatchDetails(event)
                }

                SosPhase.DONE, SosPhase.CANCELLED -> {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.safety_sos_result_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.safety_sos_sms_summary, event.smsSentCount, event.contacts.size),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    DispatchDetails(event)
                    if (!event.contactsRefreshed) {
                        Text(
                            text = stringResource(R.string.safety_sos_contacts_not_refreshed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.action_close),
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownBody(
    event: SosEvent,
    remainingSeconds: Int,
    onCancel: () -> Unit,
    onSendNow: () -> Unit,
) {
    Icon(
        Icons.Rounded.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
    )
    Text(
        text = if (event.trigger == SosTrigger.FALL) {
            stringResource(R.string.safety_fall_countdown_title)
        } else {
            stringResource(R.string.safety_manual_countdown_title)
        },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "$remainingSeconds",
        style = MaterialTheme.typography.displayLarge.tabular(),
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        text = stringResource(R.string.safety_countdown_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    // The cancel action is the biggest and most neutral control on the screen, so a panicked tap finds it first.
    PrimaryButton(
        text = stringResource(R.string.safety_im_ok_cancel),
        onClick = onCancel,
        tint = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    )
    SecondaryButton(
        text = stringResource(R.string.safety_sos_action_send_now),
        onClick = onSendNow,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DispatchDetails(event: SosEvent) {
    if (event.locationResolved) {
        val location = event.location
        Text(
            text = if (location != null) {
                stringResource(R.string.safety_sos_location_line, location.mapsUrl())
            } else {
                stringResource(R.string.safety_sos_no_location)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    if (event.contacts.isEmpty()) {
        if (event.phase == SosPhase.DONE) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.safety_sos_no_contacts),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }
    event.contacts.forEach { contact ->
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Text(contact.name, style = MaterialTheme.typography.titleSmall)
            Text(contact.phone, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            // Both delivery statuses on one line while they fit, otherwise one under the other.
            AdaptiveColumns(modifier = Modifier.fillMaxWidth(), maxColumns = 2, horizontalSpacing = 12.dp, verticalSpacing = 2.dp) {
                Text(
                    text = stringResource(R.string.safety_sos_sms_label, smsStatusLabel(contact.sms.status)),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.safety_sos_email_label, emailStatusLabel(contact, event)),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun smsStatusLabel(status: SmsStatus): String = when (status) {
    SmsStatus.SENDING -> stringResource(R.string.safety_status_sending)
    SmsStatus.SENT -> stringResource(R.string.safety_status_sent)
    SmsStatus.DELIVERED -> stringResource(R.string.safety_status_delivered)
    SmsStatus.NO_PERMISSION -> stringResource(R.string.safety_status_no_perm)
    SmsStatus.FAILED -> stringResource(R.string.safety_status_failed)
}

@Composable
private fun emailStatusLabel(contact: SosContactRow, event: SosEvent): String = when {
    contact.email.isNullOrBlank() -> stringResource(R.string.safety_status_skipped)
    event.emailQueued -> stringResource(R.string.safety_status_email_queued)
    else -> stringResource(R.string.safety_status_email_pending)
}
