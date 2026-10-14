package com.domifiao.domifiao.ui.screens.colmado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domifiao.domifiao.data.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ColmadoHomeUiState(
    val colmadoName: String = "Cargando...",
    val isOpen: Boolean = true,
    val todaySales: Int = 0,
    val pendingOrdersCount: Int = 0,
    val unreadNotificationsCount: Int = 0,
    val unreadMessagesCount: Int = 0,
    val pendingRequestsCount: Int = 0
)

@HiltViewModel
class ColmadoHomeViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColmadoHomeUiState())
    val uiState: StateFlow<ColmadoHomeUiState> = _uiState.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        setupListeners()
    }

    private fun setupListeners() {
        val colmadoId = sessionManager.getUserId()
        if (colmadoId.isNullOrEmpty()) return

        // 1. Escuchar cambios en el documento del colmado
        listeners.add(
            db.collection("colmados_requests").document(colmadoId)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && snapshot.exists()) {
                        _uiState.update {
                            it.copy(
                                colmadoName = snapshot.getString("name") ?: "Mi Colmado",
                                isOpen = snapshot.getBoolean("isOpen") ?: true
                            )
                        }
                    }
                }
        )

        // 2. Escuchar pedidos pendientes
        listeners.add(
            db.collection("orders")
                .whereEqualTo("colmadoId", colmadoId)
                .whereIn("status", listOf("PENDING", "PREPARING"))
                .addSnapshotListener { snapshot, _ ->
                    _uiState.update { it.copy(pendingOrdersCount = snapshot?.size() ?: 0) }
                }
        )

        // 3. Escuchar notificaciones no leídas (General)
        listeners.add(
            db.collection("colmados_requests").document(colmadoId).collection("notifications")
                .whereEqualTo("read", false)
                .addSnapshotListener { snapshot, _ ->
                    _uiState.update { it.copy(unreadNotificationsCount = snapshot?.size() ?: 0) }
                }
        )

        // 4. Escuchar notificaciones de mensajes no leídos
        listeners.add(
            db.collection("colmados_requests").document(colmadoId).collection("notifications")
                .whereEqualTo("read", false)
                .whereIn("type", listOf("MESSAGE", "DEBT_CHAT"))
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val uniqueConversations = snapshot.documents.mapNotNull { it.getString("targetId") }.toSet()
                        _uiState.update { it.copy(unreadMessagesCount = uniqueConversations.size) }
                    } else {
                        _uiState.update { it.copy(unreadMessagesCount = 0) }
                    }
                }
        )

        // 5. Escuchar solicitudes de fiado pendientes
        listeners.add(
            db.collection("credit_requests")
                .whereEqualTo("colmadoId", colmadoId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshot, _ ->
                    _uiState.update { it.copy(pendingRequestsCount = snapshot?.size() ?: 0) }
                }
        )

        // 6. Escuchar ventas de hoy (COMPLETED)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis

        listeners.add(
            db.collection("orders")
                .whereEqualTo("colmadoId", colmadoId)
                .whereEqualTo("status", "COMPLETED")
                .whereGreaterThanOrEqualTo("timestamp", startOfToday)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val total = snapshot.documents.sumOf { it.getLong("total")?.toInt() ?: 0 }
                        _uiState.update { it.copy(todaySales = total) }
                    }
                }
        )
    }

    fun toggleStoreStatus(onSuccess: (Boolean) -> Unit) {
        val colmadoId = sessionManager.getUserId()
        if (colmadoId.isNullOrEmpty()) return

        val newStatus = !_uiState.value.isOpen
        db.collection("colmados_requests").document(colmadoId).update("isOpen", newStatus)
            .addOnSuccessListener {
                onSuccess(newStatus)
            }
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach { it.remove() }
    }
}