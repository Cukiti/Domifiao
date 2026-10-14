package com.domifiao.domifiao.utils

import android.util.Log
import com.domifiao.domifiao.BuildConfig
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig

/**
 * Helper para Firebase Remote Config
 *
 * Facilita el acceso a valores de configuración remota para:
 * - Feature flags (activar/desactivar funciones)
 * - Modo mantenimiento
 * - Actualizaciones forzadas
 * - Configuración dinámica sin actualizar la app
 *
 * Uso:
 * ```
 * if (RemoteConfigHelper.isMaintenanceMode()) {
 *     // Mostrar pantalla de mantenimiento
 * }
 * ```
 */
object RemoteConfigHelper {

    private const val TAG = "RemoteConfigHelper"

    // Claves de configuración
    private const val KEY_MAINTENANCE_MODE = "maintenance_mode"
    private const val KEY_MIN_APP_VERSION = "min_app_version"
    private const val KEY_FORCE_UPDATE = "force_update"
    private const val KEY_CHAT_ENABLED = "chat_enabled"
    private const val KEY_CREDIT_REQUESTS_ENABLED = "credit_requests_enabled"
    private const val KEY_DELIVERY_ENABLED = "delivery_enabled"
    private const val KEY_MAX_CREDIT_AMOUNT = "max_credit_amount"
    private const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"
    private const val KEY_UPDATE_MESSAGE = "update_message"

    private val remoteConfig = Firebase.remoteConfig

    /**
     * Obtiene el valor del Remote Config de forma sincrónica
     * Los valores se actualizan en background por DomifiaoApp
     */

    // ==========================================
    // MODO MANTENIMIENTO
    // ==========================================

    /**
     * Verifica si la app está en modo mantenimiento
     */
    fun isMaintenanceMode(): Boolean {
        return remoteConfig.getBoolean(KEY_MAINTENANCE_MODE)
    }

    /**
     * Obtiene el mensaje de mantenimiento
     */
    fun getMaintenanceMessage(): String {
        return remoteConfig.getString(KEY_MAINTENANCE_MESSAGE).ifEmpty {
            "La aplicación está en mantenimiento. Por favor, intenta más tarde."
        }
    }

    // ==========================================
    // ACTUALIZACIONES
    // ==========================================

    /**
     * Verifica si se requiere actualización forzada
     */
    fun isForceUpdateRequired(): Boolean {
        val minVersion = remoteConfig.getLong(KEY_MIN_APP_VERSION).toInt()
        val currentVersion = BuildConfig.VERSION_CODE

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Min version: $minVersion, Current version: $currentVersion")
        }

        return currentVersion < minVersion || remoteConfig.getBoolean(KEY_FORCE_UPDATE)
    }

    /**
     * Obtiene el mensaje de actualización
     */
    fun getUpdateMessage(): String {
        return remoteConfig.getString(KEY_UPDATE_MESSAGE).ifEmpty {
            "Hay una nueva versión disponible. Por favor, actualiza la aplicación."
        }
    }

    /**
     * Obtiene la versión mínima requerida
     */
    fun getMinRequiredVersion(): Int {
        return remoteConfig.getLong(KEY_MIN_APP_VERSION).toInt()
    }

    // ==========================================
    // FEATURE FLAGS
    // ==========================================

    /**
     * Verifica si el chat está habilitado
     */
    fun isChatEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_CHAT_ENABLED)
    }

    /**
     * Verifica si las solicitudes de crédito están habilitadas
     */
    fun isCreditRequestsEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_CREDIT_REQUESTS_ENABLED)
    }

    /**
     * Verifica si el sistema de delivery está habilitado
     */
    fun isDeliveryEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_DELIVERY_ENABLED)
    }

    // ==========================================
    // LÍMITES Y CONFIGURACIÓN
    // ==========================================

    /**
     * Obtiene el monto máximo de crédito permitido
     */
    fun getMaxCreditAmount(): Double {
        return remoteConfig.getDouble(KEY_MAX_CREDIT_AMOUNT)
    }

    // ==========================================
    // UTILIDADES
    // ==========================================

    /**
     * Fuerza un fetch de Remote Config
     * Útil para actualizar configuración inmediatamente
     *
     * @param onComplete Callback cuando se complete el fetch
     */
    fun fetchConfig(onComplete: (Boolean) -> Unit = {}) {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                val success = task.isSuccessful
                if (BuildConfig.DEBUG) {
                    if (success) {
                        Log.d(TAG, "Remote Config fetched successfully")
                    } else {
                        Log.e(TAG, "Remote Config fetch failed")
                    }
                }
                onComplete(success)
            }
    }

    /**
     * Obtiene un valor String custom de Remote Config
     */
    fun getString(key: String, defaultValue: String = ""): String {
        return remoteConfig.getString(key).ifEmpty { defaultValue }
    }

    /**
     * Obtiene un valor Boolean custom de Remote Config
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            remoteConfig.getBoolean(key)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Obtiene un valor Long custom de Remote Config
     */
    fun getLong(key: String, defaultValue: Long = 0): Long {
        return try {
            remoteConfig.getLong(key)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Obtiene un valor Double custom de Remote Config
     */
    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return try {
            remoteConfig.getDouble(key)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Obtiene todos los valores de Remote Config como Map
     */
    fun getAllValues(): Map<String, Any> {
        return remoteConfig.all.mapValues { entry ->
            entry.value.asString()
        }
    }

    /**
     * Verifica si la app está operativa (no en mantenimiento y versión correcta)
     */
    fun isAppOperational(): Boolean {
        return !isMaintenanceMode() && !isForceUpdateRequired()
    }
}
