package com.domifiao.domifiao

import android.app.Application
import android.util.Log
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.utils.FCMHelper
import com.domifiao.domifiao.utils.NotificationHandler
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.mapbox.common.MapboxOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DomifiaoApp : Application() {

    companion object {
        private const val TAG = "DomifiaoApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Inicializar Mapbox con token seguro desde BuildConfig
        initializeMapbox()

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        // Inicializar App Check (IMPORTANTE: antes de usar Firebase)
        initializeAppCheck()

        // Inicializar Crashlytics
        initializeCrashlytics()

        // Inicializar Remote Config
        initializeRemoteConfig()

        // Crear canales de notificacion
        NotificationHandler(this)

        // Registrar token FCM si hay sesion activa
        registerFcmTokenIfLoggedIn()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DomifiaoApp inicializada")
        }
    }

    private fun initializeMapbox() {
        val mapboxToken = BuildConfig.MAPBOX_ACCESS_TOKEN
        if (mapboxToken.isNotBlank() && mapboxToken != "TOKEN_WILL_BE_LOADED_FROM_BUILDCONFIG") {
            MapboxOptions.accessToken = mapboxToken
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Mapbox inicializado correctamente")
            }
        } else {
            Log.e(TAG, "ERROR: MAPBOX_ACCESS_TOKEN no configurado en local.properties")
        }
    }

    private fun initializeAppCheck() {
        try {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            val providerFactory = if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            firebaseAppCheck.installAppCheckProviderFactory(providerFactory)
            
            val providerName = if (BuildConfig.DEBUG) "DebugAppCheckProviderFactory" else "PlayIntegrityAppCheckProviderFactory"
            Log.d(TAG, "App Check: $providerName activado")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando App Check: ${e.message}")
        }
    }

    private fun initializeCrashlytics() {
        val crashlytics = FirebaseCrashlytics.getInstance()

        // Habilitar Crashlytics solo en release
        crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Crashlytics: Deshabilitado en DEBUG")
        }
    }

    private fun initializeRemoteConfig() {
        val remoteConfig = Firebase.remoteConfig

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) {
                0 // Sin caché en debug para pruebas
            } else {
                3600 // 1 hora en producción
            }
        }

        remoteConfig.setConfigSettingsAsync(configSettings)

        // Valores por defecto
        val defaults = mapOf(
            "maintenance_mode" to false,
            "min_app_version" to 9,
            "force_update" to false,
            "chat_enabled" to true,
            "credit_requests_enabled" to true
        )

        remoteConfig.setDefaultsAsync(defaults)

        // Fetch inicial
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Remote Config actualizado")
                }
            }
            .addOnFailureListener { e ->
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error Remote Config: ${e.message}")
                }
            }
    }

    private fun registerFcmTokenIfLoggedIn() {
        val sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId()
        val userRole = sessionManager.getRole()

        if (userId != null && userRole != null) {
            FCMHelper.registerToken(userId, userRole)
        }

        // Suscribirse a topic general (opcional)
        FirebaseMessaging.getInstance().subscribeToTopic("domifiao_all")
            .addOnSuccessListener {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Suscrito a topic domifiao_all")
                }
            }
            .addOnFailureListener { e ->
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error suscribiendo a topic: ${e.message}")
                }
            }
    }
}