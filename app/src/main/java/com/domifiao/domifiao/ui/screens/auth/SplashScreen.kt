package com.domifiao.domifiao.ui.screens.auth

import android.util.Log
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.domifiao.domifiao.R
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

@Composable
fun SplashScreen(navController: NavController, pendingRoute: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    val scale = remember { androidx.compose.animation.core.Animatable(0f) }
    var navigationCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            Log.d("SplashScreen", "Iniciando SplashScreen...")
            
            // Animación del logo
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 800,
                    easing = { OvershootInterpolator(2f).getInterpolation(it) }
                )
            )

            delay(1000)

            // Timeout de 7 segundos para la navegación (un poco más por la consulta a Firestore)
            withTimeout(7000L) {
                val role = sessionManager.getRole()
                val userId = sessionManager.getUserId()
                var isLoggedIn = auth.currentUser != null && userId != null

                // VERIFICACIÓN ADICIONAL: ¿Existe el documento en Firestore?
                if (isLoggedIn && userId != null && role != null) {
                    try {
                        val collectionPath = if (role == "COLMADO") "colmados_requests" else "users"
                        val doc = db.collection(collectionPath).document(userId).get().await()
                        
                        if (!doc.exists()) {
                            Log.w("SplashScreen", "Cuenta no encontrada en Firestore. Forzando logout.")
                            auth.signOut()
                            sessionManager.clearSession()
                            isLoggedIn = false
                        }
                    } catch (e: Exception) {
                        Log.e("SplashScreen", "Error verificando cuenta en Firestore", e)
                        // Si falla la verificación por red, permitimos pasar (el listener global lo atrapará después)
                    }
                }

                Log.d("SplashScreen", "Estado: isLoggedIn=$isLoggedIn, role=$role, userId=$userId")

                if (isLoggedIn && role != null) {
                    if (pendingRoute != null && pendingRoute.isNotEmpty()) {
                        try {
                            Log.d("SplashScreen", "Navegando a pendingRoute: $pendingRoute")
                            navController.navigate(pendingRoute) {
                                popUpTo(AppScreens.Splash.route) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            Log.e("SplashScreen", "Error navegando a pendingRoute", e)
                            val homeRoute = if (role == "CLIENT") AppScreens.ClientHome.route else AppScreens.ColmadoHome.route
                            Log.d("SplashScreen", "Navegando a homeRoute: $homeRoute")
                            navController.navigate(homeRoute) { 
                                popUpTo(AppScreens.Splash.route) { inclusive = true } 
                            }
                        }
                    } else {
                        if (role == "CLIENT") {
                            Log.d("SplashScreen", "Navegando a ClientHome")
                            navController.navigate(AppScreens.ClientHome.route) { 
                                popUpTo(AppScreens.Splash.route) { inclusive = true } 
                            }
                        } else {
                            Log.d("SplashScreen", "Navegando a ColmadoHome")
                            navController.navigate(AppScreens.ColmadoHome.route) { 
                                popUpTo(AppScreens.Splash.route) { inclusive = true } 
                            }
                        }
                    }
                } else {
                    Log.d("SplashScreen", "Navegando a Onboarding")
                    navController.navigate(AppScreens.Onboarding.route) { 
                        popUpTo(AppScreens.Splash.route) { inclusive = true } 
                    }
                }
                
                navigationCompleted = true
                Log.d("SplashScreen", "Navegación completada exitosamente")
            }
        } catch (e: Exception) {
            Log.e("SplashScreen", "Error crítico en SplashScreen", e)
            // Fallback: ir a Onboarding en caso de cualquier error
            try {
                navController.navigate(AppScreens.Onboarding.route) {
                    popUpTo(AppScreens.Splash.route) { inclusive = true }
                }
            } catch (navError: Exception) {
                Log.e("SplashScreen", "Error navegando al fallback", navError)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Logo - sin try-catch porque no es compatible con Compose
            Image(
                painter = painterResource(id = R.drawable.domifiao_logo),
                contentDescription = "Logo Domifiao",
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale.value)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Domifiao",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(color = Color(0xFF4CAF50))
            
            // Indicador de estado de navegación (solo en debug)
            if (!navigationCompleted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cargando...",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Text(
            text = "Tu colmado en línea",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}