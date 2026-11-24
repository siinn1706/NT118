package vn.uit.healthos.feature.safety.card

import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.feature.safety.SafetyGlassRoot
import com.kyant.shapes.RoundedRectangle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.BentoCard
import vn.uit.healthos.core.designsystem.component.PrimaryButton
import vn.uit.healthos.feature.safety.SafetyLocalState
import vn.uit.healthos.feature.safety.SafetyPreferences
import javax.inject.Inject

/** Lock-screen-accessible Emergency Health Card with QR code and one-tap ICE call. */
@AndroidEntryPoint
class EmergencyCardActivity : ComponentActivity() {

    @Inject
    lateinit var safetyPreferences: SafetyPreferences

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()

        setContent {
            SafetyGlassRoot(preferencesRepository, alert = false) {
                val local by safetyPreferences.state.collectAsStateWithLifecycle(initialValue = SafetyLocalState())
                EmergencyCardScreen(
                    local = local,
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun EmergencyCardScreen(
    local: SafetyLocalState,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val qrBitmap = remember(local.shareUrl) {
        local.shareUrl?.let { QrGenerator.generate(it, sizePx = 560) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Reads like a sheet: a glass close button at the top start, then the title.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassIconButton(onClick = onClose, contentDescription = stringResource(R.string.action_close)) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                }
                Icon(
                    Icons.Rounded.MedicalServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = stringResource(R.string.safety_card_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }

            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = local.cardFullName.ifBlank { stringResource(R.string.profile_default_user) },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (local.cardBloodType.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.profile_blood_type_badge, local.cardBloodType),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (local.cardAllergies.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.safety_card_allergies, local.cardAllergies),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (local.cardConditions.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.safety_card_conditions, local.cardConditions),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (qrBitmap != null) {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // A solid white plate keeps the scan contrast whatever the frost behind it.
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.safety_card_qr_cd),
                            modifier = Modifier
                                .clip(RoundedRectangle(16.dp))
                                .background(Color.White)
                                .padding(12.dp)
                                .size(220.dp),
                        )
                        Text(
                            text = stringResource(R.string.safety_card_scan_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (local.cardPrimaryContactPhone.isNotBlank()) {
                PrimaryButton(
                    text = stringResource(
                        R.string.safety_call_ice,
                        local.cardPrimaryContactName.ifBlank { local.cardPrimaryContactPhone },
                    ),
                    onClick = {
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${local.cardPrimaryContactPhone}"))
                        runCatching { context.startActivity(dial) }
                    },
                    leadingIcon = Icons.Rounded.Call,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
