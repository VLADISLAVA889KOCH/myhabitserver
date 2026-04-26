package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class DreamRequest(
    val title: String,
    val imageUrl: String?,
    val habitIds: List<Int> // Уточняем тип списка
)