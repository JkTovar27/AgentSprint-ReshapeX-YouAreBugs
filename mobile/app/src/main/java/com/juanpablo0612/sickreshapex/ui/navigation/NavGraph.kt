package com.juanpablo0612.sickreshapex.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.juanpablo0612.sickreshapex.R
import com.juanpablo0612.sickreshapex.ui.components.EmptyState
import com.juanpablo0612.sickreshapex.ui.features.history.HistoryScreen
import com.juanpablo0612.sickreshapex.ui.features.home.HomeScreen
import com.juanpablo0612.sickreshapex.ui.features.processing.ProcessingScreen
import com.juanpablo0612.sickreshapex.ui.features.recommendation.RecommendationScreen
import com.juanpablo0612.sickreshapex.ui.features.settings.SettingsScreen
import com.juanpablo0612.sickreshapex.ui.features.splash.SplashScreen

@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
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
                onOpenAnalysis = { analysisId ->
                    navController.navigate(Screen.Recommendation.createRoute(analysisId))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Processing.route,
            arguments = listOf(navArgument("description") { type = NavType.StringType })
        ) { backStackEntry ->
            val description = backStackEntry.arguments?.getString("description").orEmpty()
            ProcessingScreen(
                description = description,
                onComplete = { analysisId ->
                    navController.navigate(Screen.Recommendation.createRoute(analysisId)) {
                        popUpTo(Screen.Processing.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Recommendation.route,
            arguments = listOf(navArgument("analysisId") { type = NavType.StringType })
        ) { backStackEntry ->
            val analysisId = backStackEntry.arguments?.getString("analysisId").orEmpty()
            RecommendationScreen(
                analysisId = analysisId,
                onBack = { navController.popBackStack() },
                onViewTechnicalDetails = { id ->
                    navController.navigate(Screen.TechnicalDetails.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.TechnicalDetails.route,
            arguments = listOf(navArgument("analysisId") { type = NavType.StringType })
        ) {
            TechnicalDetailsPlaceholderScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenAnalysis = { analysisId ->
                    navController.navigate(Screen.Recommendation.createRoute(analysisId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Technical Details has no dedicated feature yet (out of scope for this redesign pass);
 * this keeps the button on the Recommendation screen from navigating into a dead end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechnicalDetailsPlaceholderScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.technical_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                title = stringResource(R.string.coming_soon_title),
                description = stringResource(R.string.technical_details_placeholder_body),
                icon = Icons.Filled.Construction
            )
        }
    }
}
