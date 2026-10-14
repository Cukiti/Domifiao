package com.domifiao.domifiao.model

/**
 * Modelo de transacción de deuda.
 * Representa un movimiento en la libreta de fiado (cargo o abono).
 *
 * Tipos de transacción:
 * - "COMPRA" o "CARGO": El cliente compra algo a crédito (aumenta deuda)
 * - "ABONO" o "PAGO": El cliente paga parte de su deuda (disminuye deuda)
 */
data class DebtTransaction(
    val id: String = "",
    val colmadoId: String = "",
    val clientId: String = "",
    val type: String = "CARGO",     // "COMPRA"/"CARGO" o "ABONO"/"PAGO"
    val amount: Int = 0,
    val description: String = "",
    val timestamp: Long = 0L
)