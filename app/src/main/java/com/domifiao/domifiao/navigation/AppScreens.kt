package com.domifiao.domifiao.navigation

/**
 * AppScreens - Definición de Rutas de Navegación
 *
 * Sealed class que define todas las pantallas de la aplicación
 * usando Navigation Compose.
 *
 * Estructura:
 * - Authentication (Login, Register, etc.)
 * - Admin (Gestión de solicitudes)
 * - Client (Pantallas del cliente)
 * - Colmado (Pantallas del negocio)
 * - Common (Chat, Mapa, etc.)
 *
 * @since 1.0
 * @author Domifiao Team
 */
sealed class AppScreens(val route: String) {

    // ========================================
    // AUTHENTICATION
    // ========================================

    data object Splash : AppScreens("splash_screen")
    data object Onboarding : AppScreens("onboarding_screen")
    data object Login : AppScreens("login_screen")
    data object RegisterClient : AppScreens("register_client_screen")
    data object RegisterColmado : AppScreens("register_colmado_screen")

    /**
     * Pantalla de exito de registro.
     *
     * @param isVerified true si el cliente verifico su cedula, false si no
     */
    data object RegisterSuccess : AppScreens("register_success_screen/{isVerified}") {
        fun createRoute(isVerified: Boolean) = "register_success_screen/$isVerified"
    }

    data object VerificationPending : AppScreens("verification_pending_screen")
    data object ForgotPassword : AppScreens("forgot_password_screen")
    data object ColmadoRecovery : AppScreens("colmado_recovery_screen")
    data object ClientRecovery : AppScreens("client_recovery_screen")

    // ========================================
    // ADMIN
    // ========================================

    data object AdminRequests : AppScreens("admin_requests_screen")
    data object AdminDefaultProducts : AppScreens("admin_default_products_screen")

    // ========================================
    // CLIENT SCREENS
    // ========================================

    // --- Client Main Screens ---
    data object ClientHome : AppScreens("client_home_screen")
    data object ClientProfile : AppScreens("client_profile_screen")
    data object ClientEditProfile : AppScreens("client_edit_profile_screen")
    data object ClientFavorites : AppScreens("client_favorites_screen")
    data object ClientHistory : AppScreens("client_history_screen")
    data object ClientOrders : AppScreens("client_orders_screen")
    data object ClientNotifications : AppScreens("client_notifications_screen")
    data object ClientCheckout : AppScreens("client_checkout_screen")
    data object OrderSuccess : AppScreens("order_success_screen")
    data object ClientMyCredits : AppScreens("client_my_credits_screen")
    data object ClientLoyalty : AppScreens("client_loyalty_screen")
    data object GlobalSearch : AppScreens("global_search_screen")
    data object ClientChatList : AppScreens("client_chat_list")

    // --- Client Screens with Arguments ---

    /**
     * Pantalla de productos de un colmado específico.
     *
     * @param colmadoId ID del colmado a mostrar
     */
    data object ClientColmadoProducts : AppScreens("client_colmado_products_screen/{colmadoId}") {
        fun createRoute(colmadoId: String) = "client_colmado_products_screen/$colmadoId"
    }

    /**
     * Detalle de una orden del cliente.
     *
     * @param orderId ID de la orden
     */
    data object ClientOrderDetail : AppScreens("client_order_detail/{orderId}") {
        fun createRoute(orderId: String) = "client_order_detail/$orderId"
    }

    /**
     * Detalle de deuda con un colmado.
     *
     * @param colmadoId ID del colmado
     * @param colmadoName Nombre del colmado
     * @param currentDebt Deuda actual
     * @param limit Límite de crédito
     */
    data object ClientDebtDetail : AppScreens("client_debt_detail/{colmadoId}/{colmadoName}/{currentDebt}/{limit}") {
        fun createRoute(colmadoId: String, colmadoName: String, currentDebt: Int, limit: Int) =
            "client_debt_detail/$colmadoId/$colmadoName/$currentDebt/$limit"
    }

    /**
     * Solicitud de crédito a un colmado.
     *
     * @param colmadoId ID del colmado
     * @param colmadoName Nombre del colmado
     */
    data object ClientCreditRequest : AppScreens("client_credit_request/{colmadoId}/{colmadoName}") {
        fun createRoute(colmadoId: String, colmadoName: String) =
            "client_credit_request/$colmadoId/$colmadoName"
    }

    // ========================================
    // COLMADO SCREENS
    // ========================================

    // --- Colmado Main Screens ---
    data object ColmadoHome : AppScreens("colmado_home_screen")
    data object Inventory : AppScreens("inventory_screen")
    data object AddProduct : AppScreens("add_product_screen")
    data object ColmadoOrders : AppScreens("colmado_orders_screen")
    data object ColmadoCustomers : AppScreens("colmado_customers_screen")
    data object ColmadoReports : AppScreens("colmado_reports_screen")
    data object ColmadoSettings : AppScreens("colmado_settings_screen")
    data object ColmadoLoyaltySettings : AppScreens("colmado_loyalty_settings_screen")
    data object ColmadoStaff : AppScreens("colmado_staff_screen")
    data object ColmadoCreditRequests : AppScreens("colmado_credit_requests")
    data object ColmadoChatList : AppScreens("colmado_chat_list")
    data object ColmadoNotifications : AppScreens("colmado_notifications_screen")

    // --- Colmado Screens with Arguments ---

    /**
     * Detalle de una orden del colmado.
     *
     * @param orderId ID de la orden
     */
    data object ColmadoOrderDetail : AppScreens("colmado_order_detail/{orderId}") {
        fun createRoute(orderId: String) = "colmado_order_detail/$orderId"
    }

    /**
     * Cotización de productos para una orden.
     *
     * @param orderId ID de la orden
     */
    data object ColmadoProducts : AppScreens("colmado_products/{orderId}") {
        fun createRoute(orderId: String) = "colmado_products/$orderId"
    }


    /**
     * Pantalla de pago de deuda.
     *
     * @param debtId ID de la deuda
     * @param clientName Nombre del cliente
     * @param currentDebt Deuda actual
     */
    data object ColmadoPayment : AppScreens("colmado_payment_screen/{debtId}/{clientName}/{currentDebt}") {
        fun createRoute(debtId: String, clientName: String, currentDebt: Int) =
            "colmado_payment_screen/$debtId/$clientName/$currentDebt"
    }

    /**
     * Agregar transacción (abono o cargo) a deuda de cliente.
     *
     * @param clientId ID del cliente
     * @param clientName Nombre del cliente
     * @param currentDebt Deuda actual
     * @param debtId ID del registro de deuda
     */
    data object ColmadoAddTransaction : AppScreens("colmado_add_transaction/{clientId}/{clientName}/{currentDebt}/{debtId}") {
        fun createRoute(clientId: String, clientName: String, currentDebt: Int, debtId: String) =
            "colmado_add_transaction/$clientId/$clientName/$currentDebt/$debtId"
    }

    /**
     * Historial de deuda de un cliente.
     *
     * @param clientId ID del cliente
     * @param clientName Nombre del cliente
     * @param currentDebt Deuda actual
     * @param debtId ID del registro de deuda
     */
    data object ColmadoDebtHistory : AppScreens("colmado_debt_history/{clientId}/{clientName}/{currentDebt}/{debtId}") {
        fun createRoute(clientId: String, clientName: String, currentDebt: Int, debtId: String) =
            "colmado_debt_history/$clientId/$clientName/$currentDebt/$debtId"
    }

    // ========================================
    // COMMON SCREENS (Compartidas)
    // ========================================

    /**
     * Pantalla de chat entre dos usuarios.
     *
     * ACTUALIZADO: Ahora usa targetId y targetType para mayor flexibilidad.
     *
     * @param targetId ID del destinatario (puede ser colmadoId, clientId, orderId, etc.)
     * @param targetType Tipo del destinatario ("COLMADO", "CLIENT", "ORDER", etc.)
     *
     * Ejemplos de uso:
     * - Chat con colmado: createRoute(colmadoId, "COLMADO")
     * - Chat de orden: createRoute(orderId, "ORDER")
     * - Chat directo: createRoute(userId, "CLIENT")
     */
    data object Chat : AppScreens("chat_screen/{targetId}/{targetType}") {
        fun createRoute(targetId: String, targetType: String) =
            "chat_screen/$targetId/$targetType"

        fun createColmadoChat(colmadoId: String) = createRoute(colmadoId, "COLMADO")
    }

    /**
     * Mapa en vivo para seguimiento de entregas.
     *
     * @param orderId ID de la orden
     * @param isDeliveryMode true si es modo delivery, false si es cliente
     */
    data object LiveMap : AppScreens("live_map/{orderId}/{isDeliveryMode}") {
        fun createRoute(orderId: String, isDeliveryMode: Boolean) =
            "live_map/$orderId/$isDeliveryMode"
    }

    /**
     * Dashboard del repartidor (delivery).
     */
    data object DeliveryDashboard : AppScreens(route = "delivery_dashboard/{colmadoId}") {
        fun createRoute(colmadoId: String) = "delivery_dashboard/$colmadoId"
    }
    // ========================================
    // UTILITY FUNCTIONS
    // ========================================

    companion object
}