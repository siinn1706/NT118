package vn.uit.healthos.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import vn.uit.healthos.core.data.session.AppLockManager
import vn.uit.healthos.navigation.MeRoute
import vn.uit.healthos.navigation.SettingsRoute

/** Emergency-contact editor, opened from the Me screen and from Safety. */
@Serializable
data object EmergencyContactsRoute

private const val CONTACTS_SAVED_KEY = "emergency_contacts_saved"

/** Runs [onSaved] once after the contact editor saved and popped back to this destination. */
@Composable
fun NavBackStackEntry.OnEmergencyContactsSaved(onSaved: () -> Unit) {
    val saved by savedStateHandle.getStateFlow(CONTACTS_SAVED_KEY, false).collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            savedStateHandle[CONTACTS_SAVED_KEY] = false
            onSaved()
        }
    }
}

fun NavGraphBuilder.profileGraph(
    navController: NavController,
    appLockManager: AppLockManager,
    onOpenSafety: () -> Unit,
    onOpenPpg: () -> Unit,
    onOpenDesignGallery: () -> Unit,
    onSignedOut: () -> Unit,
) {
    composable<MeRoute> { entry ->
        val viewModel: ProfileViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event is ProfileEvent.SignedOut) onSignedOut()
            }
        }
        entry.OnEmergencyContactsSaved(viewModel::refresh)

        MeScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onOpenSafety = onOpenSafety,
            onOpenPpg = onOpenPpg,
            onOpenSettings = { navController.navigate(SettingsRoute) },
            onEditContacts = { navController.navigate(EmergencyContactsRoute) },
            onOpenDesignGallery = onOpenDesignGallery,
            onSignOut = viewModel::signOut,
        )
    }

    composable<SettingsRoute> {
        val viewModel: ProfileViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val canUseBiometrics = remember(context) { appLockManager.canAuthenticate(context) }

        SettingsScreen(
            state = state,
            canUseBiometrics = canUseBiometrics,
            onThemeModeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
            onAppLockChange = viewModel::setAppLockEnabled,
            onBack = { navController.popBackStack() },
        )
    }

    composable<EmergencyContactsRoute> {
        val viewModel: EmergencyContactsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                if (event is EmergencyContactsEvent.Saved) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(CONTACTS_SAVED_KEY, true)
                    navController.popBackStack()
                }
            }
        }

        EmergencyContactsScreen(
            state = state,
            onContactChange = viewModel::updateContact,
            onAddContact = viewModel::addContact,
            onRemoveContact = viewModel::removeContact,
            onSave = viewModel::save,
            onRetryLoad = viewModel::load,
            onBack = { navController.popBackStack() },
        )
    }
}
