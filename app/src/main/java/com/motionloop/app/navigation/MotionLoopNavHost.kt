package com.motionloop.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.motionloop.app.feature.contact.ContactScreen
import com.motionloop.app.feature.editor.EditorScreen
import com.motionloop.app.feature.help.HelpScreen
import com.motionloop.app.feature.home.HomeScreen
import com.motionloop.app.feature.pro.ProScreen
import com.motionloop.app.feature.settings.LicensesScreen
import com.motionloop.app.feature.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val ARG_PROJECT_ID = "projectId"
    const val EDITOR = "editor/{$ARG_PROJECT_ID}"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val HELP = "help"
    const val PRO = "pro"
    const val CONTACT = "contact"
    fun editor(projectId: String) = "editor/$projectId"
}

/**
 * Back that only works while the screen is resumed: a double tap during the exit animation
 * would otherwise pop the screen below too and leave a blank page.
 */
@Composable
private fun guardedBack(nav: NavHostController): () -> Unit = dropUnlessResumed { nav.popBackStack() }

@Composable
fun MotionLoopNavHost() {
    val nav = rememberNavController()
    // launchSingleTop: a double tap must not stack two copies of the same page.
    val openPro: () -> Unit = { nav.navigate(Routes.PRO) { launchSingleTop = true } }
    val openContact: () -> Unit = { nav.navigate(Routes.CONTACT) { launchSingleTop = true } }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenProject = { id -> nav.navigate(Routes.editor(id)) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenHelp = { nav.navigate(Routes.HELP) { launchSingleTop = true } },
                onOpenPro = openPro,
                onOpenContact = openContact,
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.StringType }),
        ) {
            EditorScreen(onBack = guardedBack(nav), onOpenPro = openPro)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = guardedBack(nav),
                onOpenPro = openPro,
                onOpenLicenses = { nav.navigate(Routes.LICENSES) { launchSingleTop = true } },
            )
        }
        composable(Routes.LICENSES) { LicensesScreen(onBack = guardedBack(nav)) }
        composable(Routes.HELP) { HelpScreen(onBack = guardedBack(nav), onContact = openContact) }
        composable(Routes.CONTACT) { ContactScreen(onBack = guardedBack(nav)) }
        composable(Routes.PRO) { ProScreen(onBack = guardedBack(nav)) }
    }
}
