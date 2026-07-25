package com.juanpablo0612.sickreshapex.ui.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanpablo0612.sickreshapex.domain.usecase.GetQuickExamplesUseCase
import com.juanpablo0612.sickreshapex.domain.usecase.GetRecentAnalysesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getQuickExamples: GetQuickExamplesUseCase,
    private val getRecentAnalyses: GetRecentAnalysesUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeState())
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val examples = getQuickExamples()
                val recent = getRecentAnalyses()
                _uiState.update { it.copy(
                    isLoading = false,
                    quickExamples = examples,
                    recentAnalyses = recent
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
