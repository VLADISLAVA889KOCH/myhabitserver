package com.example.plugins

import com.example.routes.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    routing {
        // Проверка связи
        get("/") {
            call.respondText("Сервер MyHabit работает!")
        }

        // Подключаем все маршруты
        authRouting()
        habitRoutes()
        statsRoutes()
        visionBoardRoutes()
        uploadRoutes()
        settingsRouting()

        // Исправленная раздача картинок для Ktor 2.x/3.x
        // Теперь картинка будет доступна по адресу: http://твой_ip:8080/uploads/имя_файла.jpg
        staticFiles("/uploads", File("uploads"))
    }
}
