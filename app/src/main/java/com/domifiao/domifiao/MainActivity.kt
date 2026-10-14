package com.domifiao.domifiao

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.model.CartManager
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.screens.auth.*
import com.domifiao.domifiao.ui.screens.client.*
import com.domifiao.domifiao.ui.screens.colmado.*
import com.domifiao.domifiao.ui.screens.common.ChatScreen
import com.domifiao.domifiao.ui.screens.common.LiveMapScreen
import com.domifiao.domifiao.ui.screens.common.NoInternetScreen
import com.domifiao.domifiao.ui.screens.delivery.DeliveryDashboardScreen
import com.domifiao.domifiao.ui.screens.admin.AdminRequestsScreen
import com.domifiao.domifiao.ui.screens.admin.AdminDefaultProductsScreen
import com.domifiao.domifiao.ui.theme.DomifiaoTheme
import com.domifiao.domifiao.utils.NetworkUtils
import com.domifiao.domifiao.utils.NotificationHandler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalContext

import android.net.Uri
import androidx.activity.viewModels
import com.domifiao.domifiao.ui.screens.common.ForceUpdateScreen
import com.domifiao.domifiao.ui.screens.common.MaintenanceScreen
import com.domifiao.domifiao.ui.screens.main.MainViewModel
import com.domifiao.domifiao.utils.RemoteConfigHelper
import dagger.hilt.android.AndroidEntryPoint

import com.domifiao.domifiao.services.RecaptchaService
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var recaptchaService: RecaptchaService

    private var pendingRoute: String? = null
    private lateinit var networkUtils: NetworkUtils
    private val mainViewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Habilita notificaciones para ver actualizaciones", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.updateOnlineStatus(true)
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.updateOnlineStatus(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainViewModel.updateOnlineStatus(false)
    }

    @Deprecated("Necesario para Firebase Auth reCAPTCHA fallback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Firebase maneja esto internamente cuando pasamos la Activity a PhoneAuthOptions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        networkUtils = NetworkUtils(this)
        pendingRoute = intent.getStringExtra(NotificationHandler.EXTRA_ROUTE)
        askNotificationPermission()

        val sessionManager = SessionManager(this)

        setContent {
            val systemTheme = androidx.compose.foundation.isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(sessionManager.isDarkMode(systemTheme)) }

            DomifiaoTheme(darkTheme = isDarkMode) {
                val isConnected by networkUtils.observeConnectivity.collectAsState(initial = networkUtils.isConnected())
                
                // Estados de bloqueo por Remote Config
                var isUpdateRequired by remember { mutableStateOf(false) }
                var isMaintenanceMode by remember { mutableStateOf(false) }
                var configLoaded by remember { mutableStateOf(false) }

                // Verificar configuración al iniciar
                LaunchedEffect(Unit) {
                    RemoteConfigHelper.fetchConfig {
                        isUpdateRequired = RemoteConfigHelper.isForceUpdateRequired()
                        isMaintenanceMode = RemoteConfigHelper.isMaintenanceMode()
                        configLoaded = true
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        // 1. Prioridad: Actualización Forzada
                        isUpdateRequired -> {
                            ForceUpdateScreen(
                                message = RemoteConfigHelper.getUpdateMessage(),
                                onUpdateClick = { openPlayStore() }
                            )
                        }

                        // 2. Prioridad: Modo Mantenimiento
                        isMaintenanceMode -> {
                            MaintenanceScreen(
                                message = RemoteConfigHelper.getMaintenanceMessage(),
                                onRetryClick = {
                                    // Reintentar fetch de config
                                    RemoteConfigHelper.fetchConfig {
                                        isUpdateRequired = RemoteConfigHelper.isForceUpdateRequired()
                                        isMaintenanceMode = RemoteConfigHelper.isMaintenanceMode()
                                    }
                                }
                            )
                        }

                        // 3. Prioridad: Sin Internet
                        !isConnected -> {
                            NoInternetScreen(onRetry = {
                                if (networkUtils.isConnected()) {
                                    // Recheck connectivity logic handled by flow
                                } else {
                                    Toast.makeText(this, "Aún no hay conexión", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }

                        // 4. App Normal
                        else -> {
                            val navController = rememberNavController()
                            
                            // Listener para manejar Deep Links/Rutas de Notificaciones cuando la app ya está abierta
                            val context = LocalContext.current
                            LaunchedEffect(Unit) {
                                if (pendingRoute != null) {
                                    val route = pendingRoute
                                    pendingRoute = null
                                    intent.removeExtra(NotificationHandler.EXTRA_ROUTE)
                                    
                                    // Esperamos un momento a que el NavHost esté listo
                                    kotlinx.coroutines.delay(500)
                                    if (route != null && route.isNotEmpty()) {
                                        navController.navigate(route)
                                    }
                                }
                            }

                            // Manejar eventos desde MainViewModel
                            LaunchedEffect(Unit) {
                                mainViewModel.accountDeletedEvent.collect {
                                    Toast.makeText(context, "Tu cuenta ya no está disponible", Toast.LENGTH_LONG).show()
                                    mainViewModel.performLogout()
                                    navController.navigate(AppScreens.Onboarding.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                            
                            LaunchedEffect(Unit) {
                                mainViewModel.notificationEvent.collect { event ->
                                    NotificationHandler(context).showNotification(
                                        event.title, 
                                        event.message, 
                                        event.route, 
                                        event.type
                                    )
                                }
                            }

                            RestoreCartSession()

                            com.domifiao.domifiao.navigation.AppNavigation(
                                navController = navController,
                                sessionManager = sessionManager,
                                recaptchaService = recaptchaService,
                                pendingRoute = pendingRoute,
                                onClearPendingRoute = {
                                    pendingRoute = null
                                    intent.removeExtra(NotificationHandler.EXTRA_ROUTE)
                                },
                                isDarkMode = isDarkMode,
                                onDarkModeChange = { isDarkMode = it }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(NotificationHandler.EXTRA_ROUTE)?.let { route ->
            pendingRoute = route
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun openPlayStore() {
        val appPackageName = packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
        }
    }
}

// --- UTILIDADES GLOBALES ---

@Composable
fun RestoreCartSession() {
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid
    LaunchedEffect(userId) {
        if (userId != null && CartManager.items.isEmpty()) {
            CartManager.loadFromFirestore(userId)
        }
    }
}