package com.domifiao.domifiao.model

/**
 * LoyaltyAccount - Sistema de Puntos y Recompensas POR COLMADO
 *
 * IMPORTANTE: Cada colmado tiene su propio programa de puntos independiente.
 * Un cliente puede tener múltiples LoyaltyAccounts (una por cada colmado).
 *
 * Reglas:
 * - 1 punto = 1 RD$ gastado
 * - Tiers basados en puntos acumulados totales
 * - Beneficios incrementales por tier
 * - Cada colmado decide si activa su programa de puntos
 */
data class LoyaltyAccount(
    val userId: String = "",
    val colmadoId: String = "",          // ID del colmado que ofrece este programa
    val colmadoName: String = "",        // Nombre del colmado
    val currentPoints: Int = 0,          // Puntos disponibles para canjear
    val totalPointsEarned: Int = 0,      // Puntos totales ganados (determina tier)
    val tier: LoyaltyTier = LoyaltyTier.BRONCE,
    val tierProgress: Float = 0f,        // Progreso al siguiente tier (0.0 - 1.0)
    val ordersCompleted: Int = 0,
    val totalSpent: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Tiers del programa de lealtad
 */
enum class LoyaltyTier(
    val displayName: String,
    val requiredPoints: Int,
    val discountPercentage: Int,
    val color: Long,
    val emoji: String
) {
    BRONCE(
        displayName = "Bronce",
        requiredPoints = 0,
        discountPercentage = 0,
        color = 0xFFCD7F32,
        emoji = "🥉"
    ),
    PLATA(
        displayName = "Plata",
        requiredPoints = 500,
        discountPercentage = 3,
        color = 0xFFC0C0C0,
        emoji = "🥈"
    ),
    ORO(
        displayName = "Oro",
        requiredPoints = 2000,
        discountPercentage = 5,
        color = 0xFFFFD700,
        emoji = "🥇"
    ),
    PLATINO(
        displayName = "Platino",
        requiredPoints = 5000,
        discountPercentage = 10,
        color = 0xFFE5E4E2,
        emoji = "💎"
    );

    /**
     * Calcula el siguiente tier, o null si ya es el máximo
     */
    fun nextTier(): LoyaltyTier? {
        return when (this) {
            BRONCE -> PLATA
            PLATA -> ORO
            ORO -> PLATINO
            PLATINO -> null
        }
    }

    /**
     * Puntos necesarios para alcanzar el siguiente tier
     */
    fun pointsToNextTier(currentPoints: Int): Int? {
        val next = nextTier() ?: return null
        return (next.requiredPoints - currentPoints).coerceAtLeast(0)
    }

    /**
     * Calcula el progreso hacia el siguiente tier (0.0 - 1.0)
     */
    fun calculateProgress(currentPoints: Int): Float {
        val next = nextTier() ?: return 1f

        val pointsInCurrentTier = currentPoints - requiredPoints
        val pointsNeededForNext = next.requiredPoints - requiredPoints

        return (pointsInCurrentTier.toFloat() / pointsNeededForNext.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        /**
         * Determina el tier basado en puntos totales acumulados
         */
        fun fromPoints(points: Int): LoyaltyTier {
            return when {
                points >= PLATINO.requiredPoints -> PLATINO
                points >= ORO.requiredPoints -> ORO
                points >= PLATA.requiredPoints -> PLATA
                else -> BRONCE
            }
        }
    }
}

/**
 * Registro de transacción de puntos
 */
data class PointsTransaction(
    val id: String = "",
    val userId: String = "",
    val orderId: String = "",
    val points: Int = 0,
    val type: TransactionType = TransactionType.EARNED,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class TransactionType {
    EARNED,      // Ganados por compra
    REDEEMED,    // Canjeados por descuento
    BONUS,       // Bonus especial (promoción)
    EXPIRED      // Puntos expirados
}
