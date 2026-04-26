package com.example.plugins

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.*

object EmailService {
    private val username = "v_kochergina@internet.ru"
    private val password = "dvtiUonMFUmmHnQyD9sv"

    // Теперь функция возвращает true, если письмо ушло, и false, если адрес неверный
    fun sendCode(userEmail: String, code: String): Boolean {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", "smtp.mail.ru")
            put("mail.smtp.port", "587")
            put("mail.smtp.ssl.protocols", "TLSv1.2")
            put("mail.smtp.connectiontimeout", "5000") // Таймаут 5 сек
            put("mail.smtp.timeout", "5000")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
        })

        return try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail))
                subject = "Подтверждение регистрации в MyHabit"
                setText("Ваш код подтверждения: $code\nВведите его в приложении, чтобы активировать аккаунт.")
            }
            Transport.send(message)
            println("ПОЧТА: Письмо успешно отправлено на $userEmail")
            true
        } catch (e: Exception) {
            println("ОШИБКА ПОЧТЫ (возможно, адрес не существует): ${e.localizedMessage}")
            false
        }
    }
}