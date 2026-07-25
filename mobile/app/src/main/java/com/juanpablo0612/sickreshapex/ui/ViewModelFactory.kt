package com.juanpablo0612.sickreshapex.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.juanpablo0612.sickreshapex.domain.repository.AnalysisRepository
import com.juanpablo0612.sickreshapex.ui.features.home.HomeViewModel
import com.juanpablo0612.sickreshapex.ui.features.recommendation.RecommendationViewModel

class ViewModelFactory(private val repository: AnalysisRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repository) as T
            modelClass.isAssignableFrom(RecommendationViewModel::class.java) -> RecommendationViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
