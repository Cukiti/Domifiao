package com.domifiao.domifiao.ui.screens.client

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.firestore.FirebaseFirestore

data class Order(
    val id: String,
    val colmadoName: String,
    val total: Int,
    val status: String,
    val timestamp: Long,
    val itemsSummary: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientOrdersScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val sessionManager = SessionManager(context)
    val userId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var activeOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var historyOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Activos, 1 = Historial

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    LaunchedEffect(userId) {
        if (userId != null) {
            db.collection("orders")
                .whereEqualTo("clientId", userId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val allOrders = snapshot.documents.mapNotNull { doc ->
                            try {
                                Order(
                                    id = doc.id,
                                    colmadoName = doc.getString("colmadoName") ?: "Colmado",
                                    total = doc.getLong("total")?.toInt() ?: 0,
                                    status = doc.getString("status") ?: "PENDING",
                                    timestamp = doc.getLong("timestamp") ?: 0L,
                                    itemsSummary = (doc.get("products") as? List<*>)?.size?.let { "$it productos" } ?: ""
                                )
                            } catch (e: Exception) { null }
                        }.sortedByDescending { it.timestamp }

                        activeOrders = allOrders.filter { 
                            it.status != "COMPLETED" && it.status != "CANCELLED" 
                        }
                        historyOrders = allOrders.filter { 
                            it.status == "COMPLETED" || it.status == "CANCELLED" 
                        }
                    }
                    isLoading = false
                }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mis Pedidos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Activos", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Historial", fontWeight = FontWeight.Bold) }
                )
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val currentOrders = if (selectedTab == 0) activeOrders else historyOrders
                
                if (currentOrders.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (selectedTab == 0) "No tienes pedidos activos" else "No hay historial de pedidos",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(currentOrders) { order ->
                            ClientOrderCard(order) {
                                navController.navigate(AppScreens.ClientOrderDetail.createRoute(order.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClientOrderCard(order: Order, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(order.colmadoName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                StatusBadge(order.status)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(order.itemsSummary, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total: RD$ ${order.total}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("Ver Detalles", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (text, color) = when(status) {
        "PENDING" -> "Pendiente" to Color(0xFFFF9800)
        "WAITING_APPROVAL" -> "Esperando Aprobación" to Color(0xFFD32F2F)
        "PREPARING" -> "Preparando" to Color(0xFF2196F3)
        "DELIVERING" -> "En camino" to Color(0xFF9C27B0)
        "WAITING_CLIENT_CONFIRMATION" -> "Esperando confirmación" to Color(0xFF009688)
        "COMPLETED" -> "Entregado" to Color(0xFF4CAF50)
        "CANCELLED" -> "Cancelado" to Color.Red
        else -> status to Color.Gray
    }

    val currentStage = when(status) {
        "PENDING" -> 1
        "WAITING_APPROVAL" -> 2
        "PREPARING" -> 3
        "DELIVERING", "WAITING_CLIENT_CONFIRMATION" -> 4
        "COMPLETED" -> 5
        else -> 0
    }

    Column(horizontalAlignment = Alignment.End) {
        Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
            Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (currentStage > 0) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.width(60.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 1..5) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (i <= currentStage) color else Color.LightGray.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}
