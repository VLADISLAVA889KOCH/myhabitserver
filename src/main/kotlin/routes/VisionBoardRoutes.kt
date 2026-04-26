package com.example.routes

import com.example.database.DatabaseFactory
import com.example.models.DreamRequest
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.visionBoardRoutes() {
    authenticate("auth-jwt") {
        route("/vision-board") {

            fun getUserId(principal: JWTPrincipal?): Int? {
                val claim = principal?.payload?.getClaim("userId")
                return claim?.asInt() ?: claim?.asString()?.toIntOrNull()
            }

            // 1. Получить все мечты пользователя
            get {
                val userId = getUserId(call.principal<JWTPrincipal>())
                if (userId == null) return@get call.respond(HttpStatusCode.Unauthorized)

                val dreams = DatabaseFactory.getVisionBoard(userId)
                call.respond(dreams)
            }

            // 2. Добавить новую мечту
            post {
                val userId = getUserId(call.principal<JWTPrincipal>())
                if (userId == null) return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<DreamRequest>()
                DatabaseFactory.addDream(userId, request.title, request.imageUrl, request.habitIds)
                call.respond(HttpStatusCode.Created, mapOf("message" to "Dream added successfully"))
            }

            // 3. Редактировать мечту
            put("/{id}") {
                val userId = getUserId(call.principal<JWTPrincipal>())
                if (userId == null) return@put call.respond(HttpStatusCode.Unauthorized)

                val dreamId = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                val request = call.receive<DreamRequest>()

                val updated = DatabaseFactory.updateDream(dreamId, userId, request.title, request.imageUrl, request.habitIds)
                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Dream updated successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Dream not found")
                }
            }

            // 4. Удалить мечту
            delete("/{id}") {
                val userId = getUserId(call.principal<JWTPrincipal>())
                if (userId == null) return@delete call.respond(HttpStatusCode.Unauthorized)

                val dreamId = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val deleted = DatabaseFactory.deleteDream(dreamId, userId)

                if (deleted) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Dream not found or access denied")
                }
            }

            // 5. Переключить статус
            post("/{id}/toggle") {
                val userId = getUserId(call.principal<JWTPrincipal>())
                if (userId == null) return@post call.respond(HttpStatusCode.Unauthorized)

                val dreamId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)

                val success = DatabaseFactory.toggleDreamStatus(dreamId, userId)
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Dream not found")
                }
            }
        }
    }
}