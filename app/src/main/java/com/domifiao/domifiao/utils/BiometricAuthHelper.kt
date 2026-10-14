package com.domifiao.domifiao.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper para autenticación biométrica (huella digital, reconocimiento facial)
 *
 * Uso:
 * ```
 * val helper = BiometricAuthHelper(activity)
 * helper.authenticate(
 *     onSuccess = { /* Usuario autenticado */ },
 *     onError = { errorCode, message -> /* Error */ }
 * )
 * ```
 */
class BiometricAuthHelper(private val activity: FragmentActivity) {

    companion object {
        const val ERROR_NO_BIOMETRIC = 1
        const val ERROR_NO_HARDWARE = 2
        const val ERROR_HARDWARE_UNAVAILABLE = 3
        const val ERROR_NONE_ENROLLED = 4
        const val ERROR_SECURITY_UPDATE_REQUIRED = 5
        const val ERROR_UNSUPPORTED = 6
        const val ERROR_UNKNOWN = 7
    }

    /**
     * Verifica si la autenticación biométrica está disponible en el dispositivo
     *
     * @return Pair<Boolean, String> - (isAvailable, errorMessage)
     */
    fun isBiometricAvailable(): Pair<Boolean, String> {
        val biometricManager = BiometricManager.from(activity)

        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Pair(true, "")
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Pair(false, "Este dispositivo no tiene sensor biométrico")
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Pair(false, "Sensor biométrico no disponible actualmente")
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Pair(false, "No hay huella digital o reconocimiento facial configurado")
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Pair(false, "Se requiere actualización de seguridad")
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Pair(false, "Autenticación biométrica no soportada")
            }
            else -> {
                Pair(false, "Error desconocido al verificar biometría")
            }
        }
    }

    /**
     * Verifica si hay biometría disponible de forma simple
     */
    fun hasBiometric(): Boolean {
        return isBiometricAvailable().first
    }

    /**
     * Autentica al usuario usando biometría
     *
     * @param title Título del diálogo de autenticación
     * @param subtitle Subtítulo opcional
     * @param description Descripción opcional
     * @param negativeButtonText Texto del botón de cancelar
     * @param onSuccess Callback cuando la autenticación es exitosa
     * @param onError Callback cuando hay error (errorCode, errorMessage)
     * @param onFailed Callback cuando la autenticación falla pero puede reintentar
     */
    fun authenticate(
        title: String = "Verificación biométrica",
        subtitle: String = "Confirma tu identidad",
        description: String = "Usa tu huella digital o reconocimiento facial",
        negativeButtonText: String = "Cancelar",
        onSuccess: () -> Unit,
        onError: (Int, String) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    ) {
        // Verificar disponibilidad primero
        val (isAvailable, errorMessage) = isBiometricAvailable()
        if (!isAvailable) {
            onError(ERROR_NO_BIOMETRIC, errorMessage)
            return
        }

        // Configurar el prompt
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        // Ejecutor para callbacks
        val executor = ContextCompat.getMainExecutor(activity)

        // Callback de autenticación
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Errores no recuperables
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Autenticación exitosa
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Fallo pero puede reintentar (huella no reconocida)
                    onFailed()
                }
            }
        )

        // Mostrar el diálogo de autenticación
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Verifica si el dispositivo tiene capacidad biométrica fuerte (no solo patrón/PIN)
     */
    fun hasStrongBiometric(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

/**
 * Extension function para Context para verificar rápidamente si hay biometría
 */
fun Context.hasBiometricCapability(): Boolean {
    val biometricManager = BiometricManager.from(this)
    return biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
}
