
package com.example.plugins

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import org.json.JSONObject

object EmailService {
    // ВСТАВЬ СЮДА ТОКЕН, КОТОРЫЙ ПОЛУЧИШЬ В ШАГЕ API
    private val apiToken = "mlsn.cb5dd2d1206656a63d1113f1acf376b5a456748dd96dec3df51fc12e6d621c8c"
    private val senderEmail = "MS_XXXXXX@trial-XXXXX.mlsend.com" // MailerSend даст тебе тестовый email отправителя

    fun sendCode(userEmail: String, code: String): Boolean {
        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()

        val jsonBody = JSONObject().apply {
            put("from", JSONObject().apply {
                put("email", senderEmail)
            })
            put("to", org.json.JSONArray().put(JSONObject().apply {
                put("email", userEmail)
            }))
            put("subject", "Код подтверждения MyHabit")
            put("text", "Ваш код подтверждения: $code")
        }

        val request = Request.Builder()
            .url("https://api.mailersend.com/v1/email")
            .post(jsonBody.toString().toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $apiToken")
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    println("ПОЧТА (MailerSend): Успешно!")
                    true
                } else {
                    println("ОШИБКА (MailerSend): ${response.code} $responseBody")
                    false
                }
            }
        } catch (e: Exception) {
            println("ОШИБКА СЕТИ: ${e.localizedMessage}")
            false
        }
    }
}