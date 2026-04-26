package com.example.routes

import com.example.database.DatabaseFactory
import com.example.models.ChangePasswordRequest
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mindrot.jbcrypt.BCrypt
import java.io.File

fun Route.settingsRouting() {
    authenticate("auth-jwt") {
        route("/user") {
            // Вспомогательная функция для извлечения ID из токена (любого типа)
            fun getUserId(principal: JWTPrincipal?): Int? {
                val claim = principal?.payload?.getClaim("userId")
                return claim?.asInt() ?: claim?.asString()?.toIntOrNull()
            }
            post("/upload-avatar") {
                println("--- ПОПЫТКА ЗАГРУЗКИ АВАТАРА ---")
                val principal = call.principal<JWTPrincipal>()
                val userId = getUserId(principal)

                if (userId == null) {
                    println("ОШИБКА: userId не найден в токене при загрузке")
                    return@post call.respond(HttpStatusCode.Unauthorized, "Неавторизован")
                }

                try {
                    val multipart = call.receiveMultipart()
                    var fileName = ""

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val timestamp = System.currentTimeMillis()
                            val name = "avatar_${userId}_$timestamp.jpg"
                            val folder = File("uploads")
                            if (!folder.exists()) folder.mkdirs()

                            val file = File(folder, name)
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output -> input.copyTo(output) }
                            }
                            fileName = "/uploads/$name"
                        }
                        part.dispose()
                    }

                    if (fileName.isNotEmpty()) {
                        DatabaseFactory.updateAvatar(userId, fileName)
                        println("УСПЕХ: Аватар сохранен как $fileName")
                        call.respond(HttpStatusCode.OK, mapOf("url" to fileName))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Файл не найден")
                    }
                } catch (e: Exception) {
                    println("ОШИБКА СЕРВЕРА: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
                }
            }

            // 1. Профиль
            get("/profile") {
                val userId = getUserId(call.principal()) ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val profile = DatabaseFactory.getUserProfile(userId)
                if (profile != null) call.respond(profile) else call.respond(HttpStatusCode.NotFound)
            }

            // 2. Смена пароля
            post("/change-password") {
                val userId = getUserId(call.principal()) ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<ChangePasswordRequest>()

                // Получаем текущий хэш из базы (который был создан через SHA-256)
                val currentHashInDb = DatabaseFactory.getUserPasswordHash(userId)

                // Хэшируем введенный "старый пароль" тем же способом (SHA-256)
                val inputOldPasswordHash = com.example.routes.hashPassword(request.oldPassword.trim())

                if (currentHashInDb == inputOldPasswordHash) {
                    // Если совпали, создаем новый хэш тоже через SHA-256 (для единообразия)
                    val newHash = com.example.routes.hashPassword(request.newPassword.trim())
                    DatabaseFactory.updatePassword(userId, newHash)
                    call.respond(HttpStatusCode.OK, "Пароль успешно изменен")
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Неверный старый пароль")
                }
            }

            // 3. Удаление аккаунта
            delete("/account") {
                val principal = call.principal<JWTPrincipal>()
                val userId = getUserId(principal)

                if (userId == null) {
                    return@delete call.respond(HttpStatusCode.Unauthorized, "Неавторизован")
                }

                try {
                    val deleted = DatabaseFactory.deleteUserAccount(userId)
                    if (deleted) {
                        println("УСПЕХ: Пользователь $userId удалил аккаунт")
                        call.respond(HttpStatusCode.OK, "Аккаунт успешно удален")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Пользователь не найден")
                    }
                } catch (e: Exception) {
                    println("ОШИБКА при удалении пользователя $userId: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, "Ошибка при удалении данных")
                }
            }


        }
    }
}