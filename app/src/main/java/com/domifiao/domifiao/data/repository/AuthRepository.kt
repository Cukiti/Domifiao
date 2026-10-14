package com.domifiao.domifiao.data.repository

import com.domifiao.domifiao.data.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import android.util.Log

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    private val TAG = "AuthRepository"

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun updateOnlineStatus(isOnline: Boolean): Resource<Unit> {
        val userId = getCurrentUserId() ?: return Resource.Error("Usuario no autenticado")
        val updateData = hashMapOf<String, Any>(
            "isOnline" to isOnline,
            "lastSeen" to System.currentTimeMillis()
        )
        
        return try {
            // Intentar actualizar en la colección 'users'
            db.collection("users").document(userId).update(updateData).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            // Fallback a 'colmados_requests' si falla
            try {
                db.collection("colmados_requests").document(userId).update(updateData).await()
                Resource.Success(Unit)
            } catch (e2: Exception) {
                Log.e(TAG, "Error actualizando estado online: ${e2.message}")
                Resource.Error("No se pudo actualizar el estado de conexión")
            }
        }
    }

    suspend fun signOut() {
        try {
            updateOnlineStatus(false)
            auth.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar sesión: ${e.message}")
        }
    }
}