package com.juanpablo0612.sickreshapex.ui.features.processing

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.R
import com.juanpablo0612.sickreshapex.domain.model.*
import com.juanpablo0612.sickreshapex.ui.components.*
import com.juanpablo0612.sickreshapex.ui.theme.Motion
import com.juanpablo0612.sickreshapex.ui.theme.ReadoutType
import com.juanpablo0612.sickreshapex.ui.theme.extendedColors
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * Centerpiece "live pipeline" screen: visualizes the Planner -> Retriever -> Validator ->
 * Evaluator -> Responder backend agents running in real time. Each stage lights up as it
 * starts, reveals a compact summary card as it completes, and the whole thing resolves into
 * a short celebratory beat before handing off to the full recommendation screen.
 */
@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel = koinViewModel(),
    description: String,
    onComplete: (analysisId: String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCompletionBeat by remember { mutableStateOf(false) }

    LaunchedEffect(description) {
        viewModel.start(description)
    }

    LaunchedEffect(uiState.analysisId) {
        val id = uiState.analysisId
        if (id != null) {
            showCompletionBeat = true
            delay(900)
            onComplete(id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedGradientBackdrop(
            modifier = Modifier.fillMaxSize(),
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surface,
                MaterialTheme.extendedColors.scanCyan.copy(alpha = 0.10f)
            )
        )

        RadarPulse(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 110.dp, y = (-110).dp)
                .alpha(0.16f),
            color = MaterialTheme.extendedColors.scanCyan
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ProcessingHeader(description = description, onBack = onBack)

            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LiveStatusRow(state = uiState)
                    Spacer(modifier = Modifier.height(10.dp))
                    OverallProgressTrack(fraction = uiState.overallProgress)
                    Spacer(modifier = Modifier.height(28.dp))

                    PipelineStepper(state = uiState)

                    if (uiState.hasFailed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PipelineErrorCard(
                            failedStage = uiState.failedStage,
                            reason = uiState.error
                                ?: uiState.errorRes?.let { stringResource(it) }.orEmpty(),
                            onBack = onBack
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showCompletionBeat,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(tween(Motion.DURATION_MEDIUM)),
                    exit = fadeOut(tween(Motion.DURATION_FAST))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CompletionBeatCard(responderOutput = uiState.responderOutput)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingHeader(
    description: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.processing_analyzing_application),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LiveStatusRow(state: ProcessingState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!state.hasFailed) {
            PulsingDot(color = MaterialTheme.extendedColors.scanCyan)
            Spacer(modifier = Modifier.width(8.dp))
            val step = (state.completedCount + 1).coerceAtMost(5)
            Text(
                text = stringResource(R.string.processing_live_step, step),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extendedColors.scanCyan
            )
        } else {
            StatusPill(
                text = stringResource(R.string.processing_pipeline_stopped),
                tone = PillTone.ERROR,
                icon = Icons.Filled.Warning
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.processing_completed_ratio, state.completedCount),
            style = ReadoutType.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverallProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(Motion.DURATION_SLOW, easing = Motion.Emphasized),
        label = "overallProgress"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillBrush = MaterialTheme.extendedColors.scanGradient
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = corner)
        if (animatedFraction > 0f) {
            drawRoundRect(
                brush = fillBrush,
                size = size.copy(width = size.width * animatedFraction),
                cornerRadius = corner
            )
        }
    }
}

@Composable
private fun PipelineErrorCard(
    failedStage: AgentStage?,
    reason: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = failedStage?.let { stringResource(R.string.processing_stage_failed, stageLabel(it)) }
                        ?: stringResource(R.string.processing_pipeline_failed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            SecondaryActionButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// --- Pipeline stepper -------------------------------------------------------------------

private val PIPELINE_STAGES = listOf(
    AgentStage.PLANNER,
    AgentStage.RETRIEVER,
    AgentStage.VALIDATOR,
    AgentStage.EVALUATOR,
    AgentStage.RESPONDER
)

@Composable
private fun stageLabel(stage: AgentStage): String = stringResource(
    when (stage) {
        AgentStage.PLANNER -> R.string.agent_planner
        AgentStage.RETRIEVER -> R.string.agent_retriever
        AgentStage.VALIDATOR -> R.string.agent_validator
        AgentStage.EVALUATOR -> R.string.agent_evaluator
        AgentStage.RESPONDER -> R.string.agent_responder
    }
)

@Composable
private fun stageDescription(stage: AgentStage): String = stringResource(
    when (stage) {
        AgentStage.PLANNER -> R.string.agent_planner_description
        AgentStage.RETRIEVER -> R.string.agent_retriever_description
        AgentStage.VALIDATOR -> R.string.agent_validator_description
        AgentStage.EVALUATOR -> R.string.agent_evaluator_description
        AgentStage.RESPONDER -> R.string.agent_responder_description
    }
)

private fun stageIcon(stage: AgentStage): ImageVector = when (stage) {
    AgentStage.PLANNER -> Icons.Filled.Psychology
    AgentStage.RETRIEVER -> Icons.Filled.TravelExplore
    AgentStage.VALIDATOR -> Icons.AutoMirrored.Filled.FactCheck
    AgentStage.EVALUATOR -> Icons.Filled.Insights
    AgentStage.RESPONDER -> Icons.AutoMirrored.Filled.Send
}

@Composable
private fun PipelineStepper(state: ProcessingState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        PIPELINE_STAGES.forEachIndexed { index, stage ->
            StageRow(
                stage = stage,
                state = state,
                isLast = index == PIPELINE_STAGES.lastIndex
            )
        }
    }
}

@Composable
private fun StageRow(
    stage: AgentStage,
    state: ProcessingState,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val status = state.stageStatuses[stage] ?: StageStatus.PENDING
    Row(modifier = modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StageIndicator(status = status, icon = stageIcon(stage))
            if (!isLast) {
                StageConnector(filled = status == StageStatus.DONE)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 4.dp else 22.dp)
        ) {
            StageHeaderRow(stage = stage, status = status)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stageDescription(stage),
                style = MaterialTheme.typography.bodySmall,
                color = if (status == StageStatus.PENDING) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            ExpandableAppearance(visible = status == StageStatus.DONE) {
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    StageDetailCard(stage = stage, state = state)
                }
            }
        }
    }
}

@Composable
private fun StageHeaderRow(stage: AgentStage, status: StageStatus, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stageLabel(stage),
            style = MaterialTheme.typography.titleMedium,
            color = when (status) {
                StageStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                StageStatus.ERROR -> MaterialTheme.colorScheme.error
                StageStatus.ACTIVE, StageStatus.DONE -> MaterialTheme.colorScheme.onBackground
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        when (status) {
            StageStatus.ACTIVE -> PulsingDot(color = MaterialTheme.colorScheme.primary)
            StageStatus.DONE -> StatusPill(
                text = stringResource(R.string.stage_done),
                tone = PillTone.SUCCESS,
                icon = Icons.Filled.Check
            )
            StageStatus.ERROR -> StatusPill(
                text = stringResource(R.string.stage_failed),
                tone = PillTone.ERROR,
                icon = Icons.Filled.ErrorOutline
            )
            StageStatus.PENDING -> Unit
        }
    }
}

@Composable
private fun StageIndicator(status: StageStatus, icon: ImageVector, modifier: Modifier = Modifier) {
    val extended = MaterialTheme.extendedColors
    val backgroundColor = when (status) {
        StageStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerHigh
        StageStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        StageStatus.DONE -> extended.success
        StageStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    val iconColor = when (status) {
        StageStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        StageStatus.ACTIVE -> MaterialTheme.colorScheme.onPrimary
        StageStatus.DONE -> extended.onSuccess
        StageStatus.ERROR -> MaterialTheme.colorScheme.onError
    }
    val scale by animateFloatAsState(
        targetValue = if (status == StageStatus.ACTIVE) 1.1f else 1f,
        animationSpec = Motion.bouncySpring(),
        label = "stageIndicatorScale"
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (status == StageStatus.ACTIVE) {
                    Modifier.border(2.dp, extended.scanCyan, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            StageStatus.DONE -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            StageStatus.ERROR -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            else -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StageConnector(filled: Boolean, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = tween(Motion.DURATION_SLOW, easing = Motion.Emphasized),
        label = "connectorFraction"
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val fillBrush = MaterialTheme.extendedColors.scanGradient
    Canvas(
        modifier = modifier
            .width(4.dp)
            .height(40.dp)
    ) {
        drawLine(
            color = trackColor,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Round
        )
        if (fraction > 0f) {
            drawLine(
                brush = fillBrush,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height * fraction),
                strokeWidth = size.width,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun StageDetailCard(stage: AgentStage, state: ProcessingState, modifier: Modifier = Modifier) {
    when (stage) {
        AgentStage.PLANNER -> state.plannerOutput?.let { PlannerStageCard(it, modifier) }
        AgentStage.RETRIEVER -> state.retrieverOutput?.let { RetrieverStageCard(it, modifier) }
        AgentStage.VALIDATOR -> state.validatorOutput?.let { ValidatorStageCard(it, modifier) }
        AgentStage.EVALUATOR -> state.evaluatorOutput?.let { EvaluatorStageCard(it, modifier) }
        AgentStage.RESPONDER -> state.responderOutput?.let { ResponderStageCard(it, modifier) }
    }
}

// --- Per-stage compact summary cards ----------------------------------------------------

@Composable
private fun ChipsRow(items: List<String>, tone: PillTone, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { label ->
            StatusPill(text = label, tone = tone)
        }
    }
}

@Composable
private fun PlannerStageCard(output: PlannerOutput, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.processing_requirements_extracted),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.percent_format, (output.extractionConfidence * 100).toInt()),
                    style = ReadoutType.small,
                    color = MaterialTheme.extendedColors.scanCyan
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ChipsRow(
                items = output.structuredRequirements.map { (key, value) ->
                    stringResource(R.string.key_value_format, key, value)
                },
                tone = PillTone.INFO
            )
            if (output.plannerNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = output.plannerNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RetrieverStageCard(output: RetrieverOutput, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(
                        R.plurals.candidates_found,
                        output.candidates.size,
                        output.candidates.size
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.sources_count,
                        output.sources.size,
                        output.sources.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ChipsRow(items = output.candidates.map { it.productName }, tone = PillTone.NEUTRAL)
            if (output.retrievalNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = output.retrievalNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ValidatorStageCard(output: ValidatorOutput, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = pluralStringResource(
                        R.plurals.viable_count,
                        output.viableCandidates.size,
                        output.viableCandidates.size
                    ),
                    tone = PillTone.SUCCESS,
                    icon = Icons.Filled.CheckCircle
                )
                StatusPill(
                    text = pluralStringResource(
                        R.plurals.discarded_count,
                        output.discardedCandidates.size,
                        output.discardedCandidates.size
                    ),
                    tone = PillTone.ERROR,
                    icon = Icons.Filled.Close
                )
            }
            if (output.validatorNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = output.validatorNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EvaluatorStageCard(output: EvaluatorOutput, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConfidenceRing(progress = output.confidence, label = stringResource(R.string.confidence_label))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val (tone, label) = when (output.nextStep) {
                    NextStep.RESPOND -> PillTone.SUCCESS to stringResource(R.string.decision_respond)
                    NextStep.CLARIFY -> PillTone.WARNING to stringResource(R.string.decision_clarify)
                    NextStep.CONTINUE -> PillTone.INFO to stringResource(R.string.decision_continue)
                    NextStep.ESCALATE -> PillTone.ERROR to stringResource(R.string.decision_escalate)
                }
                StatusPill(text = stringResource(R.string.processing_next_action, label), tone = tone)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = output.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ResponderStageCard(output: ResponderOutput, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.success,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = output.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusPill(
                    text = stringResource(R.string.confidence_percent, (output.confidence * 100).toInt()),
                    tone = PillTone.SUCCESS
                )
            }
        }
    }
}

// --- Completion beat ---------------------------------------------------------------------

@Composable
private fun CompletionBeatCard(responderOutput: ResponderOutput?, modifier: Modifier = Modifier) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.82f,
        animationSpec = Motion.bouncySpring(),
        label = "completionScale"
    )
    SickCard(
        modifier = modifier
            .padding(32.dp)
            .widthIn(max = 320.dp)
            .scale(scale)
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                RadarPulse(
                    modifier = Modifier.size(96.dp),
                    color = MaterialTheme.extendedColors.success,
                    ringCount = 2
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.extendedColors.success),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.onSuccess,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.processing_recommendation_ready),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = responderOutput?.message ?: stringResource(R.string.processing_analysis_complete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (responderOutput != null) {
                Spacer(modifier = Modifier.height(12.dp))
                StatusPill(
                    text = stringResource(
                        R.string.confidence_percent,
                        (responderOutput.confidence * 100).toInt()
                    ),
                    tone = PillTone.SUCCESS
                )
            }
        }
    }
}
