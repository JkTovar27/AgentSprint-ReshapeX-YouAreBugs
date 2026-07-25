package com.juanpablo0612.sickreshapex.domain.model

import androidx.annotation.StringRes
import com.juanpablo0612.sickreshapex.R

sealed class AppError(@StringRes val messageRes: Int?) {
    object NetworkError : AppError(R.string.error_network)
    object ServerError : AppError(R.string.error_server)
    data class UnknownError(val error: String) : AppError(null)
}
