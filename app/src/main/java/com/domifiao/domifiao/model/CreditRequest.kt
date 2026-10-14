package com.domifiao.domifiao.model

/**
 * Modelo para solicitudes de crédito/fiado
 */
data class CreditRequest(
    val id: String = "",
    val clientId: String = "",
    val clientName: String = "",
    val clientAddress: String = "",
    val clientPhone: String = "",
    val colmadoId: String = "",
    val colmadoName: String = "",
    val requestDate: Long = 0,
    val paymentDeadline: String = "", // Fecha límite propuesta por el cliente
    val status: String = "PENDING",   // PENDING, APPROVED, REJECTED
    val approvedLimit: Int = 0,       // El límite que pone el colmado al aprobar
    val isClientVerified: Boolean = false // Si el cliente tiene cédula verificada
) {

    companion object
}