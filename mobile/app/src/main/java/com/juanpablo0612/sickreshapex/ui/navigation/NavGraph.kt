package com.juanpablo0612.sickreshapex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.juanpablo0612.sickreshapex.ui.features.home.HomeScreen
import com.juanpablo0612.sickreshapex.ui.features.recommendation.RecommendationScreen

@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            com.juanpablo0612.sickreshapex.ui.features.splash.SplashScreen(
                onNext = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onStartAnalysis = { description ->
                    navController.navigate(Screen.Processing.createRoute(description))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                }
            )
        }

        composable(
            route = Screen.Processing.route,
            arguments = listOf(navArgument("description") { type = NavType.StringType })
        ) { backStackEntry ->
            val description = backStackEntry.arguments?.getString("description")
            RecommendationScreen(
                analysisId = null,
                initialDescription = description,
                onBack = { navController.popBackStack() },
                onViewTechnicalDetails = { id ->
                    navController.navigate(Screen.TechnicalDetails.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.Recommendation.route,
            arguments = listOf(navArgument("analysisId") { type = NavType.StringType })
        ) { backStackEntry ->
            val analysisId = backStackEntry.arguments?.getString("analysisId")
            RecommendationScreen(
                analysisId = analysisId,
                initialDescription = null,
                onBack = { navController.popBackStack() },
                onViewTechnicalDetails = { id ->
                    navController.navigate(Screen.TechnicalDetails.createRoute(id))
                }
            )
        }

        composable(Screen.History.route) {
            com.juanpablo0612.sickreshapex.ui.features.history.HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            // Placeholder for Settings
        }
    }
}
