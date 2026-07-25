package com.juanpablo0612.sickreshapex.domain.model

sealed class AppError(val message: String) {
    object NetworkError : AppError("Network error, please check your connection")
    object ServerError : AppError("Server error, please try again later")
    data class UnknownError(val error: String) : AppError(error)
}
