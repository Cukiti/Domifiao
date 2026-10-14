package com.domifiao.domifiao.ui.screens.colmado

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.model.DebtTransaction
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoDebtHistoryScreen(
    navController: NavController,
    clientId: String,
    clientName: String,
    currentDebt: Int,
    debtId: String
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var transactions by remember { mutableStateOf<List<DebtTransaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var liveDebt by remember { mutableIntStateOf(currentDebt) }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    DisposableEffect(colmadoId, clientId) {
        var transListener: ListenerRegistration? = null
        var debtListener: ListenerRegistration? = null

        if (colmadoId != null) {
            debtListener = db.collection("debts").document(debtId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        liveDebt = snapshot.getLong("amount")?.toInt() ?: 0
                    }
                }

            transListener = db.collection("debt_transactions")
                .whereEqualTo("clientId", clientId)
                .whereEqualTo("colmadoId", colmadoId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                DebtTransaction(
                                    id = doc.id,
                                    colmadoId = doc.getString("colmadoId") ?: "",
                                    clientId = doc.getString("clientId") ?: "",
                                    type = doc.getString("type") ?: "INFO",
                                    amount = doc.getLong("amount")?.toInt() ?: 0,
                                    description = doc.getString("description") ?: "Sin detalles",
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            } catch (e: Exception) { null }
                        }
                        transactions = list.sortedByDescending { it.timestamp }
                    }
                    isLoading = false
                }
        } else { isLoading = false }

        onDispose {
            transListener?.remove()
            debtListener?.remove()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(clientName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Historial de Cuenta", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    navController.navigate(AppScreens.ColmadoAddTransaction.createRoute(clientId, clientName, liveDebt, debtId))
                },
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("REGISTRAR PAGO/CARGO", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Deuda Total Actual", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "RD$ $liveDebt", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if(liveDebt > 0) Color.Red else Color(0xFF4CAF50))
                    if (liveDebt == 0) Text("¡Cuenta al día!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else if (transactions.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MoneyOff, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Sin movimientos recientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(transactions) { trans ->
                        ColmadoHistoryItem(trans)
                    }
                }
            }
        }
    }
}

@Composable
fun ColmadoHistoryItem(trans: DebtTransaction) {
    val isPayment = trans.type == "ABONO" || trans.type == "PAGO"
    val color = if (isPayment) Color(0xFF4CAF50) else Color(0xFFD32F2F)
    val icon = if (isPayment) Icons.Default.AttachMoney else Icons.AutoMirrored.Filled.ReceiptLong
    val bgIcon = if (isPayment) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val sign = if (isPayment) "-" else "+"
    val title = if(isPayment) "Abono Recibido" else "Pedido Fiado"
    val dateString = DateFormat.format("dd MMM yyyy, hh:mm a", Date(trans.timestamp)).toString()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if(isSystemInDarkTheme()) bgIcon.copy(0.2f) else bgIcon), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(dateString, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if(trans.description.isNotEmpty()) Text(trans.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(text = "$sign RD$ ${trans.amount}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = color)
        }
    }
}
