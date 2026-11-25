package vn.uit.healthos.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import vn.uit.healthos.R

@Serializable data object AuthRoute
@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data object MealsRoute

/** [prompt] prefills the chat composer (for example from the Ask AI widget); it is never sent automatically. */
@Serializable data class ChatRoute(val prompt: String? = null)

@Serializable data object MedsRoute
@Serializable data object MeRoute
@Serializable data object SafetyRoute
@Serializable data object PpgRoute
@Serializable data object SettingsRoute
@Serializable data object DesignGalleryRoute
@Serializable data object FriendsRoute
@Serializable data object LeaderboardRoute

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(HomeRoute, R.string.nav_home, Icons.Outlined.SpaceDashboard, Icons.Rounded.SpaceDashboard),
    MEALS(MealsRoute, R.string.nav_meals, Icons.Outlined.Restaurant, Icons.Rounded.Restaurant),
    CHAT(ChatRoute(), R.string.nav_chat, Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Rounded.Chat),
    MEDS(MedsRoute, R.string.nav_meds, Icons.Outlined.Medication, Icons.Rounded.Medication),
    ME(MeRoute, R.string.nav_me, Icons.Outlined.Person, Icons.Rounded.Person),
}
