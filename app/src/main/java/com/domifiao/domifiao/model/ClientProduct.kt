package com.domifiao.domifiao.model

/**
 * Modelo de producto para el cliente.
 * Representa un producto del inventario de un colmado tal como lo ve el cliente.
 * Actualizado con soporte para el nuevo sistema de unidades (UNIDAD/LIBRA con detalle).
 */
data class ClientProduct(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,           // Precio por unidad o por libra
    val category: String = "",

    // Sistema de unidades (configurado por el colmado)
    val unitType: String = "UNIDAD",   // UNIDAD o LIBRA

    val photoUrl: String = "",
    val imageRes: Int = 0,

    // Cantidad en el carrito
    var quantityInCart: Double = 0.0,
    var quantityDisplay: String = "",

    // Nuevo: Modo de selección del cliente (UNIDAD, POR_LIBRA, AL_DETALLE)
    var selectionMode: String = "UNIDAD"
) {
    /**
     * Calcula el precio total según el modo de selección
     */
    fun calculateTotalPrice(): Double {
        return when (selectionMode) {
            "AL_DETALLE" -> quantityInCart  // En detalle, quantityInCart es el monto en pesos
            "POR_LIBRA" -> price * quantityInCart
            "UNIDAD" -> price * quantityInCart
            else -> price * quantityInCart
        }
    }

    /**
     * Formatea la unidad para mostrar según el tipo configurado por el colmado
     */
    fun formatUnitDisplay(): String {
        return when (unitType.uppercase()) {
            "UNIDAD" -> "Por Unidad"
            "LIBRA" -> "RD$ ${price.toInt()}/lb"
            else -> "Por Unidad"
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "price" to price,
            "category" to category,
            "unitType" to unitType,
            "quantity" to quantityInCart,
            "quantityDisplay" to quantityDisplay,
            "selectionMode" to selectionMode
        )
    }

    companion object
}