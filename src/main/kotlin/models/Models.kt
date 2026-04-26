package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val userId: Int,
    val login: String
)

@Serializable
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)

@Serializable
data class UserProfile(
    val login: String,
    val email: String,
    val daysInApp: Int,
    val totalHabits: Int,
    val successRate: Int,
    val avatarUrl: String? = null
)

@Serializable
data class StatisticsResponse(
    val totalHabits: Int,
    val completedToday: Int,
    val completionRate: Double
)

@Serializable
data class UploadResponse(
    val imageUrl: String,
    val status: String
)