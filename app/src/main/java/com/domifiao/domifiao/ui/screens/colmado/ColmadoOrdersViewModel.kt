package com.domifiao.domifiao.ui.screens.colmado

import androidx.lifecycle.ViewModel
import com.domifiao.domifiao.data.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// Estados del Pedido
enum class OrderStatus {
    PENDING, WAITING_APPROVAL, PREPARING, DELIVERING, WAITING_CLIENT_CONFIRMATION, COMPLETED, CANCELLED
}

// Modelo para producto en orden
data class ColmadoOrderProduct(
    val name: String,
    val quantity: Double,
    val quantityDisplay: String,
    val price: Double
) {
    fun calculateTotal(): Double = price * quantity
    fun formatTotal(): String = "RD$ ${calculateTotal().toInt()}"
}

data class OrderSummary(
    val id: String,
    val clientId: String,
    val customerName: String,
    val address: String,
    val itemsSummary: String,
    val products: List<ColmadoOrderProduct>,
    val total: Int,
    val paymentMethod: String,
    val status: OrderStatus,
    val timestamp: Long,
    val deliveryId: String? = null,
    val isPickup: Boolean = false,
    val rawProducts: List<Map<String, Any>> = emptyList()
)

data class ColmadoOrdersUiState(
    val isLoading: Boolean = true,
    val activeOrders: List<OrderSummary> = emptyList(),
    val historyOrders: List<OrderSummary> = emptyList(),
    val colmadoName: String = "Colmado",
    val selectedTab: Int = 0,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ColmadoOrdersViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColmadoOrdersUiState())
    val uiState: StateFlow<ColmadoOrdersUiState> = _uiState.asStateFlow()

    private var ordersListener: ListenerRegistration? = null
    private val colmadoId: String = sessionManager.getUserId() ?: ""

    init {
        loadColmadoName()
        startListeningOrders()
    }

    private fun loadColmadoName() {
        if (colmadoId.isNotEmpty()) {
            db.collection("colmados_requests").document(colmadoId).get()
                .addOnSuccessListener { snapshot ->
                    _uiState.update { it.copy(colmadoName = snapshot.getString("name") ?: "Colmado") }
                }
        }
    }

    private fun startListeningOrders() {
        if (colmadoId.isEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        ordersListener = db.collection("orders")
            .whereEqualTo("colmadoId", colmadoId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val allOrders = snapshot.documents.mapNotNull { doc ->
                        try {
                            val st = doc.getString("status") ?: "PENDING"
                            val statusEnum = try { OrderStatus.valueOf(st) } catch(e: Exception) { OrderStatus.PENDING }
                            @Suppress("UNCHECKED_CAST")
                            val rawProducts = doc.get("products") as? List<Map<String, Any>> ?: emptyList()

                            // Parsear productos con precios
                            val productsList = rawProducts.map { prod ->
                                val rawPrice = prod["price"]
                                val price = when (rawPrice) {
                                    is Double -> rawPrice
                                    is Long -> rawPrice.toDouble()
                                    is Number -> rawPrice.toDouble()
                                    else -> 0.0
                                }
                                val rawQty = prod["quantity"]
                                val quantity = when (rawQty) {
                                    is Double -> rawQty
                                    is Long -> rawQty.toDouble()
                                    is Number -> rawQty.toDouble()
                                    else -> 0.0
                                }
                                ColmadoOrderProduct(
                                    name = prod["name"] as? String ?: "",
                                    quantity = quantity,
                                    quantityDisplay = prod["quantityDisplay"] as? String ?: quantity.toInt().toString(),
                                    price = price
                                )
                            }

                            // Parsear total
                            val rawTotal = doc.get("total")
                            val total = when (rawTotal) {
                                is Double -> rawTotal.toInt()
                                is Long -> rawTotal.toInt()
                                is Number -> rawTotal.toInt()
                                else -> 0
                            }

                            OrderSummary(
                                id = doc.id,
                                clientId = doc.getString("clientId") ?: "",
                                customerName = doc.getString("customerName") ?: "Cliente",
                                address = doc.getString("address") ?: "",
                                itemsSummary = doc.getString("itemsSummary") ?: "",
                                products = productsList,
                                total = total,
                                paymentMethod = doc.getString("paymentMethod") ?: "Efectivo",
                                status = statusEnum,
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                deliveryId = doc.getString("deliveryId"),
                                isPickup = doc.getBoolean("isPickup") ?: false,
                                rawProducts = rawProducts
                            )
                        } catch (e: Exception) { null }
                    }

                    val sortedOrders = allOrders.sortedByDescending { it.timestamp }

                    val active = sortedOrders.filter {
                        it.status != OrderStatus.COMPLETED && it.status != OrderStatus.CANCELLED
                    }

                    val history = sortedOrders.filter {
                        it.status == OrderStatus.COMPLETED || it.status == OrderStatus.CANCELLED
                    }

                    _uiState.update {
                        it.copy(
                            activeOrders = active,
                            historyOrders = history,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
    }

    fun onTabSelected(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun updateOrderStatus(order: OrderSummary, newStatus: OrderStatus) {
        val isFiado = order.paymentMethod.equals("Fiado", ignoreCase = true)

        if (newStatus == OrderStatus.COMPLETED && isFiado) {
            val batch = db.batch()
            val orderRef = db.collection("orders").document(order.id)
            batch.update(orderRef, "status", newStatus.name)

            db.collection("debts")
                .whereEqualTo("clientId", order.clientId)
                .whereEqualTo("colmadoId", colmadoId)
                .get()
                .addOnSuccessListener { docs ->
                    if (!docs.isEmpty) {
                        batch.update(docs.documents[0].reference, "amount", FieldValue.increment(order.total.toLong()))
                    }
                    batch.commit().addOnSuccessListener {
                        _uiState.update { it.copy(successMessage = "TOAST_CREDIT_ORDER_CLOSED") }
                    }.addOnFailureListener { e ->
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
        } else {
            // Cambio de estado normal
            db.collection("orders").document(order.id).update("status", newStatus.name)
                .addOnSuccessListener {
                    val successCode = when(newStatus) {
                        OrderStatus.PREPARING -> "STATUS_PREPARING"
                        OrderStatus.DELIVERING -> "STATUS_DELIVERING"
                        OrderStatus.CANCELLED -> "STATUS_CANCELLED"
                        else -> "TOAST_STATUS_UPDATED"
                    }
                    
                    // Notificar
                    val notifMsg = when(newStatus) {
                        OrderStatus.PREPARING -> "Preparando"
                        OrderStatus.DELIVERING -> "En Camino"
                        OrderStatus.CANCELLED -> "Cancelado"
                        else -> "Estado actualizado"
                    }
                    val notifData = hashMapOf(
                        "title" to "Actualización de Pedido",
                        "message" to notifMsg,
                        "type" to "ORDER_UPDATE",
                        "targetId" to order.id,
                        "timestamp" to System.currentTimeMillis(),
                        "read" to false
                    )
                    db.collection("users").document(order.clientId).collection("notifications").add(notifData)

                    _uiState.update { it.copy(successMessage = successCode) }
                }
                .addOnFailureListener { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        ordersListener?.remove()
    }
}
