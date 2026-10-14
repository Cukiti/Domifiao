package com.domifiao.domifiao.ui.screens.colmado

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.domifiao.domifiao.navigation.AppScreens

data class DashboardItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val count: Int = 0
)

@Composable
fun ColmadoHomeScreen(
    navController: NavController,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    viewModel: ColmadoHomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    BackHandler {
        activity?.finish()
    }

    val uiState by viewModel.uiState.collectAsState()

    fun toggleOpenStatus() {
        viewModel.toggleStoreStatus { newStatus ->
            Toast.makeText(context, if (newStatus) "Colmado ABIERTO" else "Colmado CERRADO", Toast.LENGTH_SHORT).show()
        }
    }

    val menuItems = listOf(
        DashboardItem("Inventario", Icons.Default.Inventory, MaterialTheme.colorScheme.primary),
        DashboardItem("Pedidos", Icons.Default.ReceiptLong, MaterialTheme.colorScheme.secondary, count = uiState.pendingOrdersCount),
        DashboardItem("Libreta Fiado", Icons.Default.MenuBook, MaterialTheme.colorScheme.tertiary, count = uiState.pendingRequestsCount),
        DashboardItem("Reportes", Icons.Default.BarChart, Color(0xFF9C27B0)),
        DashboardItem("Chat", Icons.Default.Chat, Color(0xFF795548), count = uiState.unreadMessagesCount),
        DashboardItem("Ajustes", Icons.Default.Settings, Color(0xFF607D8B))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // HEADER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.colmadoName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { navController.navigate(AppScreens.ColmadoNotifications.route) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        BadgedBox(badge = { if(uiState.unreadNotificationsCount > 0) Badge { Text(uiState.unreadNotificationsCount.toString()) } }) {
                            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Switch Abierto/Cerrado (MEJORADO VISUALMENTE)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isOpen) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.clickable { toggleOpenStatus() },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (uiState.isOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (uiState.isOpen) MaterialTheme.colorScheme.primary else Color.Gray))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isOpen) "ABIERTO" else "CERRADO", 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (uiState.isOpen) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjetas de Resumen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SummaryCard(
                title = "Ventas Hoy",
                value = "RD$ ${uiState.todaySales}",
                icon = Icons.Default.AttachMoney,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Pendientes",
                value = "${uiState.pendingOrdersCount} Pedidos",
                icon = Icons.Default.DeliveryDining,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grid de Menú
        Text(
            text = "Gestión Rápida",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(menuItems) { item ->
                DashboardCard(
                    item = item,
                    onClick = {
                        when (item.title) {
                            "Inventario" -> navController.navigate(AppScreens.Inventory.route)
                            "Pedidos" -> navController.navigate(AppScreens.ColmadoOrders.route)
                            "Libreta Fiado" -> navController.navigate(AppScreens.ColmadoCustomers.route)
                            "Ajustes" -> navController.navigate(AppScreens.ColmadoSettings.route)
                            "Reportes" -> navController.navigate(AppScreens.ColmadoReports.route)
                            "Chat" -> navController.navigate(AppScreens.ColmadoChatList.route)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DashboardCard(item: DashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .height(140.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(item.color.copy(alpha = if (isSystemInDarkTheme()) 0.2f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.color,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (item.count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Red),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.count.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
