package com.domifiao.domifiao.utils

import android.content.Context
import android.widget.Toast
import com.domifiao.domifiao.model.CartManager
import com.domifiao.domifiao.model.ClientProduct
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Helper para la funcionalidad "Repetir Pedido"
 * Carga productos de una orden anterior y los agrega al carrito
 */
object RepeatOrderHelper {

    /**
     * Repite un pedido cargando sus productos al carrito
     *
     * @param orderId ID de la orden a repetir
     * @param onSuccess Callback cuando se completa exitosamente
     * @param onError Callback cuando hay un error
     */
    fun repeatOrder(
        context: Context,
        orderId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        // Cargar la orden
        db.collection("orders").document(orderId).get()
            .addOnSuccessListener { orderDoc ->
                if (!orderDoc.exists()) {
                    onError("Orden no encontrada")
                    return@addOnSuccessListener
                }

                val colmadoId = orderDoc.getString("colmadoId") ?: ""
                val colmadoName = orderDoc.getString("colmadoName") ?: ""

                if (colmadoId.isEmpty()) {
                    onError("Datos de colmado inválidos")
                    return@addOnSuccessListener
                }

                // Obtener productos de la orden
                @Suppress("UNCHECKED_CAST")
                val products = orderDoc.get("products") as? List<Map<String, Any>> ?: emptyList()

                if (products.isEmpty()) {
                    onError("Esta orden no tiene productos")
                    return@addOnSuccessListener
                }

                // Verificar si hay conflicto de colmado
                if (CartManager.hasColmadoConflict(colmadoId)) {
                    onError("CONFLICT:$colmadoId:$colmadoName")
                    return@addOnSuccessListener
                }

                var addedCount = 0
                var unavailableCount = 0
                var priceChangedCount = 0

                // Procesar cada producto
                products.forEach { productMap ->
                    val productId = productMap["id"] as? String ?: return@forEach
                    val productName = productMap["name"] as? String ?: "Producto"
                    val quantity = (productMap["quantity"] as? Number)?.toDouble() ?: 1.0
                    val quantityDisplay = productMap["quantityDisplay"] as? String ?: "1"
                    val oldPrice = (productMap["price"] as? Number)?.toDouble() ?: 0.0
                    val unitType = productMap["unitType"] as? String ?: "UNIDAD"
                    val photoUrl = productMap["photoUrl"] as? String ?: ""

                    // Verificar disponibilidad y precio actual en Firestore
                    db.collection("colmado_products").document(productId).get()
                        .addOnSuccessListener { productDoc ->
                            if (!productDoc.exists()) {
                                unavailableCount++
                                return@addOnSuccessListener
                            }

                            val isAvailable = productDoc.getBoolean("isAvailable") ?: false
                            if (!isAvailable) {
                                unavailableCount++
                                Toast.makeText(context, "$productName no está disponible", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            val currentPrice = productDoc.getDouble("price") ?: oldPrice

                            // Notificar si cambió el precio
                            if (currentPrice != oldPrice) {
                                priceChangedCount++
                                Toast.makeText(
                                    context,
                                    "$productName cambió de precio: RD$ $oldPrice → RD$ $currentPrice",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            // Crear producto para agregar al carrito
                            val clientProduct = ClientProduct(
                                id = productId,
                                name = productName,
                                price = currentPrice,
                                category = productDoc.getString("category") ?: "",
                                unitType = unitType,
                                photoUrl = photoUrl,
                                quantityInCart = quantity,
                                quantityDisplay = quantityDisplay,
                                selectionMode = when (unitType) {
                                    "LIBRA" -> "POR_LIBRA"
                                    else -> "UNIDAD"
                                }
                            )

                            // Agregar al carrito
                            CartManager.addProduct(
                                product = clientProduct,
                                colmadoId = colmadoId,
                                colmadoName = colmadoName,
                                quantity = quantity,
                                quantityDisplay = quantityDisplay,
                                selectionMode = clientProduct.selectionMode
                            )

                            addedCount++

                            // Verificar si terminamos de procesar todos
                            if (addedCount + unavailableCount >= products.size) {
                                // Mostrar resumen
                                val message = buildString {
                                    append("Pedido repetido: ")
                                    if (addedCount > 0) append("$addedCount productos agregados")
                                    if (unavailableCount > 0) append(", $unavailableCount no disponibles")
                                    if (priceChangedCount > 0) append(", $priceChangedCount con precio actualizado")
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                onSuccess()
                            }
                        }
                        .addOnFailureListener {
                            unavailableCount++
                            Toast.makeText(context, "Error verificando $productName", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                onError("Error cargando orden: ${e.message}")
            }
    }
}
