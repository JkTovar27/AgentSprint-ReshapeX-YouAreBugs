package com.juanpablo0612.sickreshapex.ui.features.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.R
import com.juanpablo0612.sickreshapex.ui.components.PillTone
import com.juanpablo0612.sickreshapex.ui.components.SectionHeader
import com.juanpablo0612.sickreshapex.ui.components.SickCard
import com.juanpablo0612.sickreshapex.ui.components.StaggeredAppearance
import com.juanpablo0612.sickreshapex.ui.components.StatusPill
import com.juanpablo0612.sickreshapex.ui.theme.Motion
import com.juanpablo0612.sickreshapex.ui.theme.ThemePreference
import org.koin.androidx.compose.koinViewModel

private val PIPELINE_STAGES = listOf(
    R.string.agent_intake,
    R.string.agent_clarification,
    R.string.agent_retrieval,
    R.string.agent_evaluation,
    R.string.agent_confidence
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            StaggeredAppearance(index = 0) {
                AppearanceSection(
                    selected = uiState.theme,
                    onSelect = viewModel::setTheme
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            StaggeredAppearance(index = 1) {
                NotificationsSection(
                    enabled = uiState.notificationsEnabled,
                    onToggle = viewModel::setNotificationsEnabled
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            StaggeredAppearance(index = 2) {
                AboutSection()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppearanceSection(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit
) {
    Column {
        SectionHeader(title = stringResource(R.string.settings_appearance))
        SickCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_appearance_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeOption(
                        label = stringResource(R.string.theme_light),
                        icon = Icons.Default.LightMode,
                        selected = selected == ThemePreference.LIGHT,
                        onClick = { onSelect(ThemePreference.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOption(
                        label = stringResource(R.string.theme_dark),
                        icon = Icons.Default.DarkMode,
                        selected = selected == ThemePreference.DARK,
                        onClick = { onSelect(ThemePreference.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOption(
                        label = stringResource(R.string.theme_system),
                        icon = Icons.Default.SettingsSuggest,
                        selected = selected == ThemePreference.SYSTEM,
                        onClick = { onSelect(ThemePreference.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(Motion.DURATION_MEDIUM, easing = Motion.Standard),
        label = "themeOptionContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(Motion.DURATION_MEDIUM, easing = Motion.Standard),
        label = "themeOptionContent"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(Motion.DURATION_MEDIUM, easing = Motion.Standard),
        label = "themeOptionBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = Motion.snappySpring(),
        label = "themeOptionScale"
    )

    SickCard(
        modifier = modifier
            .scale(scale)
            .border(1.5.dp, borderColor, MaterialTheme.shapes.large),
        onClick = onClick,
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun NotificationsSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column {
        SectionHeader(title = stringResource(R.string.settings_notifications))
        SickCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_pipeline_alerts),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_pipeline_alerts_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Column {
        SectionHeader(title = stringResource(R.string.settings_about))
        SickCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.app_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_version, "1.0"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.settings_powered_by),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PIPELINE_STAGES.forEach { stageRes ->
                        StatusPill(text = stringResource(stageRes), tone = PillTone.INFO)
                    }
                }
            }
        }
    }
}
