package com.example.plugins

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.*

object EmailService {
    private val username = "v_kochergina@internet.ru"
    private val password = "CLQD8Qk33U19HRsdNfOG"

    fun sendCode(userEmail: String, code: String): Boolean {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", "smtp.mail.ru")
            put("mail.smtp.port", "465")
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.socketFactory.port", "465")
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")

            // Настройки таймаутов для стабильности на Render
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")
            put("mail.smtp.writetimeout", "15000")

            // Включаем дебаг, чтобы видеть процесс в логах Render
            put("mail.debug", "true")
        }

        // Создаем сессию
        val session = Session.getInstance(props)
        session.debug = true

        return try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail))
                subject = "Код подтверждения MyHabit"
                // Сделали текст чуть более формальным, чтобы не попасть в спам
                setText("Здравствуйте!\n\nВаш код подтверждения для приложения MyHabit: $code\n\nЕсли вы не запрашивали этот код, просто проигнорируйте письмо.")
            }

            // ЯВНОЕ подключение и отправка (решает проблему с пользователем root)
            val transport = session.getTransport("smtp")
            transport.connect("smtp.mail.ru", username, password)
            transport.sendMessage(message, message.allRecipients)
            transport.close()

            println("ПОЧТА: Письмо успешно отправлено на $userEmail")
            true
        } catch (e: Exception) {
            println("ОШИБКА ПОЧТЫ: ${e.localizedMessage}")
            e.printStackTrace() // Выведет подробности ошибки в логи
            false
        }
    }
}