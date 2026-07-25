package com.juanpablo0612.sickreshapex.ui.features.recommendation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.R
import com.juanpablo0612.sickreshapex.domain.model.AlternativeOption
import com.juanpablo0612.sickreshapex.domain.model.Analysis
import com.juanpablo0612.sickreshapex.domain.model.ApplicationRequirements
import com.juanpablo0612.sickreshapex.domain.model.Reason
import com.juanpablo0612.sickreshapex.domain.model.Recommendation
import com.juanpablo0612.sickreshapex.domain.model.SourceReference
import com.juanpablo0612.sickreshapex.ui.components.*
import com.juanpablo0612.sickreshapex.ui.theme.ReadoutType
import com.juanpablo0612.sickreshapex.ui.theme.extendedColors
import org.koin.androidx.compose.koinViewModel

/**
 * The payoff screen: shows the already-completed [Analysis] for [analysisId]. The "start a new
 * analysis" flow now lives entirely in the Processing feature, so this screen only ever loads
 * an existing, saved result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    viewModel: RecommendationViewModel = koinViewModel(),
    analysisId: String,
    onBack: () -> Unit,
    onViewTechnicalDetails: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(analysisId) {
        viewModel.loadAnalysis(analysisId)
    }

    val phase = when {
        uiState.isLoading -> ScreenPhase.LOADING
        uiState.errorRes != null -> ScreenPhase.ERROR
        uiState.analysis != null -> ScreenPhase.CONTENT
        else -> ScreenPhase.EMPTY
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recommendation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.analysis?.recommendation != null) {
                        IconButton(onClick = { viewModel.toggleFavorite(analysisId) }) {
                            Icon(
                                imageVector = if (uiState.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (uiState.isFavorite) {
                                    stringResource(R.string.favorites_remove)
                                } else {
                                    stringResource(R.string.favorites_add)
                                },
                                tint = if (uiState.isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Crossfade(
            targetState = phase,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "recommendationPhase"
        ) { currentPhase ->
            when (currentPhase) {
                ScreenPhase.LOADING -> LoadingSkeleton(modifier = Modifier.fillMaxSize())

                ScreenPhase.ERROR -> ErrorSection(
                    message = stringResource(uiState.errorRes ?: R.string.error_loading_analysis),
                    onRetry = { viewModel.loadAnalysis(analysisId) },
                    modifier = Modifier.fillMaxSize()
                )

                ScreenPhase.CONTENT -> ResultContent(
                    analysis = uiState.analysis!!,
                    onViewTechnicalDetails = { onViewTechnicalDetails(analysisId) },
                    modifier = Modifier.fillMaxSize()
                )

                ScreenPhase.EMPTY -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.recommendation_not_found_title),
                        description = stringResource(R.string.recommendation_not_found_body),
                        icon = Icons.Filled.SearchOff,
                        action = { SecondaryActionButton(text = stringResource(R.string.action_go_back), onClick = onBack) }
                    )
                }
            }
        }
    }
}

private enum class ScreenPhase { LOADING, ERROR, CONTENT, EMPTY }

/** Icons cycled through for reason cards — the domain model has no icon of its own. */
private val ReasonIcons = listOf(
    Icons.Filled.Bolt,
    Icons.Filled.Shield,
    Icons.Filled.Speed,
    Icons.Filled.Visibility,
    Icons.Filled.Verified
)

@Composable
private fun ResultContent(
    analysis: Analysis,
    onViewTechnicalDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recommendation = analysis.recommendation

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        if (recommendation == null) {
            EmptyState(
                title = stringResource(R.string.recommendation_none_title),
                description = stringResource(R.string.recommendation_none_body),
                icon = Icons.Filled.Info,
                modifier = Modifier.fillMaxWidth()
            )
            return@Column
        }

        StaggeredAppearance(index = 0) { HeroSection(recommendation) }
        StaggeredAppearance(index = 1) { RequirementsSection(recommendation.requirements) }

        if (recommendation.reasons.isNotEmpty()) {
            StaggeredAppearance(index = 2) { ReasonsSection(recommendation.reasons) }
        }
        if (recommendation.sources.isNotEmpty()) {
            StaggeredAppearance(index = 3) { SourcesSection(recommendation.sources) }
        }
        if (analysis.alternatives.isNotEmpty()) {
            StaggeredAppearance(index = 4) { AlternativesSection(analysis.alternatives) }
        }

        StaggeredAppearance(index = 5) {
            PrimaryActionButton(
                text = stringResource(R.string.recommendation_view_technical_details),
                onClick = onViewTechnicalDetails,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Insights
            )
        }
    }
}

/** Headline sensor family, confidence ring and executive summary — the screen's payoff moment. */
@Composable
private fun HeroSection(recommendation: Recommendation, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.extendedColors.cardBorder, MaterialTheme.shapes.large)
    ) {
        AnimatedGradientBackdrop(
            modifier = Modifier.matchParentSize(),
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                MaterialTheme.colorScheme.surfaceContainer
            )
        )
        Column(modifier = Modifier.padding(24.dp)) {
            StatusPill(text = stringResource(R.string.recommendation_ai_overline), tone = PillTone.INFO, icon = Icons.Filled.AutoAwesome)
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.recommendation_family_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = recommendation.sensorFamily,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                ConfidenceRing(progress = recommendation.confidenceLevel, label = stringResource(R.string.confidence_label))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = recommendation.executiveSummary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (recommendation.confidenceExplanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = recommendation.confidenceExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Application requirements as clean spec rows; distance reads like an instrument readout. */
@Composable
private fun RequirementsSection(requirements: ApplicationRequirements, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.requirements_title))
        SickCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                RequirementRow(label = stringResource(R.string.requirement_object_type), value = requirements.tipo_objeto)
                HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.requirement_distance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.distance_mm_format, requirements.distancia_mm),
                        style = ReadoutType.medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
                RequirementRow(label = stringResource(R.string.requirement_environment), value = requirements.ambiente)
                HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
                RequirementRow(label = stringResource(R.string.requirement_surface), value = requirements.material_superficie)
                HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
                RequirementRow(label = stringResource(R.string.requirement_mounting), value = requirements.montaje)
            }
        }
    }
}

/** "Why this sensor" — a staggered list of small reason cards. */
@Composable
private fun ReasonsSection(reasons: List<Reason>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.recommendation_why_title))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            reasons.forEachIndexed { index, reason ->
                StaggeredAppearance(index = index) {
                    ReasonCard(reason = reason, icon = ReasonIcons[index % ReasonIcons.size])
                }
            }
        }
    }
}

/** Tappable source list — opens the URL via the platform's URI handler. */
@Composable
private fun SourcesSection(sources: List<SourceReference>, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.sources_title))
        SickCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                sources.forEachIndexed { index, source ->
                    SourceLinkRow(
                        source = source,
                        onClick = { runCatching { uriHandler.openUri(source.url) } }
                    )
                    if (index != sources.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
                    }
                }
            }
        }
    }
}

/** Collapsible "other options considered" section — collapsed by default to keep focus on the pick. */
@Composable
private fun AlternativesSection(alternatives: List<AlternativeOption>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "alternativesChevron"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.alternatives_title_count, alternatives.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(R.string.action_collapse)
                } else {
                    stringResource(R.string.action_expand)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation)
            )
        }
        ExpandableAppearance(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                alternatives.forEach { alternative ->
                    AlternativeCard(alternative = alternative)
                }
            }
        }
    }
}

/** Shimmer skeleton shaped like the real layout: hero block, spec rows, reason cards. */
@Composable
private fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = MaterialTheme.shapes.large
        )
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .height(20.dp)
        )
        repeat(4) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            )
        }
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.36f)
                .height(20.dp)
        )
        repeat(2) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}

/** Clear error card with a retry action. */
@Composable
private fun ErrorSection(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.error_generic_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            SecondaryActionButton(text = stringResource(R.string.action_try_again), onClick = onRetry, icon = Icons.Filled.Refresh)
        }
    }
}
