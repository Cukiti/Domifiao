package com.domifiao.domifiao.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Helper para manejar notificaciones y tokens FCM
 */
object FCMHelper {

    private const val TAG = "FCMHelper"

    /**
     * Registra el token FCM del dispositivo actual en Firestore
     */
    fun registerToken(userId: String, userRole: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.d(TAG, "Token FCM obtenido: $token")

            val db = FirebaseFirestore.getInstance()
            val collection = when (userRole.uppercase()) {
                "COLMADO" -> "colmados_requests"
                "DELIVERY" -> "delivery_applications"
                else -> "users"
            }

            db.collection(collection).document(userId)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Token FCM guardado para $userRole: $userId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error guardando token: ${e.message}")
                }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error obteniendo token FCM: ${e.message}")
        }
    }

    /**
     * Guarda una notificacion en Firestore para el destinatario
     * Esto permite que la app muestre notificaciones cuando esta abierta
     * y mantiene un historial de notificaciones
     */
    fun saveNotification(
        recipientId: String,
        recipientRole: String,
        title: String,
        message: String,
        type: String,
        targetId: String = "",
        route: String = ""
    ) {
        val db = FirebaseFirestore.getInstance()
        val collection = when (recipientRole.uppercase()) {
            "COLMADO" -> "colmados_requests"
            else -> "users"
        }

        val notificationData = hashMapOf(
            "title" to title,
            "message" to message,
            "type" to type,
            "targetId" to targetId,
            "route" to route,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )

        db.collection(collection).document(recipientId)
            .collection("notifications")
            .add(notificationData)
            .addOnSuccessListener {
                Log.d(TAG, "Notificacion guardada para $recipientRole: $recipientId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error guardando notificacion: ${e.message}")
            }
    }

    /**
     * Notifica a un colmado sobre un nuevo pedido
     */
    fun notifyNewOrder(
        colmadoId: String,
        orderId: String,
        customerName: String,
        total: Int,
        paymentMethod: String
    ) {
        saveNotification(
            recipientId = colmadoId,
            recipientRole = "colmado",
            title = "Nuevo Pedido ($paymentMethod)",
            message = "Pedido de $customerName por RD$ $total",
            type = "NEW_ORDER",
            targetId = orderId,
            route = "colmado_order_detail/$orderId"
        )
    }

    /**
     * Notifica a un cliente sobre actualizacion de pedido
     */
    fun notifyOrderUpdate(
        clientId: String,
        orderId: String,
        status: String,
        colmadoName: String
    ) {
        val (title, message) = when (status) {
            "PREPARING" -> "Pedido Aceptado" to "$colmadoName esta preparando tu pedido"
            "DELIVERING" -> "Pedido en Camino" to "Tu pedido de $colmadoName va en camino"
            "COMPLETED" -> "Pedido Entregado" to "Tu pedido de $colmadoName fue entregado"
            "CANCELLED" -> "Pedido Cancelado" to "$colmadoName cancelo tu pedido"
            "WAITING_APPROVAL" -> "Revision Requerida" to "$colmadoName modifico el precio, revisa y confirma"
            else -> "Actualizacion de Pedido" to "Tu pedido de $colmadoName fue actualizado"
        }

        saveNotification(
            recipientId = clientId,
            recipientRole = "client",
            title = title,
            message = message,
            type = "ORDER_UPDATE",
            targetId = orderId,
            route = "client_order_detail/$orderId"
        )
    }

    /**
     * Notifica a un colmado sobre solicitud de credito
     */
    fun notifyCreditRequest(
        colmadoId: String,
        requestId: String,
        clientName: String,
        amount: Int
    ) {
        saveNotification(
            recipientId = colmadoId,
            recipientRole = "colmado",
            title = "Nueva Solicitud de Fiado",
            message = "$clientName solicita credito por RD$ $amount",
            type = "CREDIT_REQUEST",
            targetId = requestId,
            route = "colmado_credit_requests"
        )
    }

    /**
     * Notifica a un cliente sobre respuesta a solicitud de credito
     */
    fun notifyCreditResponse(
        clientId: String,
        colmadoName: String,
        approved: Boolean,
        amount: Int
    ) {
        val title = if (approved) "Credito Aprobado" else "Credito Rechazado"
        val message = if (approved) {
            "$colmadoName aprobo tu credito de RD$ $amount"
        } else {
            "$colmadoName rechazo tu solicitud de credito"
        }

        saveNotification(
            recipientId = clientId,
            recipientRole = "client",
            title = title,
            message = message,
            type = if (approved) "CREDIT_APPROVED" else "CREDIT_REJECTED",
            targetId = "",
            route = "client_my_credits"
        )
    }

    /**
     * Notifica sobre nuevo mensaje de chat
     */
    fun notifyNewMessage(
        recipientId: String,
        recipientRole: String,
        senderName: String,
        chatId: String
    ) {
        saveNotification(
            recipientId = recipientId,
            recipientRole = recipientRole,
            title = "Nuevo Mensaje",
            message = "$senderName te envio un mensaje",
            type = "MESSAGE",
            targetId = chatId,
            route = "chat/$chatId/direct"
        )
    }
}