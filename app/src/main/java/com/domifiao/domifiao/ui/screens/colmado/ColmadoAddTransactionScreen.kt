package com.domifiao.domifiao.ui.screens.colmado

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
fun ColmadoAddTransactionScreen(
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

    var amountText by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf("ABONO") } // ABONO o CARGO
    var isLoading by remember { mutableStateOf(false) }

    val isAbono = transactionType == "ABONO"
    val amount = amountText.toIntOrNull() ?: 0
    val newDebt = if (isAbono) (currentDebt - amount).coerceAtLeast(0) else (currentDebt + amount)
    val isValid = amount > 0 && (if (isAbono) amount <= currentDebt else true)

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (!isLoading) navigateBackSecurely() }

    fun addAmount(value: Int) {
        val current = amountText.toIntOrNull() ?: 0
        val nextVal = current + value
        if (isAbono && nextVal > currentDebt) {
            amountText = currentDebt.toString()
        } else {
            amountText = nextVal.toString()
        }
    }

    fun processTransaction() {
        if (colmadoId == null) return
        isLoading = true
        
        // Obtenemos los datos frescos de la deuda para validar el límite
        db.collection("debts").document(debtId).get().addOnSuccessListener { doc ->
            if (!doc.exists()) { 
                isLoading = false
                Toast.makeText(context, "Error: Registro de deuda no encontrado", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener 
            }
            
            val clientId = doc.getString("clientId") ?: ""
            val currentLimit = doc.getLong("limit")?.toInt() ?: 0
            val currentAmount = doc.getLong("amount")?.toInt() ?: 0
            
            // Validación de límite de crédito para CARGOS
            if (!isAbono && (currentAmount + amount) > currentLimit) {
                isLoading = false
                Toast.makeText(context, "Error: El cargo excede el límite de crédito (RD$ $currentLimit)", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            val batch = db.batch()
            val debtRef = db.collection("debts").document(debtId)
            val transRef = db.collection("debt_transactions").document()

            // Actualización atómica de la deuda
            batch.update(debtRef, "amount", FieldValue.increment((if (isAbono) -amount else amount).toLong()))
            batch.update(debtRef, "updatedAt", System.currentTimeMillis())

            val transData = hashMapOf(
                "colmadoId" to colmadoId, 
                "clientId" to clientId, 
                "type" to transactionType,
                "amount" to amount, 
                "description" to noteInput.ifEmpty { if (isAbono) "Abono manual" else "Cargo manual" },
                "timestamp" to System.currentTimeMillis()
            )
            batch.set(transRef, transData)

            batch.commit().addOnSuccessListener {
                if (clientId.isNotEmpty()) {
                    val finalDebt = if (isAbono) (currentAmount - amount) else (currentAmount + amount)
                    val notif = hashMapOf(
                        "title" to if (isAbono) "Pago Recibido" else "Nuevo Cargo",
                        "message" to if (isAbono) 
                            "Abonaste RD$ $amount. Nueva deuda: RD$ $finalDebt" 
                        else 
                            "Se cargaron RD$ $amount. Nueva deuda: RD$ $finalDebt",
                        "type" to "DEBT_UPDATE", 
                        "targetId" to debtId, 
                        "timestamp" to System.currentTimeMillis(), 
                        "read" to false
                    )
                    db.collection("users").document(clientId).collection("notifications").add(notif)
                }
                Toast.makeText(context, "Registro completado con éxito", Toast.LENGTH_SHORT).show()
                navigateBackSecurely()
            }.addOnFailureListener { e -> 
                isLoading = false
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show() 
            }
        }.addOnFailureListener { e ->
            isLoading = false
            Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val bgBrush = if (isAbono) {
        Brush.verticalGradient(listOf(if(isSystemInDarkTheme()) Color(0xFF1B5E20).copy(0.2f) else Color(0xFFE8F5E9), MaterialTheme.colorScheme.background))
    } else {
        Brush.verticalGradient(listOf(if(isSystemInDarkTheme()) Color(0xFFB71C1C).copy(0.2f) else Color(0xFFFFEBEE), MaterialTheme.colorScheme.background))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column { Text(clientName, fontWeight = FontWeight.Bold); Text("Gestión de Saldo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = { if (!isLoading) navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(bgBrush).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(8.dp)).background(if (isAbono) Color(0xFF4CAF50) else Color.Transparent).clickable { transactionType = "ABONO"; amountText = "" }, contentAlignment = Alignment.Center) { Text("ABONO (Pagar)", fontWeight = FontWeight.Bold, color = if (isAbono) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(8.dp)).background(if (!isAbono) Color(0xFFD32F2F) else Color.Transparent).clickable { transactionType = "CARGO"; amountText = "" }, contentAlignment = Alignment.Center) { Text("CARGO (Fiar)", fontWeight = FontWeight.Bold, color = if (!isAbono) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            Spacer(Modifier.height(32.dp))
            Text(if (isAbono) "Monto a Recibir" else "Monto a Fiar", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            TextField(
                value = amountText, onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                textStyle = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = if (isAbono) Color(0xFF4CAF50) else Color(0xFFD32F2F)),
                prefix = { Text("RD$ ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) },
                visualTransformation = { text ->
                    val original = text.text
                    if (original.isEmpty()) return@TextField androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
                    val formatted = try {
                        java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(original.toLong())
                    } catch (e: Exception) { original }
                    
                    val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
                        override fun originalToTransformed(offset: Int): Int {
                            if (offset == 0) return 0
                            val substring = original.take(offset)
                            val formattedSubstring = try {
                                java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(substring.toLong())
                            } catch (e: Exception) { substring }
                            return formattedSubstring.length
                        }
                        override fun transformedToOriginal(offset: Int): Int {
                            val substring = formatted.take(offset)
                            val unformattedSubstring = substring.replace(",", "")
                            return unformattedSubstring.length
                        }
                    }
                    androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), offsetMapping)
                },
                trailingIcon = {
                    if (amountText.isNotEmpty()) {
                        IconButton(onClick = { amountText = "" }) {
                            Icon(androidx.compose.ui.res.painterResource(id = com.domifiao.domifiao.R.drawable.ic_backspace), contentDescription = "Borrar", modifier = Modifier.size(28.dp), tint = Color(0xFFD32F2F))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            )
            Box(Modifier.width(150.dp).height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))

            Spacer(Modifier.height(16.dp))

            // Botones de acceso rápido
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAbono) {
                    ActionChipTransaction("Saldar Todo", Color(0xFF4CAF50), true) {
                        amountText = currentDebt.toString()
                    }
                }
                ActionChipTransaction("+100", MaterialTheme.colorScheme.onSurfaceVariant) { addAmount(100) }
                ActionChipTransaction("+200", MaterialTheme.colorScheme.onSurfaceVariant) { addAmount(200) }
                ActionChipTransaction("+500", MaterialTheme.colorScheme.onSurfaceVariant) { addAmount(500) }
                ActionChipTransaction("+1000", MaterialTheme.colorScheme.onSurfaceVariant) { addAmount(1000) }
                ActionChipTransaction("+2000", MaterialTheme.colorScheme.onSurfaceVariant) { addAmount(2000) }
            }

            Spacer(Modifier.height(24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(if (isAbono) Color(0xFFE8F5E9) else Color(0xFFFFEBEE), CircleShape), contentAlignment = Alignment.Center) { Icon(if (isAbono) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = if (isAbono) Color(0xFF4CAF50) else Color(0xFFD32F2F)) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { Text("Saldo Actual: RD$ $currentDebt", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Nuevo Saldo: RD$ $newDebt", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(value = noteInput, onValueChange = { noteInput = it }, label = { Text("Concepto o Nota") }, leadingIcon = { Icon(Icons.Default.Description, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface))

            Spacer(Modifier.weight(1f))
            Button(
                onClick = { processTransaction() }, enabled = isValid && !isLoading, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isAbono) Color(0xFF4CAF50) else Color(0xFFD32F2F), contentColor = Color.White)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("REGISTRAR ${if (isAbono) "PAGO" else "CARGO"}", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
fun ActionChipTransaction(label: String, color: Color, isPrimary: Boolean = false, onClick: () -> Unit) {
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
            fontSize = 14.sp
        )
    }
}

