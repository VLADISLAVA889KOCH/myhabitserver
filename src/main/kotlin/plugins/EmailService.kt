package com.example.plugins

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.*

object EmailService {
    private val username = "v_kochergina@internet.ru"
    private val password = "GfptdLI0R6i9QnvlzJSq"

    fun sendCode(userEmail: String, code: String): Boolean {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", "smtp.mail.ru")
            put("mail.smtp.port", "465")
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.socketFactory.port", "465")
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")


            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")

            put("mail.debug", "true") // Оставь пока для проверки
        }

        // Создаем сессию БЕЗ аутентификатора внутри
        val session = Session.getInstance(props)
        session.debug = true

        return try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail))
                subject = "Код подтверждения для доступа в MyHabit"
                setText("Ваш код подтверждения: $code")
            }

            // ЯВНО указываем транспорт и данные для входа
            val transport = session.getTransport("smtp")
            transport.connect("smtp.mail.ru", username, password) // Вот тут мы передаем данные принудительно
            transport.sendMessage(message, message.allRecipients)
            transport.close()

            println("ПОЧТА: Успешно!")
            true
        } catch (e: Exception) {
            println("ОШИБКА: ${e.localizedMessage}")
            false
        }
    }
}