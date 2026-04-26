package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequest(
    val login: String,
    val code: String,
    val newPassword: String
)