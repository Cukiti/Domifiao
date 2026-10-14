package com.domifiao.domifiao.ui.screens.colmado

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.google.firebase.firestore.FirebaseFirestore

data class CreditRequestUI(
    val id: String,
    val clientId: String,
    val clientName: String,
    val clientPhone: String,
    val clientAddress: String,
    val paymentDeadline: String,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoCreditRequestsScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var requestsList by remember { mutableStateOf<List<CreditRequestUI>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

    // Diálogo aprobación
    var showApproveDialog by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<CreditRequestUI?>(null) }
    var limitInput by remember { mutableStateOf("") }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (!isProcessing) navigateBackSecurely() }

    LaunchedEffect(colmadoId) {
        if (colmadoId != null) {
            db.collection("credit_requests")
                .whereEqualTo("colmadoId", colmadoId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        requestsList = snapshot.documents.mapNotNull { doc ->
                            try {
                                CreditRequestUI(
                                    id = doc.id,
                                    clientId = doc.getString("clientId") ?: "",
                                    clientName = doc.getString("clientName") ?: "Cliente",
                                    clientPhone = doc.getString("clientPhone") ?: "",
                                    clientAddress = doc.getString("clientAddress") ?: "",
                                    paymentDeadline = doc.getString("paymentDeadline") ?: "No especificada",
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                )
                            } catch (e: Exception) { null }
                        }
                    }
                    isLoading = false
                }
        }
    }

    fun approveCredit() {
        val req = selectedRequest ?: return
        val limit = limitInput.toIntOrNull() ?: 0
        if (limit <= 0) return

        isProcessing = true
        val batch = db.batch()

        val debtRef = db.collection("debts").document()
        val debtData = hashMapOf(
            "clientId" to req.clientId, "colmadoId" to colmadoId, "clientName" to req.clientName,
            "clientPhone" to req.clientPhone, "amount" to 0, "limit" to limit,
            "updatedAt" to System.currentTimeMillis()
        )
        batch.set(debtRef, debtData)
        batch.update(db.collection("credit_requests").document(req.id), "status", "APPROVED")

        val notif = hashMapOf(
            "title" to "Crédito Aprobado", "message" to "Tienes un límite de RD$ $limit para fiar.",
            "type" to "DEBT_UPDATE", "targetId" to debtRef.id, "timestamp" to System.currentTimeMillis(), "read" to false
        )
        batch.set(db.collection("users").document(req.clientId).collection("notifications").document(), notif)

        batch.commit().addOnSuccessListener {
            isProcessing = false
            showApproveDialog = false
            Toast.makeText(context, "Crédito aprobado", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            isProcessing = false
            Toast.makeText(context, "Error al aprobar: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("ColmadoCreditRequests", "Error approving credit: ", e)
        }
    }

    fun rejectCredit(req: CreditRequestUI) {
        db.collection("credit_requests").document(req.id).update("status", "REJECTED")
            .addOnSuccessListener { Toast.makeText(context, "Solicitud rechazada", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Solicitudes de Fiado", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (!isProcessing) navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else if (requestsList.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.Gray, modifier = Modifier.size(80.dp))
                    Text("¡Todo al día!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("No hay solicitudes pendientes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(requestsList) { req ->
                        RequestCardPremium(req, onApprove = { selectedRequest = req; limitInput = ""; showApproveDialog = true }, onReject = { rejectCredit(req) })
                    }
                }
            }
        }
    }

    if (showApproveDialog) {
        AlertDialog(
            onDismissRequest = { if(!isProcessing) showApproveDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Aprobar Crédito", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text("Límite para ${selectedRequest?.clientName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = limitInput, onValueChange = { if(it.all { c -> c.isDigit() }) limitInput = it }, label = { Text("Límite (RD$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { approveCredit() }, enabled = limitInput.isNotEmpty() && !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { showApproveDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun RequestCardPremium(request: CreditRequestUI, onApprove: () -> Unit, onReject: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var isVerified by remember { mutableStateOf(false) }

    LaunchedEffect(request.clientId) {
        if (request.clientId.isNotEmpty()) {
            db.collection("users").document(request.clientId).get()
                .addOnSuccessListener { doc ->
                    isVerified = doc.getBoolean("isIdentityVerified") ?: false
                }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f)), Alignment.Center) { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(request.clientName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isVerified) Icons.Default.VerifiedUser else Icons.Default.Warning,
                            contentDescription = if (isVerified) "Verificado" else "No Verificado",
                            tint = if (isVerified) Color(0xFF4CAF50) else Color.Red,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isVerified) "Identidad Verificada" else "Identidad No Verificada",
                            fontSize = 12.sp,
                            color = if (isVerified) Color(0xFF4CAF50) else Color.Red,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(request.clientPhone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(request.clientAddress, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Rechazar") }
                Button(onClick = onApprove, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Aprobar", color = Color.White) }
            }
        }
    }
}
