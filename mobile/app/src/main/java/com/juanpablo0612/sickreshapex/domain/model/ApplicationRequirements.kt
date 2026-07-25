package com.juanpablo0612.sickreshapex.domain.model

data class ApplicationRequirements(
    val tipo_objeto: String,
    val distancia_mm: Int,
    val ambiente: String,
    val material_superficie: String,
    val montaje: String
)
