package com.domifiao.domifiao.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

object IdentityVerifier {

    /**
     * PASO 1: Validación Matemática (Algoritmo de Luhn)
     * Verifica que el número ingresado sea real antes de gastar tiempo en la IA.
     */
    fun isCedulaValid(cedula: String): Boolean {
        val cleanCedula = cedula.replace("-", "").replace(" ", "")
        if (cleanCedula.length != 11 || !cleanCedula.all { it.isDigit() }) return false

        var sum = 0
        val digits = cleanCedula.map { it.toString().toInt() }.toIntArray()

        for (i in 0 until 10) {
            var digit = digits[i]
            val weight = if (i % 2 == 0) 1 else 2
            digit *= weight
            if (digit > 9) digit = (digit % 10) + 1
            sum += digit
        }

        val checkingDigit = (10 - (sum % 10)) % 10
        return checkingDigit == digits[10]
    }

    /**
     * PASO 2: Comparación Visual (IA)
     * - NO revisa fechas de vencimiento.
     * - SOLO busca que el Nombre y la Cédula ingresados existan en el plástico.
     */
    suspend fun verifyImageContent(
        context: Context,
        imageUri: Uri,
        expectedCedula: String,
        expectedName: String
    ): VerificationResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            // 1. Extraer todo el texto de la imagen
            val result = recognizer.process(image).await()
            val rawText = result.text.uppercase(Locale.getDefault()) // Convertir a mayúsculas

            // Limpiamos el texto escaneado para facilitar la búsqueda (quitamos guiones y espacios extra)
            val cleanScannedText = rawText.replace("-", "").replace(" ", "")

            // 2. Buscar la Cédula (Sin guiones)
            val targetCedula = expectedCedula.replace("-", "").replace(" ", "")
            val cedulaFound = cleanScannedText.contains(targetCedula)

            // 3. Buscar el Nombre
            // Dividimos el nombre esperado en partes (Ej: "Juan Perez" -> ["JUAN", "PEREZ"])
            // Ignoramos nombres muy cortos (de 1 o 2 letras) para evitar falsos positivos
            val nameParts = expectedName.uppercase().split(" ").filter { it.length > 2 }

            var matchesFound = 0
            for (part in nameParts) {
                if (rawText.contains(part)) {
                    matchesFound++
                }
            }

            // Reglas de Aprobación:
            // - Cédula OBLIGATORIA
            // - Al menos 1 nombre o apellido debe coincidir (para tolerar segundos nombres no impresos o errores de OCR)
            val nameIsAcceptable = matchesFound >= 1

            if (cedulaFound && nameIsAcceptable) {
                VerificationResult.Verified // ¡ÉXITO! Datos coinciden.
            } else if (cedulaFound) {
                // La cédula está, pero el nombre no se leyó bien (quizás borroso o mal escrito)
                VerificationResult.Partial("Cédula correcta, pero no pudimos leer bien el nombre.")
            } else if (nameIsAcceptable) {
                // El nombre está, pero la cédula no se ve
                VerificationResult.Failed("No encontramos el número de cédula en la foto.")
            } else {
                VerificationResult.Failed("Los datos de la foto no coinciden con lo que escribiste.")
            }

        } catch (e: Exception) {
            Log.e("IdentityVerifier", "Error OCR", e)
            VerificationResult.Error("No se pudo analizar la imagen. Intenta con mejor luz.")
        }
    }

    sealed class VerificationResult {
        object Verified : VerificationResult()
        data class Partial(val message: String) : VerificationResult()
        data class Failed(val message: String) : VerificationResult()
        data class Error(val message: String) : VerificationResult()
    }

    /**
     * Resultado de verificacion de cedula para recuperacion de cuenta
     */
    data class CedulaVerificationResult(
        val isValid: Boolean,
        val cedulaMatch: Boolean,
        val message: String = ""
    )

    /**
     * Verifica que el numero de cedula en una imagen coincida con el esperado.
     * Usa ML Kit para OCR y compara el numero encontrado.
     *
     * @param imageBase64 Imagen en formato Base64
     * @param expectedCedula Numero de cedula esperado (11 digitos)
     * @return CedulaVerificationResult con el resultado
     */
    suspend fun verifyCedulaNumber(imageBase64: String, expectedCedula: String): CedulaVerificationResult {
        return try {
            // Decodificar Base64 a Bitmap
            val imageBytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return CedulaVerificationResult(false, false, "No se pudo decodificar la imagen")

            // Crear InputImage desde Bitmap
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            // Procesar imagen con OCR
            val result = recognizer.process(image).await()
            val rawText = result.text.uppercase(Locale.getDefault())

            // Limpiar texto escaneado
            val cleanScannedText = rawText.replace("-", "").replace(" ", "").replace(".", "")

            // Limpiar cedula esperada
            val targetCedula = expectedCedula.replace("-", "").replace(" ", "")

            // Buscar la cedula en el texto
            val cedulaFound = cleanScannedText.contains(targetCedula)

            // Verificar que parece ser una cedula dominicana (buscar patrones)
            val hasCedulaPattern = rawText.contains("CEDULA") ||
                    rawText.contains("DOMINICANA") ||
                    rawText.contains("ELECTORAL") ||
                    rawText.contains("REPUBLICA")

            when {
                cedulaFound -> CedulaVerificationResult(
                    isValid = true,
                    cedulaMatch = true,
                    message = "Cedula verificada correctamente"
                )
                hasCedulaPattern -> CedulaVerificationResult(
                    isValid = true,
                    cedulaMatch = false,
                    message = "Se detecto una cedula pero el numero no coincide"
                )
                else -> CedulaVerificationResult(
                    isValid = false,
                    cedulaMatch = false,
                    message = "No se pudo leer la cedula. Intenta con mejor luz."
                )
            }

        } catch (e: Exception) {
            Log.e("IdentityVerifier", "Error verificando cedula", e)
            CedulaVerificationResult(
                isValid = false,
                cedulaMatch = false,
                message = "Error al analizar la imagen: ${e.message}"
            )
        }
    }
}