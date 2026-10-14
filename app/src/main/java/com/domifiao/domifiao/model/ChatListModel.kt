package com.domifiao.domifiao.model

data class ChatConversation(
    val id: String, // ID del Colmado o del Pedido (según tu lógica de agrupación)
    val name: String, // Nombre del Colmado
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val photoUrl: String = ""
)