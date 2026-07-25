package com.juanpablo0612.sickreshapex.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Processing : Screen("processing/{description}") {
        fun createRoute(description: String) = "processing/$description"
    }
    object Recommendation : Screen("recommendation/{analysisId}") {
        fun createRoute(analysisId: String) = "recommendation/$analysisId"
    }
    object TechnicalDetails : Screen("technical_details/{analysisId}") {
        fun createRoute(analysisId: String) = "technical_details/$analysisId"
    }
    object Alternatives : Screen("alternatives/{analysisId}") {
        fun createRoute(analysisId: String) = "alternatives/$analysisId"
    }
    object History : Screen("history")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
}
