package com.domifiao.domifiao.services

import android.app.Application
import android.util.Log
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecaptchaService @Inject constructor(
    private val application: Application
) {
    private var recaptchaClient: RecaptchaClient? = null
    private val siteKey = "6Lel2XIsAAAAAIF_ah78CjKmmyv__z2Ixcv3W_N1"
    private val validationUrl = "https://validaterecaptcha-lhpptkpzga-uc.a.run.app"

    private suspend fun getClient(): RecaptchaClient? = withContext(Dispatchers.IO) {
        if (recaptchaClient != null) return@withContext recaptchaClient

        return@withContext try {
            // Asumiendo que fetchClient devuelve el cliente directamente o lanza excepcion
            val client = Recaptcha.fetchClient(application, siteKey)
            recaptchaClient = client
            Log.d("RecaptchaService", "Cliente inicializado correctamente")
            client
        } catch (e: Exception) {
            Log.e("RecaptchaService", "Error al inicializar cliente: ${e.message}")
            null
        }
    }

    suspend fun validateAction(actionName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Timeout agresivo de 3 segundos para reCAPTCHA
            kotlinx.coroutines.withTimeout(3000) {
                val client = getClient() ?: return@withTimeout true // Si no hay cliente, permitimos (error técnico)
                
                val token = client.execute(RecaptchaAction.custom(actionName)).getOrNull()
                if (token == null) {
                    Log.e("RecaptchaService", "Token nulo para $actionName")
                    return@withTimeout true 
                }
                
                // Validar con el backend (Firebase Function)
                val isValid = validateWithBackend(token, actionName)
                if (!isValid) {
                    Log.w("RecaptchaService", "BOT DETECTADO para acción: $actionName")
                }
                isValid
            }
        } catch (e: Exception) {
            Log.e("RecaptchaService", "Error o Timeout en reCAPTCHA ($actionName): ${e.message}")
            true // En caso de error de red/timeout, dejamos pasar para no bloquear usuarios
        }
    }

    private fun validateWithBackend(token: String, action: String): Boolean {
        return try {
            val url = URL(validationUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val jsonInput = JSONObject().apply {
                put("token", token)
                put("action", action)
            }

            conn.outputStream.use { os ->
                val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseString = conn.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(responseString)
                val success = responseJson.optBoolean("success", false)
                val score = responseJson.optDouble("score", 0.0)
                Log.w("RecaptchaService", "Validacion backend finalizada. Score recibido: $score")
                success
            } else {
                Log.e("RecaptchaService", "Error en servidor backend: $responseCode")
                true // Fallback para no bloquear
            }
        } catch (e: Exception) {
            Log.e("RecaptchaService", "Error llamando al backend: ${e.message}")
            true // Fallback
        }
    }
}
