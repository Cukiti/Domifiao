package com.domifiao.domifiao.model

/**
 * Modelo de mensaje de chat.
 * Representa un mensaje individual en una conversación.
 */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",      // Quién lo envió (UID)
    val senderName: String = "",    // Nombre del remitente (para mostrar)
    val text: String = "",
    val timestamp: Long = 0,
    val isRead: Boolean = false,    // Si el mensaje fue leído
    val messageType: String = "TEXT", // TEXT, IMAGE, SYSTEM, LOTTIE
    
    // --- Novedades Triple A ---
    val replyToId: String? = null,    // ID del mensaje original si es una respuesta
    val replyToText: String? = null   // Extracto del texto al que se responde
) {

    companion object
}