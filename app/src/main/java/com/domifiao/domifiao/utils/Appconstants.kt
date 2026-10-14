package com.domifiao.domifiao.utils

import androidx.compose.ui.graphics.Color

/**
 * Constantes centralizadas para la aplicacion Domifiao
 */
object AppConstants {

    // --- ROLES DE USUARIO ---
    object Roles {
        const val CLIENT = "CLIENT"
        const val COLMADO = "COLMADO"
        const val DELIVERY = "DELIVERY"
        const val ADMIN = "ADMIN"
    }

    // --- ESTADOS DE PEDIDOS ---
    object OrderStatus {
        const val PENDING = "PENDING"
        const val WAITING_APPROVAL = "WAITING_APPROVAL"
        const val PREPARING = "PREPARING"
        const val DELIVERING = "DELIVERING"
        const val WAITING_CLIENT = "WAITING_CLIENT"
        const val COMPLETED = "COMPLETED"
        const val CANCELLED = "CANCELLED"

        fun isActive(status: String): Boolean {
            return status in listOf(PENDING, WAITING_APPROVAL, PREPARING, DELIVERING, WAITING_CLIENT)
        }

        fun isFinal(status: String): Boolean {
            return status in listOf(COMPLETED, CANCELLED)
        }

        fun getDisplayName(status: String): String {
            return when (status) {
                PENDING -> "Pendiente"
                WAITING_APPROVAL -> "Esperando Aprobacion"
                PREPARING -> "En Preparacion"
                DELIVERING -> "En Camino"
                WAITING_CLIENT -> "Por Confirmar"
                COMPLETED -> "Completado"
                CANCELLED -> "Cancelado"
                else -> status
            }
        }

        fun getColor(status: String): Color {
            return when (status) {
                PENDING -> Colors.StatusPending
                WAITING_APPROVAL -> Colors.Warning
                PREPARING -> Colors.StatusPreparing
                DELIVERING -> Colors.StatusDelivering
                COMPLETED -> Colors.StatusCompleted
                CANCELLED -> Colors.StatusCancelled
                else -> Color.Gray
            }
        }
    }

    // --- ESTADOS DE SOLICITUD DE CREDITO ---
    object CreditStatus {
        const val PENDING = "PENDING"
        const val APPROVED = "APPROVED"
        const val REJECTED = "REJECTED"

        fun getDisplayName(status: String): String {
            return when (status) {
                PENDING -> "Pendiente"
                APPROVED -> "Aprobado"
                REJECTED -> "Rechazado"
                else -> status
            }
        }
    }

    // --- TIPOS DE TRANSACCION DE DEUDA ---
    object TransactionTypes {
        const val COMPRA = "COMPRA"
        const val ABONO = "ABONO"

        fun getDisplayName(type: String): String {
            return when (type) {
                COMPRA -> "Compra"
                ABONO -> "Abono"
                else -> type
            }
        }
    }

    // --- TIPOS DE NOTIFICACION ---
    object NotificationTypes {
        const val NEW_ORDER = "NEW_ORDER"
        const val ORDER_UPDATE = "ORDER_UPDATE"
        const val MESSAGE = "MESSAGE"
        const val DEBT_CHAT = "DEBT_CHAT"
        const val CREDIT_REQUEST = "CREDIT_REQUEST"
        const val CREDIT_APPROVED = "CREDIT_APPROVED"
        const val CREDIT_REJECTED = "CREDIT_REJECTED"
        const val DEBT_UPDATE = "DEBT_UPDATE"
        const val SUCCESS = "SUCCESS"
        const val INFO = "INFO"
    }

    // --- METODOS DE PAGO ---
    object PaymentMethods {
        const val EFECTIVO = "Efectivo"
        const val FIADO = "Fiado"
        const val TRANSFERENCIA = "Transferencia"

        val allMethods = listOf(EFECTIVO, FIADO, TRANSFERENCIA)
    }

    // --- UNIDADES DE PRODUCTO ---
    object ProductUnits {
        const val UNIDAD = "UNIDAD"
        const val LIBRA = "LIBRA"
        const val LITRO = "LITRO"
        const val PAQUETE = "PAQUETE"
        const val DOCENA = "DOCENA"

        val allUnits = listOf(UNIDAD, LIBRA, LITRO, PAQUETE, DOCENA)

        fun getDisplayName(unit: String): String {
            return when (unit) {
                UNIDAD -> "Unidad"
                LIBRA -> "Libra"
                LITRO -> "Litro"
                PAQUETE -> "Paquete"
                DOCENA -> "Docena"
                else -> unit
            }
        }

        fun getAbbreviation(unit: String): String {
            return when (unit) {
                UNIDAD -> "ud"
                LIBRA -> "lb"
                LITRO -> "lt"
                PAQUETE -> "pq"
                DOCENA -> "doc"
                else -> unit.take(2).lowercase()
            }
        }
    }

    // --- CATEGORIAS DE PRODUCTOS ---
    object ProductCategories {
        val all = listOf(
            "Viveres",
            "Bebidas",
            "Alcohol",
            "Limpieza",
            "Embutidos",
            "Lacteos",
            "Snacks",
            "Cigarrillos",
            "Higiene",
            "Otros"
        )
    }

    // --- LIMITES Y VALIDACIONES ---
    object Limits {
        const val MIN_SEARCH_LENGTH = 3
        const val MAX_CART_ITEMS = 50
        const val MAX_ORDER_NOTE_LENGTH = 200
        const val DEFAULT_CREDIT_LIMIT = 3000
        const val MAX_CREDIT_LIMIT = 50000
        const val MIN_DELIVERY_FEE = 0
        const val MAX_DELIVERY_FEE = 500
        const val PHONE_LENGTH = 10
        const val CEDULA_LENGTH = 11
    }

    // --- COLORES DE LA APP ---
    object Colors {
        val Primary = Color(0xFF4CAF50)
        val PrimaryDark = Color(0xFF2E7D32)
        val Secondary = Color(0xFF2196F3)
        val Accent = Color(0xFFFF9800)
        val Error = Color(0xFFD32F2F)
        val Success = Color(0xFF4CAF50)
        val Warning = Color(0xFFFF9800)
        val Info = Color(0xFF2196F3)

        // Colores de estado de pedidos
        val StatusPending = Color(0xFFFF9800)
        val StatusPreparing = Color(0xFF2196F3)
        val StatusDelivering = Color(0xFF9C27B0)
        val StatusCompleted = Color(0xFF4CAF50)
        val StatusCancelled = Color(0xFFD32F2F)

        // Fondos
        val BackgroundLight = Color(0xFFF5F5F5)
        val BackgroundCard = Color.White
        val BackgroundSuccess = Color(0xFFE8F5E9)
        val BackgroundError = Color(0xFFFFEBEE)
        val BackgroundWarning = Color(0xFFFFF3E0)
    }

    // --- COLECCIONES DE FIRESTORE ---
    object Collections {
        const val USERS = "users"
        const val COLMADOS_REQUESTS = "colmados_requests"
        const val COLMADO_PRODUCTS = "colmado_products"
        const val GLOBAL_PRODUCTS = "global_products"
        const val ORDERS = "orders"
        const val DEBTS = "debts"
        const val DEBT_TRANSACTIONS = "debt_transactions"
        const val CREDIT_REQUESTS = "credit_requests"
        const val CHATS = "chats"
        const val NOTIFICATIONS = "notifications"
    }

    // --- TIEMPOS ---
    object Timeouts {
        const val SPLASH_DELAY_MS = 1500L
        const val TOAST_SHORT_MS = 2000L
        const val DEBOUNCE_MS = 300L
        const val SESSION_MAX_AGE_DAYS = 30
    }

    // --- MENSAJES COMUNES ---
    object Messages {
        const val ERROR_CONNECTION = "Error de conexion. Verifica tu internet."
        const val ERROR_GENERIC = "Ha ocurrido un error. Intenta de nuevo."
        const val SUCCESS_SAVED = "Guardado correctamente"
        const val SUCCESS_DELETED = "Eliminado correctamente"
        const val LOADING = "Cargando..."
        const val EMPTY_CART = "Tu carrito esta vacio"
        const val COLMADO_CLOSED = "El colmado esta cerrado"
        const val INSUFFICIENT_CREDIT = "Credito insuficiente"
        const val ORDER_SENT = "Pedido enviado exitosamente"
        const val ORDER_CANCELLED = "Pedido cancelado"
        const val CREDIT_APPROVED = "Credito aprobado"
        const val CREDIT_REJECTED = "Solicitud de credito rechazada"
    }
}