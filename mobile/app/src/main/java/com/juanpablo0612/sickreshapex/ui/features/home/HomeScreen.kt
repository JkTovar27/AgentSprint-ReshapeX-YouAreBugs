package com.juanpablo0612.sickreshapex.ui.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.ui.components.LoadingIndicator
import com.juanpablo0612.sickreshapex.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartAnalysis: (String) -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var description by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SICK Select Copilot") }) }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                item {
                    SectionHeader("New Analysis")
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Describe your industrial application") },
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    Button(
                        onClick = { onStartAnalysis(description) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        enabled = description.isNotBlank()
                    ) {
                        Text("Start AI Analysis")
                    }
                }

                item {
                    SectionHeader("Quick Examples")
                }
                items(uiState.quickExamples) { example ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { description = example.description }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = example.title, style = MaterialTheme.typography.titleSmall)
                            Text(text = example.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader("Recent Analyses")
                        TextButton(onClick = onNavigateToHistory) {
                            Text("See all")
                        }
                    }
                }
                items(uiState.recentAnalyses) { analysis ->
                    ListItem(
                        headlineContent = { Text(analysis.description) },
                        supportingContent = { Text("Family: ${analysis.sensorFamily ?: "Unknown"}") }
                    )
                }
            }
        }
    }
}
