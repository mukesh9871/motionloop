package com.motionloop.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.motionloop.app.ui.components.MlMotion
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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

private const val ENTER_MS = 220
private const val EXIT_MS = 160

// Secondary pages come in from the side a little and leave the same way on back, following the
// finger during the back gesture. Coming back to them from a page above uses the plain fade.
private val pageEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(MlMotion.gentle()) { it / 6 } + fadeIn(tween(ENTER_MS))
}
private val pageExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(EXIT_MS)) { it / 6 } + fadeOut(tween(EXIT_MS))
}

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
    // Quick fades instead of the 700 ms default, so moving between screens feels immediate.
    // The editor only fades: its GL preview is a SurfaceView, which does not follow scaling or sliding.
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(ENTER_MS)) },
        exitTransition = { fadeOut(tween(EXIT_MS)) },
        popEnterTransition = { fadeIn(tween(ENTER_MS)) },
        popExitTransition = { fadeOut(tween(EXIT_MS)) },
    ) {
        composable(Routes.HOME) {
            // Only while resumed: two quick taps on different projects would stack two editors.
            // dropUnlessResumed takes no-argument blocks, so the project id case is guarded by hand.
            val owner = LocalLifecycleOwner.current
            HomeScreen(
                onOpenProject = { id ->
                    if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        nav.navigate(Routes.editor(id)) { launchSingleTop = true }
                    }
                },
                onOpenSettings = dropUnlessResumed { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenHelp = dropUnlessResumed { nav.navigate(Routes.HELP) { launchSingleTop = true } },
                onOpenPro = dropUnlessResumed { openPro() },
                onOpenContact = dropUnlessResumed { openContact() },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.StringType }),
        ) {
            EditorScreen(onBack = guardedBack(nav), onOpenPro = openPro)
        }
        composable(Routes.SETTINGS, enterTransition = pageEnter, popEnterTransition = null, popExitTransition = pageExit) {
            SettingsScreen(
                onBack = guardedBack(nav),
                onOpenPro = openPro,
                onOpenLicenses = { nav.navigate(Routes.LICENSES) { launchSingleTop = true } },
            )
        }
        composable(Routes.LICENSES, enterTransition = pageEnter, popEnterTransition = null, popExitTransition = pageExit) { LicensesScreen(onBack = guardedBack(nav)) }
        composable(Routes.HELP, enterTransition = pageEnter, popEnterTransition = null, popExitTransition = pageExit) { HelpScreen(onBack = guardedBack(nav), onContact = openContact) }
        composable(Routes.CONTACT, enterTransition = pageEnter, popEnterTransition = null, popExitTransition = pageExit) { ContactScreen(onBack = guardedBack(nav)) }
        composable(Routes.PRO, enterTransition = pageEnter, popEnterTransition = null, popExitTransition = pageExit) { ProScreen(onBack = guardedBack(nav)) }
    }
}
