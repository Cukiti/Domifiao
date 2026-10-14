package com.domifiao.domifiao.model

import com.google.firebase.Timestamp

/**
 * Producto por defecto que se inyecta automáticamente a todos los colmados.
 * Los colmados pueden modificar el precio y disponibilidad en su inventario local.
 *
 * @property id ID del producto (auto-generado por Firestore)
 * @property name Nombre del producto
 * @property description Descripción adicional del producto
 * @property category Categoría del producto (Víveres, Bebidas, etc.)
 * @property suggestedPrice Precio sugerido en RD$ que los colmados pueden modificar
 * @property unitType Tipo de unidad (UNIDAD, LIBRA, PRECIO, etc.)
 * @property photoUrl URL de la foto del producto en Firebase Storage
 * @property barcode Código de barras (opcional)
 * @property isActive Estado del producto (permite soft delete)
 * @property isVisible Si el producto está visible en los colmados (ocultar/mostrar)
 * @property createdAt Fecha de creación
 * @property updatedAt Fecha de última actualización
 * @property createdBy ID del admin que creó el producto
 */
data class DefaultProduct(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val suggestedPrice: Double = 0.0,
    val unitType: String = "UNIDAD",
    val photoUrl: String = "",
    val barcode: String = "",
    val isActive: Boolean = true,
    val isVisible: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val createdBy: String = ""
) {
    companion object {
        /**
         * Categorías disponibles para productos
         */
        val CATEGORIES = listOf(
            "Víveres",
            "Bebidas",
            "Alcohol",
            "Limpieza",
            "Embutidos",
            "Lácteos",
            "Snacks",
            "Cigarrillos",
            "Higiene",
            "Otros"
        )

        /**
         * Tipos de unidad disponibles
         */
        val UNIT_TYPES = listOf(
            "UNIDAD",
            "LIBRA",
            "PAQUETE"
        )
    }

    /**
     * Convierte este producto por defecto a un producto de inventario de colmado.
     *
     * El producto se inyecta con:
     * - Precio sugerido copiado (el colmado puede modificarlo después)
     * - isAvailable = false (el colmado debe activarlo manualmente)
     * - stock = 0 (el colmado debe establecer su stock)
     * - Marca isFromDefault = true para rastreabilidad
     *
     * @param colmadoId ID del colmado al que se le inyecta el producto
     * @return Map con los datos del producto para el inventario del colmado
     */
    fun toColmadoInventoryProduct(colmadoId: String): Map<String, Any> {
        return hashMapOf(
            "name" to name,
            "description" to description,
            "category" to category,
            "price" to suggestedPrice,
            "unitType" to unitType,
            "photoUrl" to photoUrl,
            "barcode" to barcode,
            "searchName" to name.trim().lowercase(),
            "stock" to 0,
            "isAvailable" to true,  // Activado por defecto para visibilidad inmediata
            "isSelling" to true,
            "isFromDefault" to true,  // Marca que viene de productos por defecto
            "defaultProductId" to id,  // Referencia al producto original
            "isVisibleFromAdmin" to isVisible,  // Visibilidad controlada por admin
            "colmadoId" to colmadoId,
            "createdAt" to System.currentTimeMillis()
        )
    }
}
