package com.domifiao.domifiao.ui.screens.colmado

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoPaymentScreen(
    navController: NavController,
    debtId: String,
    clientName: String,
    currentDebt: Int
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estados
    var amountText by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // Cálculos visuales
    val amount = amountText.toIntOrNull() ?: 0
    val newDebt = (currentDebt - amount).coerceAtLeast(0)
    val isValid = amount > 0 && amount <= currentDebt

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (!isProcessing) navigateBackSecurely() }

    // --- FUNCIÓN DE PAGO ---
    fun processPayment() {
        if (colmadoId == null) return
        if (!isValid) {
            Toast.makeText(context, "Monto inválido o excede la deuda", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        val batch = db.batch()

        // 1. Descontar de la Deuda
        val debtRef = db.collection("debts").document(debtId)
        batch.update(debtRef, "amount", FieldValue.increment(-amount.toLong()))
        batch.update(debtRef, "updatedAt", System.currentTimeMillis())

        // 2. Registrar en Historial
        val transRef = db.collection("debt_transactions").document()

        // Obtenemos el clientId directamente del documento de deuda para asegurar integridad
        db.collection("debts").document(debtId).get()
            .addOnSuccessListener { debtDoc ->
                if (!debtDoc.exists()) {
                    isProcessing = false
                    Toast.makeText(context, "Error: Cuenta no encontrada", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val clientId = debtDoc.getString("clientId") ?: ""

                val transactionData = hashMapOf(
                    "colmadoId" to colmadoId,
                    "clientId" to clientId,
                    "type" to "ABONO", // Tipo específico para pagos
                    "amount" to amount,
                    "description" to noteInput.ifEmpty { "Abono en efectivo" },
                    "timestamp" to System.currentTimeMillis()
                )
                batch.set(transRef, transactionData)

                // 3. Ejecutar y Notificar
                batch.commit().addOnSuccessListener {
                    isProcessing = false

                    if (clientId.isNotEmpty()) {
                        val notif = hashMapOf(
                            "title" to "Pago Recibido",
                            "message" to "Abonaste RD$ $amount. Tu deuda bajó a RD$ $newDebt.",
                            "type" to "DEBT_UPDATE",
                            "targetId" to debtId,
                            "timestamp" to System.currentTimeMillis(),
                            "read" to false
                        )
                        db.collection("users").document(clientId).collection("notifications").add(notif)
                    }

                    Toast.makeText(context, "Abono registrado correctamente", Toast.LENGTH_SHORT).show()
                    navigateBackSecurely()
                }.addOnFailureListener {
                    isProcessing = false
                    Toast.makeText(context, "Error al guardar pago", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                isProcessing = false
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
    }

    // Funciones Helper para Chips
    fun addAmount(value: Int) {
        val current = amountText.toIntOrNull() ?: 0
        // Si al sumar se pasa, ponemos el máximo posible (la deuda actual)
        val nextVal = (current + value).coerceAtMost(currentDebt)
        amountText = nextVal.toString()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Registrar Abono", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 1. INPUT GIGANTE
            Text("Monto a recibir", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

            TextField(
                value = amountText,
                onValueChange = { if (it.all { char -> char.isDigit() }) amountText = it },
                textStyle = TextStyle(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF4CAF50)
                ),
                prefix = {
                    Text("RD$ ", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.width(120.dp).height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))

            Spacer(modifier = Modifier.height(24.dp))

            // 2. CHIPS DE ACCIÓN RÁPIDA
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ActionChip("Saldar Todo", Color(0xFF4CAF50), true) {
                        amountText = currentDebt.toString()
                    }
                }
                item { ActionChip("+100", MaterialTheme.colorScheme.onSurfaceVariant) { addAmount(100) } }
                item { ActionChip("+500", MaterialTheme.colorScheme.onSurfaceVariant) { addAmount(500) } }
                item { ActionChip("+1000", MaterialTheme.colorScheme.onSurfaceVariant) { addAmount(1000) } }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. TARJETA DE RESUMEN INTELIGENTE
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowDownward, null, tint = Color(0xFF4CAF50))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Deuda Actual", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("RD$ $currentDebt", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Nueva Deuda", fontSize = 12.sp, color = if(androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF2E7D32))
                        Text("RD$ $newDebt", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if(androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF2E7D32))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. NOTA OPCIONAL
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Nota opcional (Ej: Pagó con billete de 2000)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Description, null) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // 5. BOTÓN DE CONFIRMACIÓN
            Button(
                onClick = { processPayment() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isValid) Color(0xFF4CAF50) else Color.Gray,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = isValid && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONFIRMAR ABONO", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ActionChip(label: String, color: Color, isPrimary: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isPrimary) color else color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(50),
        border = if (!isPrimary) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)) else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isPrimary) Color.White else color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
