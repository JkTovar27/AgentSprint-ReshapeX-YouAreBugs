package com.juanpablo0612.sickreshapex.ui.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanpablo0612.sickreshapex.domain.usecase.GetFavoritesUseCase
import com.juanpablo0612.sickreshapex.domain.usecase.GetRecentAnalysesUseCase
import com.juanpablo0612.sickreshapex.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val getRecentAnalyses: GetRecentAnalysesUseCase,
    private val getFavorites: GetFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryState())
    val uiState: StateFlow<HistoryState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val analyses = getRecentAnalyses()
                val favorites = getFavorites()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        analyses = analyses,
                        favoriteIds = favorites.map { favorite -> favorite.analysisId }.toSet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Toggles favorite state for [id] and updates local state immediately so the UI stays responsive. */
    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            try {
                val isFavoriteNow = toggleFavoriteUseCase(id)
                _uiState.update { state ->
                    state.copy(
                        favoriteIds = if (isFavoriteNow) state.favoriteIds + id else state.favoriteIds - id
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
