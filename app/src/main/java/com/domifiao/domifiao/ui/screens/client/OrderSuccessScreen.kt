package com.domifiao.domifiao.ui.screens.client

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.domifiao.domifiao.navigation.AppScreens
import kotlinx.coroutines.delay

@Composable
fun OrderSuccessScreen(navController: NavController) {
    // Animación de entrada
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    // Bloquear botón atrás para no volver al checkout
    BackHandler {
        navController.navigate(AppScreens.ClientHome.route) {
            popUpTo(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icono Animado con Fondo Gradiente
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF4CAF50), Color(0xFF81C784))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Éxito",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "¡Pedido Enviado!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black, // Más grueso para impacto
                color = Color(0xFF2E7D32)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "El colmado ha recibido tu orden. Te notificaremos cuando la acepten o si hay cambios en el precio.",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = Color.Gray,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Botones de Acción Estilizados
            Button(
                onClick = {
                    // Navegar a Mis Pedidos y borrar la pila de checkout
                    navController.navigate(AppScreens.ClientOrders.route) {
                        popUpTo(AppScreens.ClientHome.route) { inclusive = false }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Ver Estado del Pedido", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    // Volver al inicio limpiando todo
                    navController.navigate(AppScreens.ClientHome.route) {
                        popUpTo(0)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.LightGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
            ) {
                Icon(Icons.Default.Home, null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Volver al Inicio", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}