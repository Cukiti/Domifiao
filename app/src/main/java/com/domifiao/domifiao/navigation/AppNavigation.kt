package com.domifiao.domifiao.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.services.RecaptchaService
import com.domifiao.domifiao.ui.screens.admin.*
import com.domifiao.domifiao.ui.screens.auth.*
import com.domifiao.domifiao.ui.screens.client.*
import com.domifiao.domifiao.ui.screens.colmado.*
import com.domifiao.domifiao.ui.screens.common.*
import com.domifiao.domifiao.ui.screens.delivery.DeliveryDashboardScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    sessionManager: SessionManager,
    startDestination: String = AppScreens.Splash.route,
    recaptchaService: RecaptchaService,
    pendingRoute: String?,
    onClearPendingRoute: () -> Unit,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    NavHost(
        navController = navController, 
        startDestination = startDestination,
        enterTransition = { 
            slideInHorizontally(
                animationSpec = tween(400, easing = FastOutSlowInEasing), 
                initialOffsetX = { it }
            ) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = { 
            slideOutHorizontally(
                animationSpec = tween(400, easing = FastOutSlowInEasing), 
                targetOffsetX = { -(it * 0.3).toInt() } // Simula el efecto parallax de iOS
            ) + fadeOut(animationSpec = tween(400))
        },
        popEnterTransition = { 
            slideInHorizontally(
                animationSpec = tween(400, easing = FastOutSlowInEasing), 
                initialOffsetX = { -(it * 0.3).toInt() }
            ) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = { 
            slideOutHorizontally(
                animationSpec = tween(400, easing = FastOutSlowInEasing), 
                targetOffsetX = { it }
            ) + fadeOut(animationSpec = tween(400))
        }
    ) {
        // --- SPLASH ---
        composable(AppScreens.Splash.route) {
            SplashScreen(navController, pendingRoute)
            LaunchedEffect(Unit) {
                // Limpiar el estado después de que el Splash lo procese (si la app estaba cerrada)
                if (pendingRoute != null) {
                    onClearPendingRoute()
                }
            }
        }

        // --- AUTH ---
        composable(AppScreens.Onboarding.route) { OnboardingScreen(navController) }
        composable(AppScreens.Login.route) { 
            LoginScreen(
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleDarkMode = {
                    val newVal = !isDarkMode
                    sessionManager.setDarkMode(newVal)
                    onDarkModeChange(newVal)
                },
                recaptchaService = recaptchaService
            ) 
        }
        composable(AppScreens.RegisterClient.route) {
            RegisterClientScreen(navController, recaptchaService, sessionManager)
        }
        composable(AppScreens.RegisterColmado.route) { RegisterColmadoScreen(navController, recaptchaService) }
        composable(
            route = AppScreens.RegisterSuccess.route,
            arguments = listOf(
                navArgument("isVerified") {
                    type = NavType.BoolType
                    defaultValue = true
                }
            )
        ) { backStackEntry ->
            val isVerified = backStackEntry.arguments?.getBoolean("isVerified") ?: true
            RegisterSuccessScreen(navController, isVerified)
        }
        composable(AppScreens.VerificationPending.route) { VerificationPendingScreen(navController) }
        composable(AppScreens.ForgotPassword.route) { ForgotPasswordScreen(navController) }
        composable(AppScreens.ClientRecovery.route) { ClientRecoveryScreen(navController, recaptchaService) }
        composable(AppScreens.ColmadoRecovery.route) { ColmadoRecoveryScreen(navController, recaptchaService) }

        // --- ADMIN ---
        composable(AppScreens.AdminRequests.route) { AdminRequestsScreen(navController) }
        composable(AppScreens.AdminDefaultProducts.route) { AdminDefaultProductsScreen(navController) }

        // --- CLIENTE (Pantallas Simples) ---
        composable(AppScreens.ClientHome.route) { 
            ClientHomeScreen(
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleDarkMode = {
                    val newVal = !isDarkMode
                    sessionManager.setDarkMode(newVal)
                    onDarkModeChange(newVal)
                }
            ) 
        }
        composable(AppScreens.ClientProfile.route) { ClientProfileScreen(navController) }
        composable(AppScreens.ClientEditProfile.route) { 
            ClientEditProfileScreen(
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleDarkMode = {
                    val newVal = !isDarkMode
                    sessionManager.setDarkMode(newVal)
                    onDarkModeChange(newVal)
                },
                recaptchaService = recaptchaService
            ) 
        }
        composable(AppScreens.ClientOrders.route) { ClientOrdersScreen(navController) }
        composable(AppScreens.ClientHistory.route) { ClientHistoryScreen(navController) }
        composable(AppScreens.ClientFavorites.route) { ClientFavoritesScreen(navController) }
        composable(AppScreens.ClientNotifications.route) { ClientNotificationsScreen(navController) }
        composable(AppScreens.ClientCheckout.route) { ClientCheckoutScreen(navController) }
        composable(AppScreens.OrderSuccess.route) { OrderSuccessScreen(navController) }
        composable(AppScreens.ClientMyCredits.route) { ClientMyCreditsScreen(navController) }
        composable(AppScreens.ClientLoyalty.route) { ClientLoyaltyScreen(navController) }
        composable(AppScreens.GlobalSearch.route) { GlobalSearchScreen(navController) }
        composable(AppScreens.ClientChatList.route) { ChatListScreen(navController) }

        // --- CLIENTE (Pantallas con Argumentos) ---

        // Productos de un Colmado
        composable(
            route = AppScreens.ClientColmadoProducts.route,
            arguments = listOf(navArgument("colmadoId") { type = NavType.StringType })
        ) { entry ->
            ClientColmadoProductsScreen(navController, entry.arguments?.getString("colmadoId"))
        }

        // Detalle de Orden Cliente
        composable(
            route = AppScreens.ClientOrderDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { entry ->
            ClientOrderDetailScreen(navController, entry.arguments?.getString("orderId"))
        }

        // Detalle de Deuda Cliente
        composable(
            route = AppScreens.ClientDebtDetail.route,
            arguments = listOf(
                navArgument("colmadoId") { type = NavType.StringType },
                navArgument("colmadoName") { type = NavType.StringType },
                navArgument("currentDebt") { type = NavType.IntType },
                navArgument("limit") { type = NavType.IntType }
            )
        ) { entry ->
            ClientDebtDetailScreen(
                navController = navController,
                colmadoId = entry.arguments?.getString("colmadoId") ?: "",
                colmadoName = entry.arguments?.getString("colmadoName") ?: "Colmado",
                currentDebt = entry.arguments?.getInt("currentDebt") ?: 0,
                limit = entry.arguments?.getInt("limit") ?: 0
            )
        }

        // Solicitud de Crédito
        composable(
            route = AppScreens.ClientCreditRequest.route,
            arguments = listOf(
                navArgument("colmadoId") { type = NavType.StringType },
                navArgument("colmadoName") { type = NavType.StringType }
            )
        ) { entry ->
            ClientCreditRequestScreen(
                navController,
                entry.arguments?.getString("colmadoId") ?: "",
                entry.arguments?.getString("colmadoName") ?: ""
            )
        }

        // --- COLMADO (Pantallas Simples) ---
        composable(AppScreens.ColmadoHome.route) { 
            ColmadoHomeScreen(
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleDarkMode = {
                    val newVal = !isDarkMode
                    sessionManager.setDarkMode(newVal)
                    onDarkModeChange(newVal)
                }
            ) 
        }
        composable(AppScreens.Inventory.route) { InventoryScreen(navController) }
        composable(AppScreens.AddProduct.route) { AddProductScreen(navController) }
        composable(AppScreens.ColmadoOrders.route) { ColmadoOrdersScreen(navController) }
        composable(AppScreens.ColmadoCustomers.route) { ColmadoCustomersScreen(navController) }
        composable(AppScreens.ColmadoReports.route) { ColmadoReportsScreen(navController) }
        composable(AppScreens.ColmadoSettings.route) { 
            ColmadoSettingsScreen(
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleDarkMode = {
                    val newVal = !isDarkMode
                    sessionManager.setDarkMode(newVal)
                    onDarkModeChange(newVal)
                },
                recaptchaService = recaptchaService
            ) 
        }
        composable(AppScreens.ColmadoLoyaltySettings.route) { ColmadoLoyaltySettingsScreen(navController) }
        composable(AppScreens.ColmadoStaff.route) { ColmadoStaffScreen(navController) }
        composable(AppScreens.ColmadoCreditRequests.route) { ColmadoCreditRequestsScreen(navController) }
        composable(AppScreens.ColmadoChatList.route) { ColmadoChatListScreen(navController) }

        // FIX: Usando AppScreens en lugar de string hardcodeado
        composable(AppScreens.ColmadoNotifications.route) { ColmadoNotificationsScreen(navController) }

        // --- COLMADO (Pantallas con Argumentos) ---

        // Detalle Orden Colmado
        composable(
            route = AppScreens.ColmadoOrderDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { entry ->
            ColmadoOrderDetailScreen(navController, entry.arguments?.getString("orderId"))
        }

        // FIX: Cotizar Productos - Usando AppScreens en lugar de string hardcodeado
        composable(
            route = AppScreens.ColmadoProducts.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { entry ->
            ColmadoProductsScreen(navController, entry.arguments?.getString("orderId"))
        }

        // Pantalla Pago
        composable(
            route = AppScreens.ColmadoPayment.route,
            arguments = listOf(
                navArgument("debtId") { type = NavType.StringType },
                navArgument("clientName") { type = NavType.StringType },
                navArgument("currentDebt") { type = NavType.IntType }
            )
        ) { entry ->
            ColmadoPaymentScreen(
                navController,
                entry.arguments?.getString("debtId") ?: "",
                entry.arguments?.getString("clientName") ?: "",
                entry.arguments?.getInt("currentDebt") ?: 0
            )
        }

        // Nueva Transacción (Abono/Cargo)
        composable(
            route = AppScreens.ColmadoAddTransaction.route,
            arguments = listOf(
                navArgument("clientId") { type = NavType.StringType },
                navArgument("clientName") { type = NavType.StringType },
                navArgument("currentDebt") { type = NavType.IntType },
                navArgument("debtId") { type = NavType.StringType }
            )
        ) { entry ->
            ColmadoAddTransactionScreen(
                navController,
                entry.arguments?.getString("debtId") ?: "",
                entry.arguments?.getString("clientName") ?: "",
                entry.arguments?.getInt("currentDebt") ?: 0
            )
        }

        // Historial de Deuda (Vista Colmado)
        composable(
            route = AppScreens.ColmadoDebtHistory.route,
            arguments = listOf(
                navArgument("clientId") { type = NavType.StringType },
                navArgument("clientName") { type = NavType.StringType },
                navArgument("currentDebt") { type = NavType.IntType },
                navArgument("debtId") { type = NavType.StringType }
            )
        ) { entry ->
            ColmadoDebtHistoryScreen(
                navController,
                entry.arguments?.getString("clientId") ?: "",
                entry.arguments?.getString("clientName") ?: "",
                entry.arguments?.getInt("currentDebt") ?: 0,
                entry.arguments?.getString("debtId") ?: ""
            )
        }

        // --- COMUNES ---
        composable(AppScreens.DeliveryDashboard.route) { DeliveryDashboardScreen(navController) }

        // Chat
        composable(
            route = AppScreens.Chat.route,
            arguments = listOf(
                navArgument("targetId") { type = NavType.StringType },
                navArgument("targetType") { type = NavType.StringType }
            )
        ) { entry ->
            ChatScreen(
                navController,
                entry.arguments?.getString("targetId") ?: "",
                entry.arguments?.getString("targetType") ?: ""
            )
        }

        // Mapa en vivo
        composable(
            route = AppScreens.LiveMap.route,
            arguments = listOf(
                navArgument("orderId") { type = NavType.StringType },
                navArgument("isDeliveryMode") { type = NavType.BoolType }
            )
        ) { entry ->
            LiveMapScreen(
                navController,
                entry.arguments?.getString("orderId") ?: "",
                entry.arguments?.getBoolean("isDeliveryMode") ?: false
            )
        }
    }
}
