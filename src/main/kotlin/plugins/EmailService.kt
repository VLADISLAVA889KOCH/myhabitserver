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
            put("mail.smtp.starttls.enable", "true") // Включаем STARTTLS
            put("mail.smtp.host", "smtp.mail.ru")
            put("mail.smtp.port", "587") // Меняем порт на 587

            // Эти настройки помогут пройти через фильтры Render
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.ssl.protocols", "TLSv1.2")

            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "20000")
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
            transport.connect("smtp.mail.ru", 587, username, password)
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