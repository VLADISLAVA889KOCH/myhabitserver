package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class DreamResponse(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val isCompleted: Boolean, // Добавлено: статус выполнения мечты
    val habitIds: List<Int>   // Уточняем тип списка как Int
)