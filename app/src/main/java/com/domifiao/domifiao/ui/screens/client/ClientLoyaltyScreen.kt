package com.domifiao.domifiao.ui.screens.client

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.model.LoyaltyAccount
import com.domifiao.domifiao.model.LoyaltyTier
import com.domifiao.domifiao.model.PointsTransaction
import com.domifiao.domifiao.model.TransactionType
import com.domifiao.domifiao.utils.LoyaltyManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ClientLoyaltyScreen - Pantalla del Sistema de Puntos
 *
 * Muestra:
 * - Badge del tier actual
 * - Puntos disponibles
 * - Progreso al siguiente nivel
 * - Beneficios actuales
 * - Historial de transacciones
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientLoyaltyScreen(navController: NavController) {
    val context = LocalContext.current
    val sessionManager = SessionManager(context)
    val userId = sessionManager.getUserId() ?: ""

    var loyaltyAccounts by remember { mutableStateOf<List<LoyaltyAccount>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Cargar todas las cuentas de lealtad del usuario
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            LoyaltyManager.getAllLoyaltyAccounts(
                userId = userId,
                onSuccess = { accounts ->
                    loyaltyAccounts = accounts
                    isLoading = false
                },
                onError = {
                    isLoading = false
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mis Puntos y Recompensas",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF5F5F5)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (loyaltyAccounts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(Color.White)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Sin Programas de Puntos",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Aún no tienes puntos con ningún colmado.\nHaz tu primer pedido en un colmado con programa de puntos para comenzar.",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(loyaltyAccounts) { account ->
                        // Título del colmado
                        Text(
                            account.colmadoName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        // Tier Badge
                        TierBadgeCard(account)

                        // Progreso al siguiente nivel
                        NextTierProgressCard(account)

                        // Beneficios actuales
                        CurrentBenefitsCard(account)

                        // Estadísticas
                        StatsCard(account)

                        // Espacio entre colmados
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TierBadgeCard(account: LoyaltyAccount) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(account.tier.color)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji del tier
            Text(
                account.tier.emoji,
                fontSize = 64.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Nombre del tier
            Text(
                account.tier.displayName.uppercase(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Puntos disponibles
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${account.currentPoints}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                "puntos disponibles",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun NextTierProgressCard(account: LoyaltyAccount) {
    val nextTier = account.tier.nextTier()

    if (nextTier != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Siguiente nivel: ${nextTier.emoji} ${nextTier.displayName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val pointsNeeded = account.tier.pointsToNextTier(account.totalPointsEarned) ?: 0
                    Text(
                        "$pointsNeeded pts",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { account.tierProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    color = Color(nextTier.color),
                    trackColor = Color(0xFFE0E0E0),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "${(account.tierProgress * 100).toInt()}% completado",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    } else {
        // Ya es nivel máximo
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(Color(0xFFE8F5E9))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "¡Nivel Máximo Alcanzado!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        "Has alcanzado el nivel más alto",
                        fontSize = 14.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentBenefitsCard(account: LoyaltyAccount) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Tus Beneficios",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Descuento actual
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F8E9), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF4CAF50), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${account.tier.discountPercentage}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Descuento en todos tus pedidos",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Se aplica automáticamente",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(account: LoyaltyAccount) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Tus Estadísticas",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.ShoppingBag,
                    value = "${account.ordersCompleted}",
                    label = "Pedidos",
                    color = Color(0xFF2196F3)
                )

                StatItem(
                    icon = Icons.Default.Star,
                    value = "${account.totalPointsEarned}",
                    label = "Puntos Totales",
                    color = Color(0xFFFFC107)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Total gastado: RD$ ${String.format("%.2f", account.totalSpent)}",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun TransactionItem(transaction: PointsTransaction) {
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono según tipo
            val (iconColor, sign) = when (transaction.type) {
                TransactionType.EARNED -> Color(0xFF4CAF50) to "+"
                TransactionType.REDEEMED -> Color(0xFFF44336) to "-"
                TransactionType.BONUS -> Color(0xFF2196F3) to "+"
                TransactionType.EXPIRED -> Color.Gray to "-"
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$sign${transaction.points.toString().replace("-", "")}",
                    color = iconColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    dateFormatter.format(Date(transaction.timestamp)),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
