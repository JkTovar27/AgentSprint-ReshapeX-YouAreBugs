package com.juanpablo0612.sickreshapex.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.domain.model.AlternativeOption
import com.juanpablo0612.sickreshapex.domain.model.Recommendation

@Composable
fun RecommendationCard(recommendation: Recommendation, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = recommendation.sensorFamily, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = recommendation.executiveSummary, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Confidence: ${(recommendation.confidenceLevel * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AlternativeCard(alternative: AlternativeOption, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = alternative.sensorFamily, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = alternative.discardReason, style = MaterialTheme.typography.bodySmall)
        }
    }
}
