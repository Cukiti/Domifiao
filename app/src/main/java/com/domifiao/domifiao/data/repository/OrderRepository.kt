package com.domifiao.domifiao.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private val collectionName = "orders"

    suspend fun getOrder(orderId: String): DocumentSnapshot {
        return db.collection(collectionName).document(orderId).get().await()
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        db.collection(collectionName).document(orderId).update("status", status).await()
    }

    suspend fun getOrdersByClient(clientId: String): QuerySnapshot {
        return db.collection(collectionName)
            .whereEqualTo("clientId", clientId)
            .get()
            .await()
    }

    suspend fun getOrdersByColmado(colmadoId: String): QuerySnapshot {
        return db.collection(collectionName)
            .whereEqualTo("colmadoId", colmadoId)
            .get()
            .await()
    }
}