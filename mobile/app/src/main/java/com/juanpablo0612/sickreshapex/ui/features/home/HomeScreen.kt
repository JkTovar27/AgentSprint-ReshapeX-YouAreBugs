package com.juanpablo0612.sickreshapex.ui.features.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.domain.model.QuickExample
import com.juanpablo0612.sickreshapex.domain.model.RecentAnalysis
import com.juanpablo0612.sickreshapex.ui.components.AnimatedGradientBackdrop
import com.juanpablo0612.sickreshapex.ui.components.EmptyState
import com.juanpablo0612.sickreshapex.ui.components.PillTone
import com.juanpablo0612.sickreshapex.ui.components.PrimaryActionButton
import com.juanpablo0612.sickreshapex.ui.components.RadarPulse
import com.juanpablo0612.sickreshapex.ui.components.SectionHeader
import com.juanpablo0612.sickreshapex.ui.components.ShimmerBox
import com.juanpablo0612.sickreshapex.ui.components.SickCard
import com.juanpablo0612.sickreshapex.ui.components.StaggeredAppearance
import com.juanpablo0612.sickreshapex.ui.components.StatusPill
import com.juanpablo0612.sickreshapex.ui.theme.Motion
import com.juanpablo0612.sickreshapex.ui.theme.ReadoutType
import com.juanpablo0612.sickreshapex.ui.theme.extendedColors
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onStartAnalysis: (String) -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var description by remember { mutableStateOf("") }
    // Bumped whenever a quick example is tapped, to retrigger the input highlight flash.
    var highlightTick by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            HomeHeader(
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            HomeLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    NewAnalysisSection(
                        description = description,
                        onDescriptionChange = { description = it },
                        highlightTick = highlightTick,
                        onStartAnalysis = { onStartAnalysis(description) }
                    )
                }

                item {
                    QuickExamplesSection(
                        examples = uiState.quickExamples,
                        onExampleSelected = { example ->
                            description = example.description
                            highlightTick++
                        }
                    )
                }

                item {
                    SectionHeader(title = "Recent Analyses") {
                        TextButton(onClick = onNavigateToHistory) {
                            Text("See all")
                        }
                    }
                }

                if (uiState.recentAnalyses.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No analyses yet",
                            description = "Run your first AI analysis above to see it here.",
                            icon = Icons.Default.History,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    itemsIndexed(
                        items = uiState.recentAnalyses,
                        key = { _, analysis -> analysis.id }
                    ) { index, analysis ->
                        StaggeredAppearance(index) {
                            RecentAnalysisCard(
                                analysis = analysis,
                                onClick = { onOpenAnalysis(analysis.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Branded header replacing the plain TopAppBar: logo mark, greeting/tagline, history + settings. */
@Composable
private fun HomeHeader(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedGradientBackdrop(
            modifier = Modifier.matchParentSize(),
            colors = listOf(
                MaterialTheme.extendedColors.scanCyan.copy(alpha = 0.10f),
                MaterialTheme.colorScheme.background
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    RadarPulse(
                        modifier = Modifier.size(26.dp),
                        color = MaterialTheme.colorScheme.primary,
                        ringCount = 2
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SICK Select Copilot",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "AI-guided sensor selection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/** "New analysis" input card — animates its border/elevation on focus and on quick-example fill. */
@Composable
private fun NewAnalysisSection(
    description: String,
    onDescriptionChange: (String) -> Unit,
    highlightTick: Int,
    onStartAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isHighlighted by remember { mutableStateOf(false) }

    LaunchedEffect(highlightTick) {
        if (highlightTick > 0) {
            isHighlighted = true
            delay(500)
            isHighlighted = false
        }
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary
            isHighlighted -> MaterialTheme.extendedColors.scanCyan
            else -> MaterialTheme.extendedColors.cardBorder
        },
        animationSpec = tween(Motion.DURATION_MEDIUM),
        label = "newAnalysisBorder"
    )
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 10.dp else 2.dp,
        animationSpec = tween(Motion.DURATION_MEDIUM),
        label = "newAnalysisElevation"
    )

    Column(modifier = modifier) {
        SectionHeader(title = "New Analysis")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = elevation, shape = MaterialTheme.shapes.large, clip = false)
                .border(width = 1.5.dp, color = borderColor, shape = MaterialTheme.shapes.large)
        ) {
            SickCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        label = { Text("Describe your industrial application") },
                        placeholder = { Text("e.g. Detect cardboard boxes on a fast conveyor belt") },
                        interactionSource = interactionSource,
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryActionButton(
                        text = "Start AI Analysis",
                        onClick = onStartAnalysis,
                        icon = Icons.Default.AutoAwesome,
                        enabled = description.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Icons cycled across quick-example cards; the model carries no icon field of its own. */
private val quickExampleIcons: List<ImageVector> = listOf(
    Icons.Default.PrecisionManufacturing,
    Icons.Default.WaterDrop,
    Icons.Default.Inventory2,
    Icons.Default.Sensors,
    Icons.Default.Straighten
)

@Composable
private fun QuickExamplesSection(
    examples: List<QuickExample>,
    onExampleSelected: (QuickExample) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(title = "Quick Examples")
        if (examples.isEmpty()) {
            EmptyState(
                title = "No examples yet",
                description = "Quick-start templates will appear here once available.",
                icon = Icons.Default.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(
                    items = examples,
                    key = { _, example -> example.title }
                ) { index, example ->
                    StaggeredAppearance(index) {
                        QuickExampleCard(
                            example = example,
                            icon = quickExampleIcons[index % quickExampleIcons.size],
                            onClick = { onExampleSelected(example) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickExampleCard(
    example: QuickExample,
    icon: ImageVector,
    onClick: () -> Unit
) {
    SickCard(
        onClick = onClick,
        modifier = Modifier.width(200.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = example.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = example.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentAnalysisCard(
    analysis: RecentAnalysis,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SickCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = analysis.sensorFamily ?: "Pending",
                    tone = if (analysis.sensorFamily != null) PillTone.SUCCESS else PillTone.NEUTRAL,
                    icon = Icons.Default.Sensors
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = relativeTimeFrom(analysis.timestamp),
                        style = ReadoutType.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = analysis.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Skeleton shaped like the real layout (header stays put; only the body swaps to shimmer). */
@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(modifier = Modifier.width(140.dp).height(20.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = MaterialTheme.shapes.large
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(modifier = Modifier.width(140.dp).height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    ShimmerBox(
                        modifier = Modifier.width(160.dp).height(110.dp),
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(modifier = Modifier.width(160.dp).height(20.dp))
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    shape = MaterialTheme.shapes.large
                )
            }
        }
    }
}

/** Simple relative-time readout ("Just now", "2h ago", "3d ago") from an epoch-millis timestamp. */
private fun relativeTimeFrom(timestampMillis: Long): String {
    val diffMillis = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0)
    val minutes = diffMillis / 60_000
    val hours = diffMillis / 3_600_000
    val days = diffMillis / 86_400_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
