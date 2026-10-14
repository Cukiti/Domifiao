package com.domifiao.domifiao.services

import android.util.Log
import com.domifiao.domifiao.BuildConfig
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.utils.NotificationHandler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DomifiaoMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "DomifiaoFCM"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Solo loggear tokens en modo debug (removido en producción)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Nuevo FCM Token recibido")
        }

        // Guardar el token en Firestore
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Mensaje recibido")
        }

        // Obtener datos del mensaje
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "Domifiao"
        val body = data["body"] ?: message.notification?.body ?: ""
        val type = data["type"]
        val route = data["route"]
        data["targetId"]

        // Mostrar notificacion local
        val notificationHandler = NotificationHandler(applicationContext)
        notificationHandler.showNotification(
            title = title,
            message = body,
            route = route,
            type = type
        )

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Notificación procesada")
        }
    }

    private fun saveTokenToFirestore(token: String) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val sessionManager = SessionManager(applicationContext)

        val userId = auth.currentUser?.uid ?: sessionManager.getUserId()
        val userRole = sessionManager.getRole()

        if (userId != null) {
            val collection = when (userRole) {
                "colmado" -> "colmados_requests"
                "delivery" -> "delivery_applications"
                else -> "users"
            }

            db.collection(collection).document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Token FCM guardado")
                    }
                }
                .addOnFailureListener { e ->
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Error guardando token: ${e.message}")
                    }
                    // Intentar con set merge si el documento no existe
                    db.collection(collection).document(userId)
                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                }
        }
    }
}