package com.example.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.database.Users
import com.example.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.util.*

const val jwtSecret = "super-secret-key-123"
const val jwtIssuer = "https://myhabitserver.onrender.com/"
const val jwtAudience = "users"

fun hashPassword(password: String): String {
    val bytes = password.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

fun Route.authRouting() {

    // 0. РЕГИСТРАЦИЯ
    route("/register") {
        post {
            try {
                val request = call.receive(UserRegistrationRequest::class)
                val rLogin = request.login.toString().trim()
                val rEmail = request.email.toString().trim()
                val rPassword = request.password.toString().trim()

                // Достаем данные из базы
                val userByLogin = transaction { Users.select { Users.login eq rLogin }.singleOrNull() }
                val userByEmail = transaction { Users.select { Users.email eq rEmail }.singleOrNull() }

                // ПРАВИЛО 1: Полное совпадение и аккаунт подтвержден
                if (userByLogin != null && userByEmail != null && userByLogin[Users.id] == userByEmail[Users.id]) {
                    if (userByLogin[Users.isVerified]) {
                        call.respond(HttpStatusCode.Conflict, "У вас уже есть аккаунт, войдите через экран авторизации")
                        return@post
                    }
                }

                // ПРАВИЛО 2: Логин занят (даже если почта новая или чужая)
                if (userByLogin != null && userByLogin[Users.email] != rEmail) {
                    call.respond(HttpStatusCode.Conflict, "Этот логин занят, введите другой")
                    return@post
                }

                // ПРАВИЛО 3: Почта занята другим пользователем (новый логин + старая почта)
                if (userByEmail != null && userByEmail[Users.login] != rLogin) {
                    call.respond(HttpStatusCode.Conflict, "Эта почта уже зарегистрирована другим пользователем, укажите новую почту")
                    return@post
                }

                // КЕЙС ДЛЯ ТЕХ, КТО НЕ ДОШЕЛ ДО КОНЦА (Логин и почта совпали, но isVerified = false)
                if (userByLogin != null && userByEmail != null) {
                    val randomCode = (100000..999999).random().toString()
                    transaction {
                        Users.update({ Users.id eq userByLogin[Users.id] }) {
                            it[verificationCode] = randomCode
                        }
                    }
                    com.example.plugins.EmailService.sendCode(rEmail, randomCode)
                    call.respond(HttpStatusCode.OK, "Аккаунт ожидает подтверждения. Код отправлен повторно")
                    return@post
                }

                // СОЗДАНИЕ НОВОГО (Если ничего выше не сработало)
                val randomCode = (100000..999999).random().toString()
                transaction {
                    Users.insert {
                        it[login] = rLogin
                        it[email] = rEmail
                        it[passwordHash] = hashPassword(rPassword)
                        it[verificationCode] = randomCode
                        it[isVerified] = false
                    }
                }
                com.example.plugins.EmailService.sendCode(rEmail, randomCode)
                call.respond(HttpStatusCode.OK, "Код подтверждения отправлен")

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Ошибка данных")
            }

        }
    }


    println("МАРШРУТЫ АВТОРИЗАЦИИ ЗАГРУЖЕНЫ")

    // 1. ЗАПРОС КОДА (ЗАБЫЛИ ПАРОЛЬ)
    route("/forgot-password") {
        post {
            try {
                val request = call.receive<ResetRequest>()
                println("ЗАПРОС НА СБРОС: Login='${request.login}', Email='${request.email}'")

                val randomCode = (100000..999999).random().toString()

                val user = transaction {
                    addLogger(StdOutSqlLogger)
                    Users.select {
                        (Users.login.lowerCase() eq request.login.trim().lowercase()) and
                                (Users.email.lowerCase() eq request.email.trim().lowercase())
                    }.singleOrNull()
                }

                if (user != null) {
                    transaction {
                        Users.update({ Users.id eq user[Users.id] }) {
                            it[verificationCode] = randomCode
                        }
                    }
                    com.example.plugins.EmailService.sendCode(request.email.trim(), randomCode)
                    call.respond(HttpStatusCode.OK, "Код отправлен")
                } else {
                    println("ОШИБКА: Пользователь не найден в БД!")
                    call.respond(HttpStatusCode.NotFound, "Данные не найдены")
                }
            } catch (e: Exception) {
                println("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, "Ошибка: ${e.localizedMessage}")
            }
        }
    }

    // 2. СБРОС ПАРОЛЯ
    route("/reset-password") {
        post {
            try {
                val request = call.receive<ResetPasswordRequest>()
                val cleanLogin = request.login.trim()
                val cleanCode = request.code.trim()

                println("ПОПЫТКА СБРОСА: Логин='$cleanLogin', Код='$cleanCode'")

                val user = transaction {
                    // Ищем пользователя, у которого совпадает логин И проверочный код
                    Users.select {
                        (Users.login eq cleanLogin) and (Users.verificationCode eq cleanCode)
                    }.singleOrNull()
                }

                if (user != null) {
                    transaction {
                        Users.update({ Users.id eq user[Users.id] }) {
                            it[passwordHash] = hashPassword(request.newPassword.trim())
                            it[verificationCode] = null // Очищаем код после использования
                        }
                    }
                    println("УСПЕХ: Пароль для $cleanLogin обновлен")
                    call.respond(HttpStatusCode.OK, "Пароль обновлен")
                } else {
                    println("ОШИБКА: Пользователь с таким кодом не найден в БД")
                    call.respond(HttpStatusCode.BadRequest, "Неверный код или логин")
                }
            } catch (e: Exception) {
                println("КРИТИЧЕСКАЯ ОШИБКА СБРОСА: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Ошибка на сервере")
            }
        }
    }

    // 3. ВЕРИФИКАЦИЯ
    route("/verify") {
        post {
            try {
                val request = call.receive<VerificationRequest>()
                val cleanLogin = request.login.trim()
                val cleanCode = request.code.trim()

                val user = transaction {
                    Users.select {
                        (Users.login eq cleanLogin) and (Users.verificationCode eq cleanCode)
                    }.singleOrNull()
                }

                if (user != null) {
                    // ПРОВЕРКА: Если пользователь уже был верифицирован (isVerified == true),
                    // значит это процесс СБРОСА ПАРОЛЯ, и код стирать ПОКА НЕ НУЖНО.
                    val alreadyVerified = user[Users.isVerified]

                    if (!alreadyVerified) {
                        // Это первичная регистрация -> подтверждаем и стираем код
                        transaction {
                            Users.update({ Users.login eq cleanLogin }) {
                                it[isVerified] = true
                                it[verificationCode] = null
                            }
                        }
                    } else {
                        // Это сброс пароля -> код НЕ стираем, он нужен для следующего шага
                        println("DEBUG: Код подтвержден для сброса пароля пользователя $cleanLogin. Код сохранен для смены пароля.")
                    }

                    call.respond(HttpStatusCode.OK, "Успешно")
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Неверный код")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Ошибка сервера")
            }
        }
    }

    // 4. ЛОГИН
    route("/login") {
        post {
            try {
                val credentials = call.receive<UserLoginRequest>()
                val userEntry = transaction {
                    Users.select { Users.login eq credentials.login.trim() }.singleOrNull()
                }

                if (userEntry == null) {
                    call.respond(HttpStatusCode.NotFound, "Нет пользователя")
                } else {
                    val verified = userEntry[Users.isVerified]
                    if (!verified) {
                        call.respond(HttpStatusCode.Forbidden, "Подтвердите почту")
                        return@post
                    }

                    val inputHash = hashPassword(credentials.password.trim())
                    val databaseHash = userEntry[Users.passwordHash]

                    println("--- ПРОВЕРКА ВХОДА ---")
                    println("Логин: ${credentials.login}")
                    println("Хэш из приложения: $inputHash")
                    println("Хэш из базы данных: $databaseHash")

                    if (inputHash == databaseHash) {
                        println("РЕЗУЛЬТАТ: Пароли совпали, создаю токен...")

                        val token = JWT.create()
                            .withAudience(jwtAudience)
                            .withIssuer(jwtIssuer)
                            .withClaim("userId", userEntry[Users.id])
                            .withClaim("login", credentials.login.trim())
                            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                            .sign(Algorithm.HMAC256(jwtSecret))

                        call.respond(mapOf("token" to token))
                    } else {
                        println("РЕЗУЛЬТАТ: ОШИБКА 401 - Хэши не совпали!")
                        call.respond(HttpStatusCode.Unauthorized, "Пароль неверный")
                    }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Ошибка: ${e.localizedMessage}")
            }
        }
    }
}