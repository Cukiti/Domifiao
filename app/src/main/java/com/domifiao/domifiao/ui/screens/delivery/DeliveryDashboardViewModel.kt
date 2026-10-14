package com.domifiao.domifiao.ui.screens.delivery

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DeliveryDashboardUiState(
    val orders: List<DeliveryOrder> = emptyList(),
    val isLoading: Boolean = true,
    val currentTab: Int = 0,
    val message: String? = null
)

@HiltViewModel
class DeliveryDashboardViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeliveryDashboardUiState())
    val uiState: StateFlow<DeliveryDashboardUiState> = _uiState.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        loadOrders()
    }

    fun setTab(tab: Int) {
        if (_uiState.value.currentTab != tab) {
            _uiState.update { it.copy(currentTab = tab, isLoading = true) }
            loadOrders()
        }
    }

    private fun loadOrders() {
        val userId = auth.currentUser?.uid ?: return
        val currentTab = _uiState.value.currentTab

        listenerRegistration?.remove()

        val ordersRef = db.collection("orders")
        val query = if (currentTab == 0) {
            ordersRef
                .whereEqualTo("status", "PREPARING")
                .whereEqualTo("deliveryId", null)
        } else {
            ordersRef
                .whereEqualTo("deliveryId", userId)
                .whereIn("status", listOf("DELIVERING", "PREPARING"))
        }

        listenerRegistration = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                _uiState.update { it.copy(isLoading = false, message = "ERROR_LOADING") }
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val ordersList = snapshot.documents.mapNotNull { doc ->
                    try {
                        val deliveryId = doc.getString("deliveryId")
                        if (currentTab == 0 && !deliveryId.isNullOrEmpty()) return@mapNotNull null

                        val clientId = doc.getString("clientId") ?: ""

                        DeliveryOrder(
                            id = doc.id,
                            colmadoName = doc.getString("colmadoName") ?: "Colmado",
                            customerName = doc.getString("customerName") ?: "Cliente",
                            customerId = clientId,
                            address = doc.getString("address") ?: "Sin dirección",
                            status = doc.getString("status") ?: "UNKNOWN",
                            total = doc.getLong("total")?.toInt() ?: 0,
                            lat = doc.getDouble("clientLat") ?: 0.0,
                            lng = doc.getDouble("clientLng") ?: 0.0,
                            assignedToMe = deliveryId == userId,
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            housePhotoUrl = null,
                            houseDescription = null
                        )
                    } catch (e: Exception) { null }
                }

                _uiState.update { 
                    it.copy(
                        orders = ordersList.sortedByDescending { order -> order.timestamp }, 
                        isLoading = false
                    ) 
                }

                // Fetch extra details for each order
                ordersList.forEach { order ->
                    if (order.customerId.isNotEmpty()) {
                        db.collection("users").document(order.customerId).get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc.exists()) {
                                    val photo = userDoc.getString("housePhotoUrl")
                                    val desc = userDoc.getString("houseDescription")

                                    _uiState.update { state ->
                                        state.copy(
                                            orders = state.orders.map {
                                                if (it.id == order.id) {
                                                    it.copy(
                                                        housePhotoUrl = photo,
                                                        houseDescription = desc ?: ""
                                                    )
                                                } else it
                                            }
                                        )
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    fun acceptOrder(orderId: String) {
        val userId = auth.currentUser?.uid ?: return
        val orderRef = db.collection("orders").document(orderId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(orderRef)
            val currentStatus = snapshot.getString("status")
            val currentDelivery = snapshot.getString("deliveryId")

            if (currentStatus == "PREPARING" && currentDelivery.isNullOrEmpty()) {
                transaction.update(orderRef, "deliveryId", userId)
                transaction.update(orderRef, "status", "DELIVERING")
            } else {
                throw FirebaseFirestoreException("Pedido ya tomado", FirebaseFirestoreException.Code.ABORTED)
            }
        }.addOnSuccessListener {
            _uiState.update { it.copy(message = "ORDER_ACCEPTED") }
            setTab(1)
        }.addOnFailureListener {
            _uiState.update { it.copy(message = "ORDER_TAKEN") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
