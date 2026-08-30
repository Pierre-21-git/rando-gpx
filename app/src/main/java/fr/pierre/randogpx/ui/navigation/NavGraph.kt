package fr.pierre.randogpx.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import fr.pierre.randogpx.ui.about.AboutScreen
import fr.pierre.randogpx.ui.detail.DetailScreen
import fr.pierre.randogpx.ui.history.HistoryScreen
import fr.pierre.randogpx.ui.map.OfflineAreaScreen
import fr.pierre.randogpx.ui.massif.ManageMassifsScreen
import fr.pierre.randogpx.ui.recording.RecordingScreen

sealed class Screen(val route: String) {
    object History : Screen("history")
    object Recording : Screen("recording")
    object OfflineArea : Screen("offline_area")
    object ManageMassifs : Screen("massifs")
    object About : Screen("about")
    object Detail : Screen("detail/{hikeId}") {
        fun createRoute(hikeId: Long) = "detail/$hikeId"
    }
}

@Composable
fun RandoNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.History.route) {

        composable(Screen.History.route) {
            HistoryScreen(
                onStartNewHike = { navController.navigate(Screen.Recording.route) },
                onOpenHike = { hikeId -> navController.navigate(Screen.Detail.createRoute(hikeId)) },
                onOpenOfflineAreas = { navController.navigate(Screen.OfflineArea.route) },
                onManageMassifs = { navController.navigate(Screen.ManageMassifs.route) },
                onOpenAbout = { navController.navigate(Screen.About.route) }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.OfflineArea.route) {
            OfflineAreaScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.ManageMassifs.route) {
            ManageMassifsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Recording.route) {
            RecordingScreen(
                onFinished = { hikeId ->
                    navController.popBackStack()
                    navController.navigate(Screen.Detail.createRoute(hikeId))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("hikeId") { type = NavType.LongType })
        ) { backStackEntry ->
            val hikeId = backStackEntry.arguments?.getLong("hikeId") ?: return@composable
            DetailScreen(
                hikeId = hikeId,
                onBack = { navController.popBackStack() },
                onManageMassifs = { navController.navigate(Screen.ManageMassifs.route) }
            )
        }
    }
}
