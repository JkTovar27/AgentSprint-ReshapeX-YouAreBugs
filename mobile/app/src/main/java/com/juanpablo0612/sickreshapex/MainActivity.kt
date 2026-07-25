package com.juanpablo0612.sickreshapex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.juanpablo0612.sickreshapex.ui.navigation.NavGraph
import com.juanpablo0612.sickreshapex.ui.theme.SickReshapeXTheme
import com.juanpablo0612.sickreshapex.ui.theme.ThemeController
import com.juanpablo0612.sickreshapex.ui.theme.ThemePreference
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeController = koinInject<ThemeController>()
            val preference by themeController.preference.collectAsState()
            val darkTheme = when (preference) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            SickReshapeXTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                Scaffold() {
                    NavGraph(
                        navController = navController,
                        modifier = Modifier.padding(it)
                    )
                }
            }
        }
    }
}
