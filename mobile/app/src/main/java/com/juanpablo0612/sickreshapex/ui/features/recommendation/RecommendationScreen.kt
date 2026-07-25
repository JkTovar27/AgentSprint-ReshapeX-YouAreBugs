package com.juanpablo0612.sickreshapex.ui.features.recommendation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.ui.components.LoadingIndicator
import com.juanpablo0612.sickreshapex.ui.components.RequirementRow
import com.juanpablo0612.sickreshapex.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    viewModel: RecommendationViewModel,
    analysisId: String?,
    initialDescription: String?,
    onBack: () -> Unit,
    onViewTechnicalDetails: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(analysisId, initialDescription) {
        if (analysisId != null) {
            viewModel.loadAnalysis(analysisId)
        } else if (initialDescription != null) {
            viewModel.startNewAnalysis(initialDescription)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recommendation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center
            ) {
                LoadingIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Analyzing your application...",
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (uiState.analysis != null) {
            val analysis = uiState.analysis!!
            val recommendation = analysis.recommendation

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (recommendation != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Recommended Family",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = recommendation.sensorFamily,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recommendation.executiveSummary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    SectionHeader("Application Requirements")
                    val reqs = recommendation.requirements
                    RequirementRow("Object Type", reqs.tipo_objeto)
                    RequirementRow("Distance", "${reqs.distancia_mm} mm")
                    RequirementRow("Environment", reqs.ambiente)
                    RequirementRow("Surface", reqs.material_superficie)
                    RequirementRow("Mounting", reqs.montaje)

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onViewTechnicalDetails(analysis.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Technical Details")
                    }
                } else {
                    Text("No recommendation found.")
                }
            }
        } else if (uiState.error != null) {
            Text("Error: ${uiState.error}")
        }
    }
}
