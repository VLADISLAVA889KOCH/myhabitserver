package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class HabitStat(
    val habitId: Int,
    val name: String,
    val color: String,
    val completedCount: Int,
    val goalValue: Int,
    val progressPercent: Int,
    val currentStreak: Int
)

@Serializable
data class UserStatsResponse(
    val progressDay: Int,    // Процент за сегодня
    val progressWeek: Int,   // Процент за 7 дней
    val progressMonth: Int,  // Процент за 30 дней
    val activeStreaks: Int,  // Серия дней
    val bestHabit: String,   // Лидер дисциплины
    // ИСПРАВЛЕНО: Добавлен тип <HabitStat>
    val habitsDistribution: List<HabitStat>
)
