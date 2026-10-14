package com.domifiao.domifiao.ui.screens.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationEvent(
    val title: String,
    val message: String,
    val route: String?,
    val type: String
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _accountDeletedEvent = MutableSharedFlow<Unit>()
    val accountDeletedEvent: SharedFlow<Unit> = _accountDeletedEvent.asSharedFlow()

    private val _notificationEvent = MutableSharedFlow<NotificationEvent>()
    val notificationEvent: SharedFlow<NotificationEvent> = _notificationEvent.asSharedFlow()

    private var accountListener: ListenerRegistration? = null
    private var notificationListener: ListenerRegistration? = null

    init {
        startListeners()
    }

    private fun startListeners() {
        val currentUserId = sessionManager.getUserId()
        val role = sessionManager.getRole()

        if (currentUserId != null && role != null) {
            val collectionPath = if (role == "COLMADO") "colmados_requests" else "users"

            // Account Status Listener
            accountListener = db.collection(collectionPath).document(currentUserId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && !snapshot.exists()) {
                        viewModelScope.launch {
                            _accountDeletedEvent.emit(Unit)
                        }
                    }
                }

            // Notification Listener
            val notifPath = "$collectionPath/$currentUserId/notifications"
            val appStartTime = System.currentTimeMillis()
            
            notificationListener = db.collection(notifPath)
                .whereEqualTo("read", false)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == DocumentChange.Type.ADDED) {
                            val data = change.document.data
                            val timestamp = data["timestamp"] as? Long ?: 0L
                            
                            if (timestamp > appStartTime) {
                                val title = data["title"] as? String ?: "Nueva notificacion"
                                val message = data["message"] as? String ?: ""
                                val type = data["type"] as? String ?: "INFO"
                                val targetId = data["targetId"] as? String ?: ""
                                
                                val route = when {
                                    role == "CLIENT" && (type == "ORDER_UPDATE" || type == "SUCCESS" || type == "NEW_ORDER") -> "client_order_detail/$targetId"
                                    role == "CLIENT" && (type == "MESSAGE" || type == "DEBT_CHAT") -> "client_chat_list"
                                    role == "CLIENT" && (type == "DEBT_UPDATE" || type == "CREDIT_APPROVED") -> "client_my_credits"
                                    role == "COLMADO" && type == "NEW_ORDER" -> "colmado_order_detail/$targetId"
                                    role == "COLMADO" && type == "CREDIT_REQUEST" -> "colmado_credit_requests"
                                    role == "COLMADO" && (type == "MESSAGE" || type == "DEBT_CHAT") -> "colmado_chat_list"
                                    else -> null
                                }
                                
                                viewModelScope.launch {
                                    _notificationEvent.emit(NotificationEvent(title, message, route, type))
                                }
                            }
                        }
                    }
                }
        }
    }

    fun updateOnlineStatus(isOnline: Boolean) {
        viewModelScope.launch {
            authRepository.updateOnlineStatus(isOnline)
        }
    }
    
    fun performLogout() {
        viewModelScope.launch {
            authRepository.signOut()
            sessionManager.clearSession()
        }
    }

    override fun onCleared() {
        super.onCleared()
        accountListener?.remove()
        notificationListener?.remove()
    }
}