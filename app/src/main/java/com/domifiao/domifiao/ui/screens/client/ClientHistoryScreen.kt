package com.domifiao.domifiao.ui.screens.client

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

// Modelo de datos para el historial
// Modelo para producto en historial
data class HistoryProductItem(
    val name: String,
    val quantity: Double,
    val quantityDisplay: String,
    val price: Double
) {
    fun calculateTotal(): Double = price * quantity
    fun formatTotal(): String = "RD$ ${calculateTotal().toInt()}"
}

data class OrderHistoryItem(
    val id: String,
    val colmadoId: String,
    val colmadoName: String,
    val itemsSummary: String,
    val products: List<HistoryProductItem>,
    val total: Int,
    val paymentMethod: String,
    val status: String,
    val timestamp: Long,
    val rating: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid

    var historyList by remember { mutableStateOf<List<OrderHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalSpent by remember { mutableIntStateOf(0) }

    // Estados para el diálogo de calificación
    var showRatingDialog by remember { mutableStateOf(false) }
    var selectedOrderForRating by remember { mutableStateOf<OrderHistoryItem?>(null) }
    var currentRating by remember { mutableIntStateOf(0) }

    // --- NAVEGACIÓN DEFENSIVA ---
    fun navigateBackSecurely() {
        if (!navController.popBackStack()) {
            navController.navigate(AppScreens.ClientHome.route) {
                popUpTo(AppScreens.ClientHome.route) { inclusive = true }
            }
        }
    }
    BackHandler {
        if (showRatingDialog) showRatingDialog = false else navigateBackSecurely()
    }

    // --- ESCUCHAR HISTORIAL EN TIEMPO REAL ---
    LaunchedEffect(Unit) {
        if (userId != null) {
            db.collection("orders")
                .whereEqualTo("clientId", userId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        isLoading = false
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val orders = snapshot.documents.map { doc ->
                            // Parsear productos
                            @Suppress("UNCHECKED_CAST")
                            val rawProducts = doc.get("products") as? List<Map<String, Any>> ?: emptyList()
                            val productsList = rawProducts.map { prod ->
                                val rawPrice = prod["price"]
                                val price = when (rawPrice) {
                                    is Double -> rawPrice
                                    is Long -> rawPrice.toDouble()
                                    is Number -> rawPrice.toDouble()
                                    else -> 0.0
                                }
                                val rawQty = prod["quantity"]
                                val quantity = when (rawQty) {
                                    is Double -> rawQty
                                    is Long -> rawQty.toDouble()
                                    is Number -> rawQty.toDouble()
                                    else -> 0.0
                                }
                                HistoryProductItem(
                                    name = prod["name"] as? String ?: "",
                                    quantity = quantity,
                                    quantityDisplay = prod["quantityDisplay"] as? String ?: quantity.toInt().toString(),
                                    price = price
                                )
                            }

                            // Parsear total
                            val rawTotal = doc.get("total")
                            val total = when (rawTotal) {
                                is Double -> rawTotal.toInt()
                                is Long -> rawTotal.toInt()
                                is Number -> rawTotal.toInt()
                                else -> 0
                            }

                            OrderHistoryItem(
                                id = doc.id,
                                colmadoId = doc.getString("colmadoId") ?: "",
                                colmadoName = doc.getString("colmadoName") ?: "Colmado Desconocido",
                                itemsSummary = doc.getString("itemsSummary") ?: "Sin detalles",
                                products = productsList,
                                total = total,
                                paymentMethod = doc.getString("paymentMethod") ?: "Efectivo",
                                status = doc.getString("status") ?: "PENDING",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                rating = doc.getLong("rating")?.toInt() ?: 0
                            )
                        }

                        // Ordenar por fecha (Mas reciente primero)
                        historyList = orders.sortedByDescending { it.timestamp }

                        // Calcular total gastado historico (Solo completados)
                        totalSpent = orders.filter { it.status == "COMPLETED" }.sumOf { it.total }

                        isLoading = false
                    }
                }
        } else {
            isLoading = false
        }
    }

    // --- FUNCIÓN PARA GUARDAR CALIFICACIÓN ---
    // --- LÓGICA DE CALIFICACIÓN CORREGIDA ---
    fun submitRating() {
        val order = selectedOrderForRating ?: return
        if (currentRating == 0) return

        db.runTransaction { transaction ->
            val orderRef = db.collection("orders").document(order.id)
            val colmadoRef = db.collection("colmados_requests").document(order.colmadoId)

            val colmadoSnapshot = transaction.get(colmadoRef)

            // Valores actuales (con defaults a 0 si es nuevo)
            val currentReviewCount = colmadoSnapshot.getLong("reviewCount") ?: 0L
            val currentTotalStars = colmadoSnapshot.getLong("totalStars") ?: 0L

            // Nuevos valores
            val newReviewCount = currentReviewCount + 1
            val newTotalStars = currentTotalStars + currentRating
            // Promedio simple: Total Estrellas / Total Votos
            val newAverageRating = newTotalStars.toDouble() / newReviewCount.toDouble()

            transaction.update(orderRef, "rating", currentRating)

            transaction.update(colmadoRef, "reviewCount", newReviewCount)
            transaction.update(colmadoRef, "totalStars", newTotalStars)
            transaction.update(colmadoRef, "rating", newAverageRating)
        }.addOnSuccessListener {
            Toast.makeText(context, "¡Gracias por calificar!", Toast.LENGTH_SHORT).show()
            showRatingDialog = false
        }.addOnFailureListener {
            Toast.makeText(context, "Error al calificar", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Historial de Compras", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tarjeta de Resumen de Gastos
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(if (isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.History, null, tint = Color(0xFF4CAF50))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Total Gastado (Completados)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("RD$ $totalSpent", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32))
                    }
                }
            }


            Text(
                text = "Pedidos Recientes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                }
            } else if (historyList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Aún no has realizado pedidos.", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(historyList) { order ->
                        HistoryCard(
                            order = order,
                            onRateClick = {
                                selectedOrderForRating = order
                                currentRating = 0
                                showRatingDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // --- DIÁLOGO DE CALIFICACIÓN ---
    if (showRatingDialog) {
        Dialog(onDismissRequest = { showRatingDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Califica tu experiencia", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "¿Qué tal fue el servicio de ${selectedOrderForRating?.colmadoName}?",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { currentRating = star }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { submitRating() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        enabled = currentRating > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enviar Calificación")
                    }

                    // BOTÓN EXPLÍCITO: Cerrar sin calificar
                    TextButton(
                        onClick = { showRatingDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar sin calificar", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCard(order: OrderHistoryItem, onRateClick: () -> Unit) {
    val dateString = DateFormat.format("dd/MM/yyyy hh:mm aa", Date(order.timestamp)).toString()
    val isCredit = order.paymentMethod == "Fiado"
    val isCompleted = order.status == "COMPLETED"

    val statusColor = when (order.status) {
        "COMPLETED" -> Color(0xFF4CAF50)
        "CANCELLED" -> Color.Red
        "DELIVERING" -> Color(0xFF9C27B0)
        else -> Color(0xFF2196F3)
    }

    val statusText = when (order.status) {
        "PENDING" -> "Pendiente"
        "PREPARING" -> "Preparando"
        "DELIVERING" -> "En Camino"
        "WAITING_CLIENT_CONFIRMATION" -> "Esperando confirmación"
        "COMPLETED" -> "Entregado"
        "CANCELLED" -> "Cancelado"
        "WAITING_APPROVAL" -> "Esperando Aprobación"
        else -> order.status
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Encabezado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.colmadoName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateString,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = statusText.uppercase(),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // Lista de productos con precios
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Productos:", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }

                order.products.forEach { product ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${product.quantityDisplay} ${product.name}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = product.formatTotal(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pie: Pago, Total y Botón
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCredit) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = if (isCredit) Color(0xFFFF9800) else Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RD$ ${order.total}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCredit) {
                        Text(" (Fiado)", fontSize = 12.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                    }
                }

                // Botón de Calificar (Solo si está completado y no calificado aún)
                if (isCompleted && order.rating == 0) {
                    Button(
                        onClick = onRateClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                    ) {
                        Text("Calificar", color = Color.Black, fontSize = 12.sp)
                    }
                } else if (order.rating > 0) {
                    // Si ya calificó, mostramos las estrellas
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${order.rating}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}