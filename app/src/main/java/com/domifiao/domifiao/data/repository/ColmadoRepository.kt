package com.domifiao.domifiao.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColmadoRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private val collectionName = "colmados_requests"

    suspend fun getColmado(colmadoId: String): DocumentSnapshot {
        return db.collection(collectionName).document(colmadoId).get().await()
    }

    suspend fun updateColmado(colmadoId: String, data: Map<String, Any>) {
        db.collection(collectionName).document(colmadoId).update(data).await()
    }

    suspend fun getApprovedColmadosByMunicipality(municipality: String): QuerySnapshot {
        return db.collection(collectionName)
            .whereEqualTo("status", "APPROVED")
            .whereEqualTo("municipality", municipality)
            .get()
            .await()
    }

    suspend fun getAllApprovedColmados(): QuerySnapshot {
        return db.collection(collectionName)
            .whereEqualTo("status", "APPROVED")
            .get()
            .await()
    }
}