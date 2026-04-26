package com.example

import com.example.database.DatabaseFactory
import com.example.plugins.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.http.content.*
import java.io.File
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.callloging.* // Добавлено для логов
import io.ktor.server.plugins.statuspages.* // Добавлено для отлова ошибок
import io.ktor.http.* // Для статус-кодов
import io.ktor.server.response.*
import org.slf4j.event.Level
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.routing.routing

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {

    // 1. ПОДРОБНОЕ ЛОГИРОВАНИЕ
    // Теперь в консоли IDEA ты увидишь каждый запрос (GET, POST) и его результат
    install(CallLogging) {
        level = Level.INFO
    }

    // 2. ОТЛОВ ОШИБОК (StatusPages)
    // Если в коде (например, в DatabaseFactory) случится ошибка, она напечатается в консоль
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            println("=== ОБНАРУЖЕНА ОШИБКА НА СЕРВЕРЕ ===")
            cause.printStackTrace() // Печатает полный путь ошибки (Stacktrace) в консоль IDEA
            call.respond(HttpStatusCode.InternalServerError, "Ошибка сервера: ${cause.message}")
        }
    }

    // 3. Инициализация базы данных
    DatabaseFactory.init(environment.config)

    // 4. Установка плагина сериализации (JSON)
    install(ContentNegotiation) {
        json()
    }

    // 5. Настройка безопасности (Authentication)
    authentication {
        jwt("auth-jwt") {
            // Берем настройки из системы (на Render мы их пропишем),
            // либо используем дефолтные для локального теста
            val jwtSecret = System.getenv("JWT_SECRET") ?: "super-secret-key-123"
            val jwtIssuer = System.getenv("JWT_ISSUER") ?: "http://0.0.0.0:8080"
            val jwtAudience = "users"

            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )

            validate { credential ->
                val userIdClaim = credential.payload.getClaim("userId")
                val userId = userIdClaim.asInt() ?: userIdClaim.asString()?.toIntOrNull()

                if (userId != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    println("!!! ОШИБКА JWT: userId не найден в токене")
                    null
                }
            }
        }
    }



    // Не забудь вызвать настройку роутинга в конце, если она в отдельном файле:
    configureRouting()
    routing {
        // remotePath — это то, как путь выглядит в ссылке (URL)
        // dir — это реальная папка в проекте
        staticFiles(remotePath = "/uploads", dir = File("uploads"))
    }
}

