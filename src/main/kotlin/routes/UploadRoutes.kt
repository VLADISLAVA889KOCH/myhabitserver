package com.example.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.*
import com.example.database.DatabaseFactory.dbQuery
import com.example.database.Users
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq





fun Route.uploadRoutes() {
    route("/upload") {
        post {
            try {
                val multipart = call.receiveMultipart()
                var fileName: String? = null

                val uploadDir = File("uploads")
                if (!uploadDir.exists()) uploadDir.mkdirs()

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        // Извлекаем расширение более надежно
                        val originalName = part.originalFileName ?: "image.jpg"
                        val ext = originalName.substringAfterLast('.', "jpg")

                        // Генерируем уникальное имя
                        fileName = "${UUID.randomUUID()}.$ext"
                        val file = File(uploadDir, fileName!!)

                        part.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    part.dispose()
                }

                if (fileName != null) {
                    call.respond(HttpStatusCode.Created, mapOf("fileName" to fileName))
                } else {
                    call.respond(HttpStatusCode.BadRequest, "No file uploaded")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${e.message}")
            }
        }
    }

    authenticate("auth-jwt") {
        post("/user/upload-avatar") {
            try {
                // Извлекаем ID пользователя из токена
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "User ID not found in token")

                val multipart = call.receiveMultipart()
                var fileName: String? = null
                val uploadDir = File("uploads")
                if (!uploadDir.exists()) uploadDir.mkdirs()

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val originalName = part.originalFileName ?: "avatar.jpg"
                        val ext = originalName.substringAfterLast('.', "jpg")

                        // Имя файла делаем привязанным к ID пользователя, чтобы не плодить мусор
                        fileName = "avatar_user_$userId.$ext"
                        val file = File(uploadDir, fileName!!)

                        part.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    part.dispose()
                }

                if (fileName != null) {
                    val avatarPath = "/uploads/$fileName"

                    // СОХРАНЯЕМ В БАЗУ: Обновляем колонку avatar_url у текущего пользователя
                    dbQuery {
                        Users.update({ Users.id eq userId }) {
                            it[avatarUrl] = avatarPath
                        }
                    }

                    call.respond(HttpStatusCode.OK, mapOf("avatarUrl" to avatarPath))
                } else {
                    call.respond(HttpStatusCode.BadRequest, "No file uploaded")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${e.message}")
            }
        }
    }
}