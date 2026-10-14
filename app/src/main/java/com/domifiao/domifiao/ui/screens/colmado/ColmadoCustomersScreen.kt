package com.domifiao.domifiao.ui.screens.colmado

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class CustomerUI(
    val id: String,
    val name: String,
    val phone: String,
    val debt: Int,
    val limit: Int,
    val debtId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoCustomersScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var customers by remember { mutableStateOf<List<CustomerUI>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var totalDebtStreet by remember { mutableIntStateOf(0) }
    var pendingRequestsCount by remember { mutableIntStateOf(0) }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    LaunchedEffect(colmadoId) {
        if (colmadoId != null) {
            // Escuchar deudas
            db.collection("debts").whereEqualTo("colmadoId", colmadoId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                CustomerUI(
                                    id = doc.getString("clientId") ?: "",
                                    name = doc.getString("clientName") ?: "Cliente",
                                    phone = doc.getString("clientPhone") ?: "",
                                    debt = doc.getLong("amount")?.toInt() ?: 0,
                                    limit = doc.getLong("limit")?.toInt() ?: 0,
                                    debtId = doc.id
                                )
                            } catch (e: Exception) { null }
                        }.sortedByDescending { it.debt }
                        customers = list
                        totalDebtStreet = list.sumOf { it.debt }
                    }
                    isLoading = false
                }

            // Escuchar solicitudes pendientes
            db.collection("credit_requests")
                .whereEqualTo("colmadoId", colmadoId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snap, _ ->
                    pendingRequestsCount = snap?.size() ?: 0
                }
        }
    }

    val filteredList = customers.filter { it.name.contains(searchText, ignoreCase = true) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mis Clientes", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { navController.navigate(AppScreens.ColmadoCreditRequests.route) }, containerColor = Color(0xFF4CAF50), contentColor = Color.White) {
                    Icon(Icons.Default.PersonAdd, "Solicitudes")
                }
                if (pendingRequestsCount > 0) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(22.dp).clip(CircleShape).background(Color.Red).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                        Text(if(pendingRequestsCount > 9) "9+" else pendingRequestsCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Fiado en la Calle", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("RD$ $totalDebtStreet", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (totalDebtStreet > 0) Color.Red else Color(0xFF4CAF50))
                }
            }

            OutlinedTextField(
                value = searchText, onValueChange = { searchText = it }, placeholder = { Text("Buscar cliente...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4CAF50), unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface)
            )

            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredList) { customer ->
                        CustomerCard(customer) {
                            navController.navigate(AppScreens.ColmadoDebtHistory.createRoute(customer.id, customer.name, customer.debt, customer.debtId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerCard(customer: CustomerUI, onClick: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var isVerified by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(customer.id) {
        if (customer.id.isNotEmpty()) {
            db.collection("users").document(customer.id).get()
                .addOnSuccessListener { doc ->
                    isVerified = doc.getBoolean("isIdentityVerified") ?: false
                }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = "Advertencia", tint = Color.Red, modifier = Modifier.size(32.dp)) },
            title = { Text("Eliminar Cliente", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que quieres eliminar a ${customer.name}? Esta acción es irreversible y eliminará su registro de esta lista de clientes.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("debts").document(customer.debtId).delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Cliente eliminado exitosamente", Toast.LENGTH_SHORT).show()
                                showDeleteDialog = false
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Sí, Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = Color(0xFFFFA000), modifier = Modifier.size(36.dp)) },
            title = { Text("Acción Denegada", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("No puedes eliminar a ${customer.name}.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text("Este cliente tiene una deuda pendiente de RD$ ${customer.debt}. Debes liquidar el saldo total antes de poder eliminar su registro del sistema.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showErrorDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Entendido", fontWeight = FontWeight.Bold) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        if (isVerified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Verificado", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(customer.phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("RD$ ${customer.debt}", fontWeight = FontWeight.Black, color = if(customer.debt > 0) Color.Red else Color(0xFF4CAF50), fontSize = 16.sp)
                    Text("Límite: ${customer.limit}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { 
                        if (customer.debt > 0) {
                            showErrorDialog = true
                        } else {
                            showDeleteDialog = true 
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Borrar")
                }
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver Registros")
                }
            }
        }
    }
}
