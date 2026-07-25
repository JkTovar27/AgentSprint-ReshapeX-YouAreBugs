package com.juanpablo0612.sickreshapex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.juanpablo0612.sickreshapex.data.repository.MockAnalysisRepository
import com.juanpablo0612.sickreshapex.ui.navigation.NavGraph
import com.juanpablo0612.sickreshapex.ui.theme.SickReshapeXTheme

class MainActivity : ComponentActivity() {
    // In a real app, this would be injected via Hilt or another DI framework
    private val analysisRepository = MockAnalysisRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SickReshapeXTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    analysisRepository = analysisRepository
                )
            }
        }
    }
}
