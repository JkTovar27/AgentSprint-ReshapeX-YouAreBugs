package com.juanpablo0612.sickreshapex.ui.features.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecommendationViewModel(private val repository: AnalysisRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RecommendationState())
    val uiState: StateFlow<RecommendationState> = _uiState.asStateFlow()

    fun loadAnalysis(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val analysis = repository.getAnalysisById(id)
                _uiState.update { it.copy(isLoading = false, analysis = analysis) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun startNewAnalysis(description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val analysis = repository.startAnalysis(description)
                _uiState.update { it.copy(isLoading = false, analysis = analysis) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
