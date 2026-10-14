package com.domifiao.domifiao.utils

import android.content.Context
import android.util.Log
import com.domifiao.domifiao.model.LoyaltyAccount
import com.domifiao.domifiao.model.LoyaltyTier
import com.domifiao.domifiao.model.PointsTransaction
import com.domifiao.domifiao.model.TransactionType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * LoyaltyManager - Gestión del Sistema de Puntos POR COLMADO
 *
 * IMPORTANTE: Cada colmado decide si tiene programa de puntos activado.
 *
 * Responsabilidades:
 * - Verificar si colmado tiene programa activo
 * - Crear cuentas de lealtad para cliente-colmado
 * - Acumular puntos cuando se completan órdenes
 * - Calcular y actualizar tiers
 * - Gestionar canjes de puntos
 * - Enviar notificaciones de logros
 */
object LoyaltyManager {

    private const val TAG = "LoyaltyManager"
    private val db = FirebaseFirestore.getInstance()

    /**
     * Verifica si un colmado tiene activado el programa de puntos
     */
    fun isLoyaltyProgramActive(
        colmadoId: String,
        onResult: (Boolean) -> Unit
    ) {
        db.collection("colmados_requests").document(colmadoId).get()
            .addOnSuccessListener { doc ->
                val isActive = doc.getBoolean("hasLoyaltyProgram") ?: false
                onResult(isActive)
            }
            .addOnFailureListener {
                Log.e(TAG, "Error verificando programa de puntos: ${it.message}")
                onResult(false)
            }
    }

    /**
     * Acumula puntos cuando una orden se completa
     * 1 punto = 1 RD$ gastado
     *
     * IMPORTANTE: Solo acumula si el colmado tiene el programa activo
     *
     * @param userId ID del usuario (cliente)
     * @param colmadoId ID del colmado
     * @param colmadoName Nombre del colmado
     * @param orderId ID de la orden completada
     * @param orderTotal Total de la orden en RD$
     */
    fun awardPointsForOrder(
        userId: String,
        colmadoId: String,
        colmadoName: String,
        orderId: String,
        orderTotal: Double,
        onSuccess: (pointsAwarded: Int, newTier: LoyaltyTier?) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {}
    ) {
        // 1. Verificar si el colmado tiene programa activo
        isLoyaltyProgramActive(colmadoId) { isActive ->
            if (!isActive) {
                Log.d(TAG, "Colmado $colmadoName no tiene programa de puntos activo")
                return@isLoyaltyProgramActive
            }

            // 2. Proceder a acumular puntos
            val pointsToAward = orderTotal.toInt() // 1 punto = 1 RD$
            val accountId = "${userId}_${colmadoId}" // ID compuesto
            val accountRef = db.collection("loyalty_accounts").document(accountId)

        db.runTransaction<Map<String, Any>> { transaction ->
            val snapshot = transaction.get(accountRef)

            // Si no existe, crear cuenta nueva
            if (!snapshot.exists()) {
                val newTier = LoyaltyTier.fromPoints(pointsToAward)
                val newAccount = LoyaltyAccount(
                    userId = userId,
                    colmadoId = colmadoId,
                    colmadoName = colmadoName,
                    currentPoints = pointsToAward,
                    totalPointsEarned = pointsToAward,
                    tier = newTier,
                    tierProgress = LoyaltyTier.BRONCE.calculateProgress(pointsToAward),
                    ordersCompleted = 1,
                    totalSpent = orderTotal,
                    lastUpdated = System.currentTimeMillis()
                )
                transaction.set(accountRef, newAccount)
                return@runTransaction mapOf(
                    "points" to pointsToAward,
                    "oldTier" to "BRONCE",
                    "newTier" to newTier.name,
                    "colmadoName" to colmadoName
                )
            }

            // Actualizar cuenta existente
            val currentPoints = snapshot.getLong("currentPoints")?.toInt() ?: 0
            val totalPointsEarned = snapshot.getLong("totalPointsEarned")?.toInt() ?: 0
            val ordersCompleted = snapshot.getLong("ordersCompleted")?.toInt() ?: 0
            val totalSpent = snapshot.getDouble("totalSpent") ?: 0.0
            val oldTierName = snapshot.getString("tier") ?: "BRONCE"

            val newCurrentPoints = currentPoints + pointsToAward
            val newTotalPoints = totalPointsEarned + pointsToAward
            val newTier = LoyaltyTier.fromPoints(newTotalPoints)
            val newProgress = newTier.calculateProgress(newTotalPoints)

            val updates = hashMapOf<String, Any>(
                "currentPoints" to newCurrentPoints,
                "totalPointsEarned" to newTotalPoints,
                "tier" to newTier.name,
                "tierProgress" to newProgress,
                "ordersCompleted" to (ordersCompleted + 1),
                "totalSpent" to (totalSpent + orderTotal),
                "lastUpdated" to System.currentTimeMillis()
            )

            transaction.update(accountRef, updates)

            // Registrar transacción
            val pointsTransaction = PointsTransaction(
                userId = userId,
                orderId = orderId,
                points = pointsToAward,
                type = TransactionType.EARNED,
                description = "Orden completada - RD$ $orderTotal",
                timestamp = System.currentTimeMillis()
            )

            val transactionRef = db.collection("loyalty_accounts")
                .document(userId)
                .collection("transactions")
                .document()

            transaction.set(transactionRef, pointsTransaction)

            // Retornar info para procesar después
            mapOf(
                "points" to pointsToAward,
                "oldTier" to oldTierName,
                "newTier" to newTier.name
            )
        }
            .addOnSuccessListener { result ->
                val points = (result["points"] as? Int) ?: 0
                val oldTierName = result["oldTier"] as? String ?: "BRONCE"
                val newTierName = result["newTier"] as? String ?: "BRONCE"

                val oldTier = LoyaltyTier.valueOf(oldTierName)
                val newTier = LoyaltyTier.valueOf(newTierName)
                val tierUpgraded = newTier != oldTier

                Log.d(TAG, "Puntos acumulados: $points para usuario $userId")

                if (tierUpgraded) {
                    Log.d(TAG, "¡Tier upgrade! Nuevo tier: ${newTier.displayName}")
                    sendTierUpgradeNotification(userId, newTier, colmadoName)
                    onSuccess(points, newTier)
                } else {
                    onSuccess(points, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error acumulando puntos: ${e.message}")
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Canjea puntos por descuento
     *
     * @param userId ID del usuario
     * @param pointsToRedeem Puntos a canjear
     * @param discountAmount Descuento aplicado en RD$
     */
    fun redeemPoints(
        userId: String,
        orderId: String,
        pointsToRedeem: Int,
        discountAmount: Double,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val accountRef = db.collection("loyalty_accounts").document(userId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(accountRef)

            if (!snapshot.exists()) {
                throw Exception("Cuenta de lealtad no encontrada")
            }

            val currentPoints = snapshot.getLong("currentPoints")?.toInt() ?: 0

            if (currentPoints < pointsToRedeem) {
                throw Exception("Puntos insuficientes. Tienes $currentPoints puntos.")
            }

            val newCurrentPoints = currentPoints - pointsToRedeem

            transaction.update(accountRef, mapOf(
                "currentPoints" to newCurrentPoints,
                "lastUpdated" to System.currentTimeMillis()
            ))

            // Registrar transacción de canje
            val pointsTransaction = PointsTransaction(
                userId = userId,
                orderId = orderId,
                points = -pointsToRedeem,
                type = TransactionType.REDEEMED,
                description = "Descuento de RD$ $discountAmount",
                timestamp = System.currentTimeMillis()
            )

            val transactionRef = db.collection("loyalty_accounts")
                .document(userId)
                .collection("transactions")
                .document()

            transaction.set(transactionRef, pointsTransaction)
        }
            .addOnSuccessListener {
                Log.d(TAG, "Puntos canjeados: $pointsToRedeem para usuario $userId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error canjeando puntos: ${e.message}")
                onError(e.message ?: "Error desconocido")
            }
    }

    /**
     * Obtiene TODAS las cuentas de lealtad de un usuario
     * (Una cuenta por cada colmado con programa activo)
     */
    fun getAllLoyaltyAccounts(
        userId: String,
        onSuccess: (List<LoyaltyAccount>) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        db.collection("loyalty_accounts")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val accounts = snapshot.documents.mapNotNull { doc ->
                    try {
                        val tierName = doc.getString("tier") ?: "BRONCE"
                        LoyaltyAccount(
                            userId = doc.getString("userId") ?: userId,
                            colmadoId = doc.getString("colmadoId") ?: "",
                            colmadoName = doc.getString("colmadoName") ?: "Colmado",
                            currentPoints = doc.getLong("currentPoints")?.toInt() ?: 0,
                            totalPointsEarned = doc.getLong("totalPointsEarned")?.toInt() ?: 0,
                            tier = LoyaltyTier.valueOf(tierName),
                            tierProgress = doc.getDouble("tierProgress")?.toFloat() ?: 0f,
                            ordersCompleted = doc.getLong("ordersCompleted")?.toInt() ?: 0,
                            totalSpent = doc.getDouble("totalSpent") ?: 0.0,
                            lastUpdated = doc.getLong("lastUpdated") ?: 0L
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando cuenta: ${e.message}")
                        null
                    }
                }
                onSuccess(accounts.sortedByDescending { it.currentPoints })
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error obteniendo cuentas: ${e.message}")
                onError(e.message ?: "Error desconocido")
            }
    }

    /**
     * Obtiene la cuenta de lealtad de un usuario con un colmado específico
     */
    fun getLoyaltyAccount(
        userId: String,
        colmadoId: String,
        onSuccess: (LoyaltyAccount?) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val accountId = "${userId}_${colmadoId}"
        db.collection("loyalty_accounts").document(accountId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    try {
                        val tierName = doc.getString("tier") ?: "BRONCE"
                        val account = LoyaltyAccount(
                            userId = doc.getString("userId") ?: userId,
                            colmadoId = doc.getString("colmadoId") ?: colmadoId,
                            colmadoName = doc.getString("colmadoName") ?: "Colmado",
                            currentPoints = doc.getLong("currentPoints")?.toInt() ?: 0,
                            totalPointsEarned = doc.getLong("totalPointsEarned")?.toInt() ?: 0,
                            tier = LoyaltyTier.valueOf(tierName),
                            tierProgress = doc.getDouble("tierProgress")?.toFloat() ?: 0f,
                            ordersCompleted = doc.getLong("ordersCompleted")?.toInt() ?: 0,
                            totalSpent = doc.getDouble("totalSpent") ?: 0.0,
                            lastUpdated = doc.getLong("lastUpdated") ?: 0L
                        )
                        onSuccess(account)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando cuenta: ${e.message}")
                        onSuccess(null)
                    }
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error obteniendo cuenta: ${e.message}")
                onError(e.message ?: "Error desconocido")
            }
    }

    /**
     * Envía notificación cuando un usuario sube de tier
     */
    private fun sendTierUpgradeNotification(userId: String, newTier: LoyaltyTier, colmadoName: String) {
        val notification = hashMapOf(
            "title" to "¡Nuevo Nivel Desbloqueado! ${newTier.emoji}",
            "message" to "¡Felicidades! Has alcanzado el nivel ${newTier.displayName} en $colmadoName. Ahora tienes ${newTier.discountPercentage}% de descuento en tus pedidos.",
            "type" to "TIER_UPGRADE",
            "targetId" to newTier.name,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )

        db.collection("users").document(userId).collection("notifications")
            .add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Notificación de tier upgrade enviada para $colmadoName")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error enviando notificación: ${e.message}")
            }
    }

    /**
     * Calcula el descuento aplicable según el tier del usuario
     */
    fun calculateDiscount(subtotal: Double, tier: LoyaltyTier): Double {
        return subtotal * (tier.discountPercentage / 100.0)
    }
}
