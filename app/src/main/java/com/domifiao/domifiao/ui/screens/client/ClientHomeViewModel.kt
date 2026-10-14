package com.domifiao.domifiao.ui.screens.client

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domifiao.domifiao.model.Colmado
import com.domifiao.domifiao.model.LoyaltyAccount
import com.domifiao.domifiao.utils.LoyaltyManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientHomeUiState(
    val isLoading: Boolean = true,
    val isGpsLoading: Boolean = false,
    val userName: String = "Cliente",
    val userPhoto: String = "",
    val userMunicipality: String = "",
    val userProvince: String = "",
    val userLat: Double = 0.0,
    val userLng: Double = 0.0,
    val locationStatus: String = "Detectando ubicacion...",
    val colmadosList: List<Colmado> = emptyList(),
    val loyaltyAccountsMap: Map<String, LoyaltyAccount> = emptyMap(),
    val activeOrdersCount: Int = 0,
    val unreadChatsCount: Int = 0,
    val unreadNotificationsCount: Int = 0,
    val isApprovedDelivery: Boolean = false,
    val activeDeliveriesCount: Int = 0,
    val searchText: String = "",
    val showOnlyWithPoints: Boolean = false,
    val showLogoutConfirmDialog: Boolean = false
)

@HiltViewModel
class ClientHomeViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientHomeUiState())
    val uiState: StateFlow<ClientHomeUiState> = _uiState.asStateFlow()

    private var colmadosListener: ListenerRegistration? = null
    private var deliveryAppListener: ListenerRegistration? = null
    private var activeOrdersListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null

    init {
        loadUserData()
        setupListeners()
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            val municipality = doc.getString("municipality") ?: ""
            val province = doc.getString("province") ?: ""
            _uiState.update { it.copy(
                userName = doc.getString("name") ?: "Cliente",
                userPhoto = doc.getString("photoUrl") ?: "",
                userMunicipality = municipality,
                userProvince = province,
                userLat = doc.getDouble("latitude") ?: 0.0,
                userLng = doc.getDouble("longitude") ?: 0.0,
                locationStatus = if (municipality.isNotEmpty() && province.isNotEmpty()) "$municipality, $province" else "Ubicacion no configurada"
            )}
            loadColmados(municipality)
        }

        LoyaltyManager.getAllLoyaltyAccounts(userId = userId, onSuccess = { accounts ->
            _uiState.update { it.copy(loyaltyAccountsMap = accounts.associateBy { acc -> acc.colmadoId }) }
        })
    }

    private fun setupListeners() {
        val userId = auth.currentUser?.uid ?: return

        deliveryAppListener = db.collection("delivery_applications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "APPROVED")
            .addSnapshotListener { snapshot, _ ->
                _uiState.update { it.copy(isApprovedDelivery = snapshot != null && !snapshot.isEmpty) }
            }

        activeOrdersListener = db.collection("orders")
            .whereEqualTo("deliveryId", userId)
            .whereIn("status", listOf("DELIVERING", "WAITING_CLIENT_CONFIRMATION"))
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _uiState.update { it.copy(activeDeliveriesCount = snapshot.size()) }
                }
            }

        notificationsListener = db.collection("users").document(userId).collection("notifications")
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val count = snapshot.documents.filter { doc ->
                        doc.getString("type") != "DEBT_CHAT" && doc.getString("type") != "MESSAGE"
                    }.size
                    _uiState.update { it.copy(unreadNotificationsCount = count) }
                }
            }
    }

    private fun loadColmados(municipality: String) {
        colmadosListener?.remove()
        _uiState.update { it.copy(isLoading = true) }

        val query = if (municipality.isNotEmpty()) {
            db.collection("colmados_requests").whereEqualTo("status", "APPROVED").whereEqualTo("municipality", municipality)
        } else {
            db.collection("colmados_requests").whereEqualTo("status", "APPROVED")
        }

        colmadosListener = query.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        Colmado(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            address = doc.getString("address") ?: "",
                            phone = doc.getString("phone") ?: "",
                            photoUrl = doc.getString("photoUrl") ?: doc.getString("photoBase64") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            isOpen = doc.getBoolean("isOpen") ?: true,
                            rating = doc.getDouble("rating") ?: 0.0,
                            province = doc.getString("province") ?: "",
                            municipality = doc.getString("municipality") ?: "",
                            hasLoyaltyProgram = doc.getBoolean("hasLoyaltyProgram") ?: false
                        )
                    } catch (e: Exception) { null }
                }.sortedWith(compareByDescending<Colmado> { it.isOpen }.thenByDescending { it.rating })
                
                _uiState.update { it.copy(colmadosList = list, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSearchTextChanged(text: String) {
        _uiState.update { it.copy(searchText = text) }
    }

    fun toggleShowOnlyWithPoints() {
        _uiState.update { it.copy(showOnlyWithPoints = !it.showOnlyWithPoints) }
    }

    fun toggleLogoutDialog(show: Boolean) {
        _uiState.update { it.copy(showLogoutConfirmDialog = show) }
    }

    fun updateLocation(lat: Double, lng: Double, municipality: String, province: String) {
        val userId = auth.currentUser?.uid
        if (userId != null && municipality.isNotEmpty()) {
            _uiState.update { it.copy(
                userLat = lat, userLng = lng,
                userMunicipality = municipality, userProvince = province,
                locationStatus = "$municipality, $province"
            )}
            db.collection("users").document(userId).update(
                mapOf("latitude" to lat, "longitude" to lng, "municipality" to municipality, "province" to province)
            )
            loadColmados(municipality)
        }
    }

    fun setGpsLoading(loading: Boolean) {
        _uiState.update { it.copy(isGpsLoading = loading) }
    }

    fun setLocationStatus(status: String) {
        _uiState.update { it.copy(locationStatus = status) }
    }

    override fun onCleared() {
        super.onCleared()
        colmadosListener?.remove()
        deliveryAppListener?.remove()
        activeOrdersListener?.remove()
        notificationsListener?.remove()
    }
}