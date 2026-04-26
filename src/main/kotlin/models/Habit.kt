package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class Habit(
    val id: Int? = null,
    val userId: Int? = null,
    val name: String,
    val description: String?,
    val goalDays: String,
    val priority: Int = 1,
    val colorHex: String = "#4CAF50",
    val reminders: List<String> = emptyList(), // Теперь список
    val isActive: Boolean = true,
    val isCompletedToday: Boolean = false,
    val showRemindersOnCard: Boolean = true
)