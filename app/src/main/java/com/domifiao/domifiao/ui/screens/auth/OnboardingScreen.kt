package com.domifiao.domifiao.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.domifiao.domifiao.R
import com.domifiao.domifiao.navigation.AppScreens
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val imageRes: Int
)

@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity

    // --- BLINDAJE DE NAVEGACIÓN ---
    // Si le dan atrás aquí, cerramos la app porque es el inicio
    BackHandler {
        activity?.finish()
    }

    // Definimos las 4 páginas
    val pages = listOf(
        OnboardingPage(
            "Domifiao",
            "Conecta con tu colmado local. Pide tus productos favoritos y recíbelos en la puerta de tu casa.",
            Icons.Filled.Store,
            R.drawable.fondo_colmado
        ),
        OnboardingPage(
            "Pide Fiado Fácil",
            "Realiza pedidos a crédito y mantén un control claro de tus deudas y compras mensuales.",
            Icons.Filled.ShoppingCart,
            R.drawable.fondo_compra
        ),
        OnboardingPage(
            "Gestión Transparente",
            "Evita confusiones. Lleva una gestión de deuda clara para que no haya malentendidos.",
            Icons.Filled.AccountBalanceWallet,
            R.drawable.fondo_deuda
        ),
        OnboardingPage(
            "Seguridad Total",
            "Tu colmado de confianza, ahora digital. Registro seguro y validado.",
            Icons.Filled.VerifiedUser,
            R.drawable.fondo_seguridad
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Fondo (Imagen)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            Image(
                painter = painterResource(id = pages[pageIndex].imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Filtro Oscuro (Overlay)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        // 3. Contenido (Texto y Botones)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f)) // Empujar al centro

            // Icono grande
            Icon(
                imageVector = pages[pagerState.currentPage].icon,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Título
            Text(
                text = pages[pagerState.currentPage].title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Descripción
            Text(
                text = pages[pagerState.currentPage].description,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.weight(1f)) // Empujar abajo

            // Controles Inferiores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicadores (Puntitos)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(pages.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.White else Color.Gray
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color, CircleShape)
                        )
                    }
                }

                // Botón Siguiente / Empezar
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            // Siguiente página
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            // Ir al Login y borrar el Onboarding del historial
                            navController.navigate(AppScreens.Login.route) {
                                popUpTo(AppScreens.Onboarding.route) { inclusive = true }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text(
                        text = if (pagerState.currentPage == pages.size - 1) "Empezar" else "Siguiente",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}