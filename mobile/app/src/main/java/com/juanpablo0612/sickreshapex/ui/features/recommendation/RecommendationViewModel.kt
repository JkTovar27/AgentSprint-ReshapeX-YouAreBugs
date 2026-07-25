package com.juanpablo0612.sickreshapex.ui.features.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads a single, already-saved [com.juanpablo0612.sickreshapex.domain.model.Analysis] by id
 * and exposes it as the payoff/results screen state. The pipeline that produces a new analysis
 * lives entirely in the Processing feature now — this screen only ever reads a finished result.
 */
class RecommendationViewModel(private val repository: AnalysisRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RecommendationState())
    val uiState: StateFlow<RecommendationState> = _uiState.asStateFlow()

    fun loadAnalysis(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val analysis = repository.getAnalysisById(id)
                val isFavorite = repository.getFavorites().any { favorite -> favorite.analysisId == id }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        analysis = analysis,
                        isFavorite = isFavorite,
                        error = if (analysis == null) "We couldn't find this analysis." else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Something went wrong while loading this analysis.")
                }
            }
        }
    }

    /** Optional nicety: star/unstar this analysis. Failures are non-critical and ignored. */
    fun toggleFavorite(analysisId: String) {
        viewModelScope.launch {
            try {
                val isFavorite = repository.toggleFavorite(analysisId)
                _uiState.update { it.copy(isFavorite = isFavorite) }
            } catch (_: Exception) {
                // Favoriting is a nice-to-have; swallow failures rather than surfacing an error state.
            }
        }
    }
}
