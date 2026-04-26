package com.example.routes

import com.example.database.DatabaseFactory
import com.example.models.Habit
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.habitRoutes() {
    authenticate("auth-jwt") {
        route("/habits") {

            // Вспомогательная функция, чтобы не дублировать код
            fun getUserId(principal: JWTPrincipal?): Int? {
                val claim = principal?.payload?.getClaim("userId")
                return claim?.asInt() ?: claim?.asString()?.toIntOrNull()
            }

            // Получение всех привычек
            get {
                val userId = getUserId(call.principal<JWTPrincipal>())
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val habits = DatabaseFactory.getAllHabits(userId)
                call.respond(habits)
            }

            // Добавление новой привычки
            post {
                val userId = getUserId(call.principal<JWTPrincipal>())
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val habit = call.receive<Habit>()
                DatabaseFactory.addHabit(habit, userId)
                call.respond(HttpStatusCode.Created)
            }

            // Обновление привычки
            put("/{id}") {
                val userId = getUserId(call.principal<JWTPrincipal>())
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)

                val habitId = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                val updatedHabit = call.receive<Habit>()
                val wasUpdated = DatabaseFactory.updateHabit(habitId, userId, updatedHabit)
                if (wasUpdated) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Привычка не найдена")
                }
            }

            // Удаление привычки
            delete("/{id}") {
                val userId = getUserId(call.principal<JWTPrincipal>())
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                val habitId = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)

                DatabaseFactory.deleteHabit(habitId, userId)
                call.respond(HttpStatusCode.OK)
            }

            // Отметка о выполнении
            post("/{id}/complete") {
                val habitId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Неверный ID привычки")

                val userId = getUserId(call.principal<JWTPrincipal>())
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Пользователь не авторизован")

                try {
                    val success = DatabaseFactory.markHabitCompleted(habitId, userId)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Не удалось обновить статус")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Ошибка сервера")
                }
            }
        }
    }
}