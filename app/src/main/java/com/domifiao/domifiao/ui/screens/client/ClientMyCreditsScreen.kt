package com.domifiao.domifiao.ui.screens.client

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Store
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ClientCreditAccount(
    val id: String,
    val colmadoId: String,
    val colmadoName: String,
    val amount: Int,
    val limit: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientMyCreditsScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val userId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var accounts by remember { mutableStateOf<List<ClientCreditAccount>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    LaunchedEffect(userId) {
        if (userId != null) {
            db.collection("debts")
                .whereEqualTo("clientId", userId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val accList = mutableListOf<ClientCreditAccount>()
                            for (doc in snapshot.documents) {
                                try {
                                    val cId = doc.getString("colmadoId") ?: ""
                                    var cName = doc.getString("colmadoName")
                                    if (cName == null || cName == "Colmado" || cName.isEmpty()) {
                                        try {
                                            val cDoc = db.collection("colmados_requests").document(cId).get().await()
                                            cName = cDoc.getString("name") ?: "Colmado"
                                        } catch (e: Exception) {
                                            cName = "Colmado"
                                        }
                                    }
                                    accList.add(
                                        ClientCreditAccount(
                                            id = doc.id,
                                            colmadoId = cId,
                                            colmadoName = cName ?: "Colmado",
                                            amount = doc.getLong("amount")?.toInt() ?: 0,
                                            limit = doc.getLong("limit")?.toInt() ?: 0
                                        )
                                    )
                                } catch (e: Exception) { }
                            }
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                accounts = accList.sortedByDescending { it.amount }
                                isLoading = false
                            }
                        }
                    } else {
                        isLoading = false
                    }
                }
        } else {
            isLoading = false
        }
    }

    val totalDebt = accounts.sumOf { it.amount }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mis Créditos", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            // Tarjeta de Deuda Total
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = if(isSystemInDarkTheme()) Color(0xFFB71C1C).copy(0.2f) else Color(0xFFFFEBEE)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Deuda Total Acumulada", fontSize = 14.sp, color = if(isSystemInDarkTheme()) Color(0xFFFFCDD2) else Color(0xFFB71C1C))
                    Spacer(Modifier.height(4.dp))
                    Text("RD$ $totalDebt", fontSize = 36.sp, fontWeight = FontWeight.Black, color = if(isSystemInDarkTheme()) Color(0xFFEF9A9A) else Color(0xFFD32F2F))
                }
            }

            Text("Cuentas por Colmado", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else if (accounts.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CreditCard, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No tienes créditos activos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(accounts) { account ->
                        CreditAccountCard(account) {
                            navController.navigate(AppScreens.ClientDebtDetail.createRoute(account.colmadoId, account.colmadoName, account.amount, account.limit))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreditAccountCard(account: ClientCreditAccount, onClick: () -> Unit) {
    val progress = if(account.limit > 0) account.amount.toFloat() / account.limit.toFloat() else 0f
    val color = if(progress > 0.8f) Color.Red else if(progress > 0.5f) Color(0xFFFF9800) else Color(0xFF4CAF50)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f)), Alignment.Center) {
                    Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.colmadoName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Límite: RD$ ${account.limit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("RD$ ${account.amount}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = if(account.amount > 0) Color.Red else Color(0xFF4CAF50))
            }
            
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = color,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
