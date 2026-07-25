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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * Centerpiece "live pipeline" screen: visualizes the orchestrator stages (intake ->
 * clarificacion -> retrieval -> evaluacion -> confianza) streaming in real time. Each stage
 * lights up as it starts and reveals its `detalle` as it completes. The terminal `resultado`
 * either resolves into a short celebratory beat that hands off to the recommendation screen,
 * or — when the backend needs clarification — surfaces its questions with an inline answer
 * box that re-runs the pipeline in the same session.
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

                    if (uiState.needsClarification) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ClarificationPanel(
                            questions = uiState.clarificationQuestions,
                            onSend = { answer -> viewModel.sendClarification(answer) }
                        )
                    }

                    if (uiState.hasFailed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PipelineErrorCard(
                            reason = uiState.error
                                ?: uiState.errorRes?.let { stringResource(it) }.orEmpty(),
                            onBack = onBack
                        )
                    }

                    if (uiState.finishedWithoutData) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PipelineErrorCard(
                            reason = stringResource(R.string.processing_no_results_reason),
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
                        CompletionBeatCard(result = uiState.result)
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
        when {
            state.hasFailed -> StatusPill(
                text = stringResource(R.string.processing_pipeline_stopped),
                tone = PillTone.ERROR,
                icon = Icons.Filled.Warning
            )

            state.needsClarification -> StatusPill(
                text = stringResource(R.string.clarification_needed_pill),
                tone = PillTone.WARNING,
                icon = Icons.AutoMirrored.Filled.HelpOutline
            )

            state.finishedWithoutData -> StatusPill(
                text = stringResource(R.string.processing_no_results_pill),
                tone = PillTone.WARNING,
                icon = Icons.Filled.Warning
            )

            else -> {
                PulsingDot(color = MaterialTheme.extendedColors.scanCyan)
                Spacer(modifier = Modifier.width(8.dp))
                val step = (state.completedCount + 1).coerceAtMost(5)
                Text(
                    text = stringResource(R.string.processing_live_step, step),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.scanCyan
                )
            }
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
                    text = stringResource(R.string.processing_pipeline_failed),
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
    AgentStage.INTAKE,
    AgentStage.CLARIFICATION,
    AgentStage.RETRIEVAL,
    AgentStage.EVALUATION,
    AgentStage.CONFIDENCE
)

@Composable
private fun stageLabel(stage: AgentStage): String = stringResource(
    when (stage) {
        AgentStage.INTAKE -> R.string.agent_intake
        AgentStage.CLARIFICATION -> R.string.agent_clarification
        AgentStage.RETRIEVAL -> R.string.agent_retrieval
        AgentStage.EVALUATION -> R.string.agent_evaluation
        AgentStage.CONFIDENCE -> R.string.agent_confidence
    }
)

@Composable
private fun stageDescription(stage: AgentStage): String = stringResource(
    when (stage) {
        AgentStage.INTAKE -> R.string.agent_intake_description
        AgentStage.CLARIFICATION -> R.string.agent_clarification_description
        AgentStage.RETRIEVAL -> R.string.agent_retrieval_description
        AgentStage.EVALUATION -> R.string.agent_evaluation_description
        AgentStage.CONFIDENCE -> R.string.agent_confidence_description
    }
)

private fun stageIcon(stage: AgentStage): ImageVector = when (stage) {
    AgentStage.INTAKE -> Icons.Filled.Psychology
    AgentStage.CLARIFICATION -> Icons.AutoMirrored.Filled.HelpOutline
    AgentStage.RETRIEVAL -> Icons.Filled.TravelExplore
    AgentStage.EVALUATION -> Icons.Filled.Insights
    AgentStage.CONFIDENCE -> Icons.Filled.Verified
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
        AgentStage.INTAKE -> state.intake?.let { IntakeStageCard(it, modifier) }
        AgentStage.CLARIFICATION -> state.clarification?.let { ClarificationStageCard(it, modifier) }
        AgentStage.RETRIEVAL -> state.retrieval?.let { RetrievalStageCard(it, modifier) }
        AgentStage.EVALUATION -> EvaluationStageCard(
            candidateCount = state.evaluationCandidateCount,
            verdicts = state.verdicts,
            modifier = modifier
        )
        AgentStage.CONFIDENCE -> state.confidence?.let { ConfidenceStageCard(it, modifier) }
    }
}

// --- Per-stage `detalle` summary cards --------------------------------------------------

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
private fun IntakeStageCard(detail: StageDetail.Intake, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.processing_requirements_extracted),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail.structuredRequirements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ChipsRow(
                    items = detail.structuredRequirements.map { (key, value) ->
                        stringResource(R.string.key_value_format, key, value)
                    },
                    tone = PillTone.INFO
                )
            }
            if (detail.missingFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.processing_missing_fields),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.warning
                )
                Spacer(modifier = Modifier.height(6.dp))
                ChipsRow(items = detail.missingFields, tone = PillTone.WARNING)
            }
        }
    }
}

@Composable
private fun ClarificationStageCard(detail: StageDetail.Clarification, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (detail.questions.isEmpty()) {
                        stringResource(R.string.clarification_none_needed)
                    } else {
                        pluralStringResource(
                            R.plurals.clarification_questions_count,
                            detail.questions.size,
                            detail.questions.size
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = stringResource(R.string.clarification_iteration, detail.iterationCount),
                    tone = PillTone.NEUTRAL
                )
            }
            detail.questions.forEach { question ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.bullet_item, question),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RetrievalStageCard(detail: StageDetail.Retrieval, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.TravelExplore,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.scanCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.candidates_found,
                    detail.totalCandidates,
                    detail.totalCandidates
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EvaluationStageCard(
    candidateCount: Int?,
    verdicts: List<CandidateVerdict>,
    modifier: Modifier = Modifier
) {
    if (candidateCount == null && verdicts.isEmpty()) return
    SickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            val viable = verdicts.filter { it.isViable }
            val discarded = verdicts.filterNot { it.isViable }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (verdicts.isNotEmpty()) {
                    StatusPill(
                        text = pluralStringResource(R.plurals.viable_count, viable.size, viable.size),
                        tone = PillTone.SUCCESS,
                        icon = Icons.Filled.CheckCircle
                    )
                    StatusPill(
                        text = pluralStringResource(R.plurals.discarded_count, discarded.size, discarded.size),
                        tone = PillTone.ERROR,
                        icon = Icons.Filled.Close
                    )
                } else if (candidateCount != null) {
                    StatusPill(
                        text = pluralStringResource(
                            R.plurals.candidates_evaluated,
                            candidateCount,
                            candidateCount
                        ),
                        tone = PillTone.INFO,
                        icon = Icons.Filled.Insights
                    )
                }
            }
            val named = verdicts.filter { it.candidate.isNotBlank() }
            if (named.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    named.forEach { verdict ->
                        StatusPill(
                            text = verdict.candidate,
                            tone = if (verdict.isViable) PillTone.SUCCESS else PillTone.ERROR
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceStageCard(detail: StageDetail.Confidence, modifier: Modifier = Modifier) {
    SickCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConfidenceRing(
                progress = detail.score / 100f,
                label = stringResource(R.string.confidence_label)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (detail.needsHumanReview) {
                    StatusPill(
                        text = stringResource(R.string.processing_needs_review),
                        tone = PillTone.WARNING,
                        icon = Icons.Filled.Warning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = detail.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- Clarification panel (resultado.status == needs_clarification) -----------------------

@Composable
private fun ClarificationPanel(
    questions: List<String>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var answer by remember { mutableStateOf("") }
    SickCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.warning
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.clarification_needed_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            questions.forEach { question ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.bullet_item, question),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.clarification_answer_hint)) },
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(
                text = stringResource(R.string.clarification_send),
                onClick = { onSend(answer) },
                enabled = answer.isNotBlank(),
                icon = Icons.AutoMirrored.Filled.Send,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// --- Completion beat ---------------------------------------------------------------------

@Composable
private fun CompletionBeatCard(result: PipelineResult?, modifier: Modifier = Modifier) {
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
                text = result?.confidence?.rationale?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.processing_analysis_complete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            result?.confidence?.let { confidence ->
                Spacer(modifier = Modifier.height(12.dp))
                StatusPill(
                    text = stringResource(R.string.confidence_percent, confidence.score),
                    tone = PillTone.SUCCESS
                )
            }
        }
    }
}
