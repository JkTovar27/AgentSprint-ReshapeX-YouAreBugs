package com.juanpablo0612.sickreshapex.ui.features.processing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.domain.model.AgentStage
import com.juanpablo0612.sickreshapex.domain.model.EvaluatorOutput
import com.juanpablo0612.sickreshapex.domain.model.NextStep
import com.juanpablo0612.sickreshapex.domain.model.PlannerOutput
import com.juanpablo0612.sickreshapex.domain.model.ResponderOutput
import com.juanpablo0612.sickreshapex.domain.model.RetrieverOutput
import com.juanpablo0612.sickreshapex.domain.model.StageStatus
import com.juanpablo0612.sickreshapex.domain.model.ValidatorOutput
import com.juanpablo0612.sickreshapex.ui.components.AnimatedGradientBackdrop
import com.juanpablo0612.sickreshapex.ui.components.ConfidenceRing
import com.juanpablo0612.sickreshapex.ui.components.ExpandableAppearance
import com.juanpablo0612.sickreshapex.ui.components.PillTone
import com.juanpablo0612.sickreshapex.ui.components.PulsingDot
import com.juanpablo0612.sickreshapex.ui.components.RadarPulse
import com.juanpablo0612.sickreshapex.ui.components.SecondaryActionButton
import com.juanpablo0612.sickreshapex.ui.components.SickCard
import com.juanpablo0612.sickreshapex.ui.components.StatusPill
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

                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PipelineErrorCard(
                            failedStage = uiState.failedStage,
                            reason = uiState.error.orEmpty(),
                            onBack = onBack
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

                AnimatedVisibility(
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
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
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
                    text = "Analyzing application",
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
        if (state.error == null) {
            PulsingDot(color = MaterialTheme.extendedColors.scanCyan)
            Spacer(modifier = Modifier.width(8.dp))
            val step = (state.completedCount + 1).coerceAtMost(5)
            Text(
                text = "LIVE  ·  Step $step of 5",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extendedColors.scanCyan
            )
        } else {
            StatusPill(text = "Pipeline stopped", tone = PillTone.ERROR, icon = Icons.Filled.Warning)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${state.completedCount}/5",
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
                    text = failedStage?.let { "${stageLabel(it)} failed" } ?: "Pipeline failed",
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
            SecondaryActionButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
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

private fun stageLabel(stage: AgentStage): String = when (stage) {
    AgentStage.PLANNER -> "Planner"
    AgentStage.RETRIEVER -> "Retriever"
    AgentStage.VALIDATOR -> "Validator"
    AgentStage.EVALUATOR -> "Evaluator"
    AgentStage.RESPONDER -> "Responder"
}

private fun stageDescription(stage: AgentStage): String = when (stage) {
    AgentStage.PLANNER -> "Extracting structured requirements from your description"
    AgentStage.RETRIEVER -> "Searching the SICK sensor knowledge base"
    AgentStage.VALIDATOR -> "Checking candidates against every requirement"
    AgentStage.EVALUATOR -> "Scoring confidence in the leading match"
    AgentStage.RESPONDER -> "Preparing your recommendation"
}

private fun stageIcon(stage: AgentStage): ImageVector = when (stage) {
    AgentStage.PLANNER -> Icons.Filled.Psychology
    AgentStage.RETRIEVER -> Icons.Filled.TravelExplore
    AgentStage.VALIDATOR -> Icons.Filled.FactCheck
    AgentStage.EVALUATOR -> Icons.Filled.Insights
    AgentStage.RESPONDER -> Icons.Filled.Send
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
            StageStatus.DONE -> StatusPill(text = "Done", tone = PillTone.SUCCESS, icon = Icons.Filled.Check)
            StageStatus.ERROR -> StatusPill(text = "Failed", tone = PillTone.ERROR, icon = Icons.Filled.ErrorOutline)
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
                    text = "Requirements extracted",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(output.extractionConfidence * 100).toInt()}%",
                    style = ReadoutType.small,
                    color = MaterialTheme.extendedColors.scanCyan
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ChipsRow(
                items = output.structuredRequirements.map { (key, value) -> "$key: $value" },
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
                    text = "${output.candidates.size} candidates found",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${output.sources.size} sources",
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
                    text = "${output.viableCandidates.size} viable",
                    tone = PillTone.SUCCESS,
                    icon = Icons.Filled.CheckCircle
                )
                StatusPill(
                    text = "${output.discardedCandidates.size} discarded",
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
            ConfidenceRing(progress = output.confidence, label = "confidence")
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val (tone, label) = when (output.nextStep) {
                    NextStep.RESPOND -> PillTone.SUCCESS to "Respond"
                    NextStep.CLARIFY -> PillTone.WARNING to "Clarify"
                    NextStep.CONTINUE -> PillTone.INFO to "Continue"
                    NextStep.ESCALATE -> PillTone.ERROR to "Escalate"
                }
                StatusPill(text = "Next: $label", tone = tone)
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
                StatusPill(text = "${(output.confidence * 100).toInt()}% confidence", tone = PillTone.SUCCESS)
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
                text = "Recommendation ready",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = responderOutput?.message ?: "Analysis complete.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (responderOutput != null) {
                Spacer(modifier = Modifier.height(12.dp))
                StatusPill(
                    text = "${(responderOutput.confidence * 100).toInt()}% confidence",
                    tone = PillTone.SUCCESS
                )
            }
        }
    }
}
