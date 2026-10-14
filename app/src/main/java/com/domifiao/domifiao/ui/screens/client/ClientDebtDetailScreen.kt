package com.domifiao.domifiao.ui.screens.client

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.model.DebtTransaction
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDebtDetailScreen(
    navController: NavController,
    colmadoId: String,
    colmadoName: String,
    currentDebt: Int,
    limit: Int
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid

    val lifecycleOwner = LocalLifecycleOwner.current

    var transactions by remember { mutableStateOf<List<DebtTransaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var liveDebt by remember { mutableIntStateOf(currentDebt) }
    var selectedFilter by remember { mutableIntStateOf(0) } // 0=Todo, 1=Abonos, 2=Compras

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    // --- CARGA DE DATOS ---
    DisposableEffect(userId, colmadoId) {
        var debtListener: ListenerRegistration? = null
        var historyListener: ListenerRegistration? = null

        if (userId != null && colmadoId.isNotEmpty()) {
            // 1. Escuchar Saldo en Tiempo Real
            debtListener = db.collection("debts")
                .whereEqualTo("clientId", userId)
                .whereEqualTo("colmadoId", colmadoId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && !snapshot.isEmpty) {
                        liveDebt = snapshot.documents[0].getLong("amount")?.toInt() ?: 0
                    }
                }

            // 2. Escuchar Historial
            historyListener = db.collection("debt_transactions")
                .whereEqualTo("clientId", userId)
                .whereEqualTo("colmadoId", colmadoId)
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                DebtTransaction(
                                    id = doc.id,
                                    colmadoId = doc.getString("colmadoId") ?: "",
                                    clientId = doc.getString("clientId") ?: "",
                                    type = doc.getString("type") ?: "INFO",
                                    amount = doc.getLong("amount")?.toInt() ?: 0,
                                    description = doc.getString("description") ?: "Detalle no disponible",
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            } catch (e: Exception) { null }
                        }
                        // ORDENAMIENTO
                        transactions = list.sortedByDescending { it.timestamp }
                        isLoading = false
                    } else {
                        isLoading = false
                    }
                }
        } else {
            isLoading = false
        }

        onDispose {
            debtListener?.remove()
            historyListener?.remove()
        }
    }

    // Filtrado local
    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            1 -> transactions.filter { it.type == "ABONO" || it.type == "PAGO" }
            2 -> transactions.filter { it.type == "COMPRA" }
            else -> transactions
        }
    }

    // Agrupación por fecha
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy {
            SimpleDateFormat("MMMM yyyy", Locale("es", "ES")).format(Date(it.timestamp))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(colmadoName, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Estado de Cuenta", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (userId != null) {
                            navController.navigate(AppScreens.Chat.createColmadoChat(colmadoId))
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, "Contactar", tint = Color(0xFF2196F3))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            // 1. TARJETA DE CRÉDITO PREMIUM
            PremiumDebtCard(liveDebt, limit)

            // 2. FILTROS (Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selectedFilter == 0, onClick = { selectedFilter = 0 }, label = { Text("Todo") })
                FilterChip(selected = selectedFilter == 1, onClick = { selectedFilter = 1 }, label = { Text("Pagos") })
                FilterChip(selected = selectedFilter == 2, onClick = { selectedFilter = 2 }, label = { Text("Compras") })
            }

            // 3. LISTA TIMELINE
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                }
            } else if (filteredTransactions.isEmpty()) {
                EmptyHistoryState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    groupedTransactions.forEach { (header, transList) ->
                        item {
                            Text(
                                text = header.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }

                        items(transList) { trans ->
                            TimelineTransactionItem(trans, colmadoName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumDebtCard(currentDebt: Int, limit: Int) {
    val available = (limit - currentDebt).coerceAtLeast(0)
    val progress = if (limit > 0) currentDebt.toFloat() / limit.toFloat() else 0f

    // Gradiente dinámico según salud de la deuda
    val bgBrush = if (progress > 0.9f) {
        Brush.horizontalGradient(listOf(Color(0xFFD32F2F), Color(0xFFEF5350))) // Rojo (Peligro)
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF1976D2), Color(0xFF42A5F5))) // Azul (Normal)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
            // Decoración de fondo
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.1f),
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 30.dp)
            )

            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tarjeta Fiao", color = Color.White.copy(0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.Nfc, null, tint = Color.White.copy(0.8f))
                }

                Column {
                    Text("Deuda Total", color = Color.White.copy(0.9f), fontSize = 12.sp)
                    Text("RD$ $currentDebt", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Límite: RD$ $limit", color = Color.White.copy(0.8f), fontSize = 12.sp)
                        Text("Disponible: RD$ $available", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // IMPORTANTE: Convertimos el progreso a Float directo para evitar problemas de compatibilidad
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if(progress > 0.9f) Color(0xFFFFC107) else Color.White,
                        trackColor = Color.Black.copy(0.2f),
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineTransactionItem(trans: DebtTransaction, colmadoName: String) {
    var expanded by remember { mutableStateOf(false) }
    val isDebtIncrease = trans.type == "COMPRA" || trans.type == "CARGO"
    val color = if (isDebtIncrease) Color(0xFFD32F2F) else Color(0xFF4CAF50)
    val icon = if (isDebtIncrease) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Default.AttachMoney
    val sign = if (isDebtIncrease) "+" else "-"
    val bgIcon = if (isDebtIncrease) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    val dateString = DateFormat.format("dd MMM, hh:mm a", Date(trans.timestamp)).toString()

    // IntrinsicSize.Min asegura que la línea vertical mida lo mismo que la tarjeta
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // Línea de tiempo
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.LightGray.copy(0.5f))
            )
        }

        // Tarjeta de Contenido
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(0.dp), // Flat para estilo timeline
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .animateContentSize()
                .clickable { expanded = !expanded }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(bgIcon),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (trans.type) {
                                "COMPRA" -> "Compra Fiada"
                                "CARGO" -> "Cargo Fiao"
                                else -> "Abono / Pago"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = colmadoName,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(dateString, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Text(
                        text = "$sign RD$ ${trans.amount}",
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                if (expanded || trans.description.length < 30) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = trans.description.ifEmpty { "Sin detalles" },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.History, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No hay movimientos", color = Color.Gray, fontWeight = FontWeight.Bold)
        Text("Tu historial de crédito aparecerá aquí", fontSize = 12.sp, color = Color.LightGray)
    }
}