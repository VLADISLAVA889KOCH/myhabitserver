package com.example

import io.ktor.server.application.*
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt

fun Application.configureSecurity() {
    authentication {
        jwt("auth-jwt") { /* настройки тут */ }
    }
}