package com.domifiao.domifiao.ui.screens.colmado

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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

// Modelos internos para reporte
data class ReportOrder(
    val id: String,
    val total: Int,
    val timestamp: Long,
    val customerName: String,
    val products: List<ReportProduct>
)

data class ReportProduct(
    val name: String,
    val quantity: Int,
    val price: Int
)

data class ProductSummary(
    val name: String,
    val totalQuantity: Int,
    val totalRevenue: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoReportsScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estados
    var allOrders by remember { mutableStateOf<List<ReportOrder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Filtros
    var selectedFilter by remember { mutableStateOf("Hoy") }
    var selectedTab by remember { mutableIntStateOf(0) }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    // --- CARGAR VENTAS ---
    LaunchedEffect(colmadoId) {
        if (colmadoId != null) {
            db.collection("orders")
                .whereEqualTo("colmadoId", colmadoId)
                .whereEqualTo("status", "COMPLETED") // Solo ventas reales
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(500) // Límite razonable para análisis local
                .get()
                .addOnSuccessListener { snapshot ->
                    val orders = snapshot.documents.map { doc ->
                        @Suppress("UNCHECKED_CAST")
                        val rawProds = doc.get("products") as? List<Map<String, Any>> ?: emptyList()
                        val parsedProds = rawProds.map { p ->
                            ReportProduct(
                                name = p["name"] as? String ?: "Item",
                                quantity = (p["quantity"] as? Long)?.toInt() ?: 1,
                                price = (p["price"] as? Long)?.toInt() ?: 0
                            )
                        }

                        ReportOrder(
                            id = doc.id,
                            total = doc.getLong("total")?.toInt() ?: 0,
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            customerName = doc.getString("customerName") ?: "Cliente",
                            products = parsedProds
                        )
                    }
                    allOrders = orders
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        }
    }

    // --- CÁLCULOS DINÁMICOS ---
    val filteredOrders = remember(allOrders, selectedFilter) {
        val calendar = Calendar.getInstance()

        // Definir rangos
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val startOfWeek = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.timeInMillis

        when (selectedFilter) {
            "Hoy" -> allOrders.filter { it.timestamp >= startOfToday }
            "Semana" -> allOrders.filter { it.timestamp >= startOfWeek }
            "Mes" -> allOrders.filter { it.timestamp >= startOfMonth }
            else -> allOrders
        }
    }

    val totalRevenue = filteredOrders.sumOf { it.total }
    val totalCount = filteredOrders.size
    val averageTicket = if (totalCount > 0) totalRevenue / totalCount else 0
    val maxSale = if (filteredOrders.isNotEmpty()) filteredOrders.maxOf { it.total } else 0

    // Resumen de productos vendidos
    val productsSummary = remember(filteredOrders) {
        val map = mutableMapOf<String, ProductSummary>()
        filteredOrders.forEach { order ->
            order.products.forEach { prod ->
                val current = map[prod.name]
                if (current == null) {
                    map[prod.name] = ProductSummary(prod.name, prod.quantity, prod.price * prod.quantity)
                } else {
                    map[prod.name] = current.copy(
                        totalQuantity = current.totalQuantity + prod.quantity,
                        totalRevenue = current.totalRevenue + (prod.price * prod.quantity)
                    )
                }
            }
        }
        map.values.toList().sortedByDescending { it.totalRevenue }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reportes del Negocio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Filtros de Tiempo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selectedFilter == "Hoy", "Hoy") { selectedFilter = "Hoy" }
                    FilterChip(selectedFilter == "Semana", "Esta Semana") { selectedFilter = "Semana" }
                    FilterChip(selectedFilter == "Mes", "Este Mes") { selectedFilter = "Mes" }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Tarjeta Principal de Ingresos
                    item {
                        PremiumRevenueCard(totalRevenue, selectedFilter)
                    }

                    // 2. Métricas Secundarias
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStatCard(
                                title = "Pedidos",
                                value = "$totalCount",
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                color = Color(0xFF2196F3),
                                modifier = Modifier.weight(1f)
                            )
                            MiniStatCard(
                                title = "Promedio",
                                value = "RD$ $averageTicket",
                                icon = Icons.Default.Analytics,
                                color = Color(0xFFFF9800),
                                modifier = Modifier.weight(1f)
                            )
                            MiniStatCard(
                                title = "Venta Máx",
                                value = "RD$ $maxSale",
                                icon = Icons.Default.EmojiEvents,
                                color = Color(0xFF9C27B0),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 3. Tabs (Transacciones vs Productos)
                    item {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF4CAF50),
                            divider = {},
                            indicator = { tabPositions ->
                                SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        ) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Ventas") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Productos Top") })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (selectedTab == 0) {
                        if (filteredOrders.isEmpty()) {
                            item { EmptyState("No hay ventas en este periodo") }
                        } else {
                            items(filteredOrders) { order ->
                                TransactionItem(order)
                            }
                        }
                    } else {
                        if (productsSummary.isEmpty()) {
                            item { EmptyState("No hay datos de productos") }
                        } else {
                            items(productsSummary) { product ->
                                ProductSalesItem(product)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- COMPONENTES AUXILIARES ---

@Composable
fun FilterChip(isSelected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun PremiumRevenueCard(amount: Int, period: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.horizontalGradient(colors = listOf(Color(0xFF2E7D32), Color(0xFF66BB6A))))
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column {
                Text(text = "Ingresos Totales ($period)", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "RD$ $amount", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            }
            Icon(
                Icons.Default.AttachMoney, contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.align(Alignment.CenterEnd).size(64.dp)
            )
        }
    }
}

@Composable
fun MiniStatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun TransactionItem(order: ReportOrder) {
    val date = remember(order.timestamp) {
        SimpleDateFormat("dd/MM - hh:mm a", Locale.getDefault()).format(Date(order.timestamp))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.5.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(order.customerName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("RD$ ${order.total}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
        }
    }
}

@Composable
fun ProductSalesItem(product: ProductSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.5.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF4E342E) else Color(0xFFFFF3E0), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.List, null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("${product.totalQuantity} unidades", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("RD$ ${product.totalRevenue}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.BarChart, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
            Text(message, color = Color.Gray, fontSize = 12.sp)
        }
    }
}