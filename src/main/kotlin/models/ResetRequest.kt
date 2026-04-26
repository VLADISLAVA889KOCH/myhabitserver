package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class ResetRequest(
    val login: String,
    val email: String
)