package com.juanpablo0612.sickreshapex.ui.features.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.domain.model.RecentAnalysis
import com.juanpablo0612.sickreshapex.ui.components.AnimatedGradientBackdrop
import com.juanpablo0612.sickreshapex.ui.components.EmptyState
import com.juanpablo0612.sickreshapex.ui.components.PillTone
import com.juanpablo0612.sickreshapex.ui.components.ShimmerBox
import com.juanpablo0612.sickreshapex.ui.components.SickCard
import com.juanpablo0612.sickreshapex.ui.components.StaggeredAppearance
import com.juanpablo0612.sickreshapex.ui.components.StatusPill
import com.juanpablo0612.sickreshapex.ui.theme.Motion
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = koinViewModel(),
    onBack: () -> Unit,
    onOpenAnalysis: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedGradientBackdrop(modifier = Modifier.matchParentSize())

            when {
                uiState.isLoading -> HistoryLoadingSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )

                uiState.analyses.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No analyses yet",
                        description = "Run your first AI analysis from Home and it will show up here for quick reference.",
                        icon = Icons.Filled.History
                    )
                }

                else -> HistoryList(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    analyses = uiState.analyses,
                    favoriteIds = uiState.favoriteIds,
                    query = query,
                    onQueryChange = { query = it },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpenAnalysis = onOpenAnalysis
                )
            }
        }
    }
}

@Composable
private fun HistoryList(
    analyses: List<RecentAnalysis>,
    favoriteIds: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(analyses, query) {
        if (query.isBlank()) {
            analyses
        } else {
            analyses.filter { it.description.contains(query, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HistorySearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = "No analyses match \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            itemsIndexed(filtered) { index, analysis ->
                StaggeredAppearance(index = index) {
                    HistoryAnalysisRow(
                        analysis = analysis,
                        isFavorite = favoriteIds.contains(analysis.id),
                        onToggleFavorite = { onToggleFavorite(analysis.id) },
                        onClick = { onOpenAnalysis(analysis.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search past analyses") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun HistoryAnalysisRow(
    analysis: RecentAnalysis,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SickCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = analysis.sensorFamily ?: "Unclassified",
                    tone = if (analysis.sensorFamily != null) PillTone.INFO else PillTone.NEUTRAL,
                    icon = Icons.Filled.Sensors
                )
                FavoriteToggleButton(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = analysis.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = relativeTimeText(analysis.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Star toggle that pops with a quick overshoot scale + color swap whenever the
 * favorite state flips (but not on first composition, so lists don't "pop" on load).
 */
@Composable
private fun FavoriteToggleButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }
    var isInitial by remember { mutableStateOf(true) }

    LaunchedEffect(isFavorite) {
        if (isInitial) {
            isInitial = false
        } else {
            scale.animateTo(1.35f, animationSpec = tween(Motion.DURATION_INSTANT, easing = Motion.Emphasized))
            scale.animateTo(1f, animationSpec = Motion.bouncySpring())
        }
    }

    val tint by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.DURATION_FAST),
        label = "favoriteTint"
    )

    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onToggle() },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = tint,
            modifier = Modifier.scale(scale.value)
        )
    }
}

@Composable
private fun HistoryLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(6) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                shape = MaterialTheme.shapes.large
            )
        }
    }
}

/** Simple manual "time ago" formatting — no date/time dependency needed. */
private fun relativeTimeText(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diffMillis = (nowMillis - epochMillis).coerceAtLeast(0)
    val minutes = diffMillis / 60_000
    val hours = diffMillis / 3_600_000
    val days = diffMillis / 86_400_000

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        days < 365 -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}
