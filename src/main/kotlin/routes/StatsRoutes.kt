package com.example.routes

import com.example.database.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.statsRoutes() {
    authenticate("auth-jwt") {
        get("/statistics") {
            val principal = call.principal<JWTPrincipal>()

            // Исправленное извлечение ID
            val userId = principal?.payload?.getClaim("userId")?.asInt()
                ?: principal?.payload?.getClaim("userId")?.asString()?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Пользователь не авторизован")

            try {
                val stats = DatabaseFactory.getUserStatistics(userId)
                call.respond(stats)
            } catch (e: Exception) {
                println("ОШИБКА СТАТИСТИКИ: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Ошибка: ${e.message}")
            }
        }
    }
}
