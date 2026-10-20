package com.motionloop.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.motionloop.app.feature.editor.EditorScreen
import com.motionloop.app.feature.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val ARG_PROJECT_ID = "projectId"
    const val EDITOR = "editor/{$ARG_PROJECT_ID}"
    fun editor(projectId: String) = "editor/$projectId"
}

@Composable
fun MotionLoopNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenProject = { id -> nav.navigate(Routes.editor(id)) })
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.StringType }),
        ) {
            EditorScreen(onBack = { nav.popBackStack() })
        }
    }
}
