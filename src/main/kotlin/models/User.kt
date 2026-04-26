package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class UserRegistrationRequest(
    val login: String,
    val email: String,
    val password: String
)

@Serializable
data class UserLoginRequest(
    val login: String,
    val password: String
)

// Добавим модельку для приема кода из письма
@Serializable
data class VerificationRequest(
    val login: String,
    val code: String
)