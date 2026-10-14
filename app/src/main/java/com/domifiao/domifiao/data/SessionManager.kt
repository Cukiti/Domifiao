package com.domifiao.domifiao.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.domifiao.domifiao.BuildConfig

/**
 * SessionManager - Gestión de sesión local del usuario
 *
 * Almacena datos básicos de sesión en EncryptedSharedPreferences para:
 * - Mantener sesión activa entre reinicios de app
 * - Acceso rápido a datos del usuario sin consultar Firestore
 * - Determinar tipo de usuario (Cliente/Colmado)
 * - SEGURIDAD: Datos cifrados usando Android Keystore
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = try {
        // Crear MasterKey para cifrado (usa Android Keystore)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Crear EncryptedSharedPreferences
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback a SharedPreferences normal si falla el cifrado
        // (puede ocurrir en dispositivos antiguos sin Keystore)
        if (BuildConfig.DEBUG) {
            Log.e("SessionManager", "Error creando EncryptedSharedPreferences, usando fallback", e)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_NAME = "domifiao_prefs"

        // Claves principales
        private const val KEY_USER_ROLE = "user_role"      // "CLIENT" o "COLMADO"
        private const val KEY_USER_ID = "user_id"          // UID de Firebase o ID de documento
        private const val KEY_USER_NAME = "user_name"      // Nombre para mostrar
        private const val KEY_USER_PHOTO = "user_photo"    // Foto en Base64 o URL
        private const val KEY_LAST_LOGIN = "last_login"    // Timestamp del último login
        private const val KEY_LAST_ACTIVITY = "last_activity" // Timestamp de última actividad
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled" // Biometría activada
        private const val KEY_DARK_MODE = "dark_mode"      // Modo oscuro activado

        // Timeout de sesión (30 días para una experiencia "siempre conectado")
        private const val SESSION_TIMEOUT_MS = 30L * 24 * 60 * 60 * 1000L
    }

    // ==========================================
    // GUARDAR SESIÓN
    // ==========================================

    /**
     * Guarda la sesión al hacer Login o Registro.
     *
     * @param role Rol del usuario: "CLIENT" o "COLMADO"
     * @param id ID único del usuario (UID de Firebase para clientes, docId para colmados)
     * @param name Nombre para mostrar (opcional)
     * @param photo Foto en Base64 o URL (opcional)
     */
    fun saveSession(role: String, id: String, name: String = "", photo: String = "") {
        prefs.edit().apply {
            putString(KEY_USER_ROLE, role)
            putString(KEY_USER_ID, id)
            putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()) // Iniciar actividad

            if (name.isNotEmpty()) putString(KEY_USER_NAME, name)
            if (photo.isNotEmpty()) putString(KEY_USER_PHOTO, photo)

            apply()
        }
    }

    /**
     * Actualiza nombre y foto del usuario (útil tras editar perfil)
     */
    fun updateProfile(name: String = "", photo: String = "") {
        prefs.edit().apply {
            if (name.isNotEmpty()) putString(KEY_USER_NAME, name)
            if (photo.isNotEmpty()) putString(KEY_USER_PHOTO, photo)
            apply()
        }
    }

    // ==========================================
    // OBTENER DATOS
    // ==========================================

    /**
     * Obtiene el rol del usuario actual
     * @return "CLIENT", "COLMADO" o null si no hay sesión
     */
    fun getRole(): String? = prefs.getString(KEY_USER_ROLE, null)

    /**
     * Obtiene el ID del usuario actual
     * @return ID del usuario o null si no hay sesión
     */
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    /**
     * Obtiene el nombre del usuario para mostrar
     * @return Nombre del usuario o "Usuario" por defecto
     */
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Usuario") ?: "Usuario"

    /**
     * Obtiene la foto del usuario
     * @return Foto en Base64/URL o cadena vacía
     */
    fun getUserPhoto(): String = prefs.getString(KEY_USER_PHOTO, "") ?: ""

    // ==========================================
    // UTILIDADES
    // ==========================================

    /**
     * Verifica si hay una sesión activa
     */
    fun isLoggedIn(): Boolean = !getUserId().isNullOrEmpty()

    /**
     * Verifica si el usuario es cliente
     */
    fun isClient(): Boolean = getRole() == "CLIENT"

    /**
     * Verifica si el usuario es colmado
     */
    fun isColmado(): Boolean = getRole() == "COLMADO"

    /**
     * Cierra la sesión eliminando todos los datos guardados
     */
    fun clearSession() {
        prefs.edit().clear().apply()
    }

    // ==========================================
    // TIMEOUT DE SESIÓN
    // ==========================================

    /**
     * Actualiza el timestamp de última actividad
     * Llamar este método cada vez que el usuario interactúa con la app
     */
    fun updateLastActivity() {
        prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply()
    }

    /**
     * Verifica si la sesión ha expirado por inactividad
     * @return true si la sesión expiró, false en caso contrario
     */
    fun isSessionExpired(): Boolean {
        val lastActivity = prefs.getLong(KEY_LAST_ACTIVITY, 0)
        if (lastActivity == 0L) {
            // Primera vez o sin registro, actualizar y permitir
            updateLastActivity()
            return false
        }

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - lastActivity

        return elapsedTime > SESSION_TIMEOUT_MS
    }

    /**
     * Obtiene el tiempo restante de sesión en milisegundos
     * @return tiempo restante o 0 si ya expiró
     */
    fun getRemainingSessionTime(): Long {
        val lastActivity = prefs.getLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - lastActivity
        val remaining = SESSION_TIMEOUT_MS - elapsedTime

        return if (remaining > 0) remaining else 0
    }

    /**
     * Invalida la sesión si expiró
     * @return true si se cerró la sesión por timeout
     */
    fun checkAndInvalidateSession(): Boolean {
        if (isSessionExpired() && isLoggedIn()) {
            clearSession()
            return true
        }
        return false
    }

    // ==========================================
    // AUTENTICACIÓN BIOMÉTRICA
    // ==========================================

    /**
     * Habilita o deshabilita la autenticación biométrica
     */
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Verifica si la autenticación biométrica está habilitada
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Obtiene la fecha del último login
     * @return timestamp o 0 si no hay registro
     */
    fun getLastLoginTime(): Long {
        return prefs.getLong(KEY_LAST_LOGIN, 0)
    }

    // ==========================================
    // TEMA (MODO OSCURO)
    // ==========================================

    /**
     * Habilita o deshabilita el modo oscuro
     */
    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    /**
     * Verifica si el modo oscuro está habilitado
     */
    fun isDarkMode(systemDefault: Boolean): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, systemDefault)
    }
}