package com.domifiao.domifiao.ui.screens.client

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.model.CartManager
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientCheckoutScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid
    val sessionManager = SessionManager(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    // Datos del carrito
    val cartItems = remember { CartManager.items }
    val colmadoId = remember { CartManager.currentColmadoId }
    val subtotal = remember { CartManager.items.sumOf { it.calculateTotalPrice() } }

    // Estados de perfil
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var isIdentityVerified by remember { mutableStateOf(false) }

    // Estados de Colmado
    var isColmadoOpen by remember { mutableStateOf(true) }
    var deliveryFee by remember { mutableIntStateOf(0) }
    var hasCreditAccount by remember { mutableStateOf(false) }
    var creditLimit by remember { mutableIntStateOf(0) }
    var currentDebt by remember { mutableIntStateOf(0) }

    // Estado del pedido
    var note by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Efectivo") }
    var isOrdering by remember { mutableStateOf(false) }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (!isOrdering) navigateBackSecurely() }

    // Cargar datos
    LaunchedEffect(userId, colmadoId) {
        if (userId != null) {
            db.collection("users").document(userId).get().addOnSuccessListener { doc ->
                address = doc.getString("address") ?: ""
                phone = doc.getString("phone") ?: ""
                customerName = doc.getString("name") ?: "Cliente"
                isIdentityVerified = doc.getBoolean("isIdentityVerified") ?: false
            }

            if (colmadoId != null) {
                db.collection("colmados_requests").document(colmadoId).get().addOnSuccessListener { doc ->
                    isColmadoOpen = doc.getBoolean("isOpen") ?: true
                    deliveryFee = doc.getLong("deliveryFee")?.toInt() ?: 0
                }

                db.collection("debts")
                    .whereEqualTo("clientId", userId)
                    .whereEqualTo("colmadoId", colmadoId)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (!snap.isEmpty) {
                            val d = snap.documents[0]
                            hasCreditAccount = true
                            creditLimit = d.getLong("limit")?.toInt() ?: 0
                            currentDebt = d.getLong("amount")?.toInt() ?: 0
                        }
                    }
            }
        }
    }

    val total = subtotal + deliveryFee
    val availableCredit = (creditLimit - currentDebt).coerceAtLeast(0)

    fun placeOrder() {
        if (userId == null || colmadoId == null) return
        if (!isColmadoOpen) { Toast.makeText(context, "El colmado está cerrado", Toast.LENGTH_LONG).show(); return }
        if (address.isEmpty() || phone.isEmpty()) { Toast.makeText(context, "Faltan datos en tu perfil", Toast.LENGTH_LONG).show(); return }

        if (paymentMethod == "Fiado") {
            if (!hasCreditAccount) { Toast.makeText(context, "No tienes crédito aquí", Toast.LENGTH_SHORT).show(); return }
            if (total > availableCredit) { Toast.makeText(context, "Crédito insuficiente", Toast.LENGTH_SHORT).show(); return }
        }

        isOrdering = true
        db.runTransaction { transaction ->
            val colRef = db.collection("colmados_requests").document(colmadoId)
            if (!(transaction.get(colRef).getBoolean("isOpen") ?: true)) {
                throw FirebaseFirestoreException("Cerrado", FirebaseFirestoreException.Code.ABORTED)
            }

            // FIX: El pedido siempre inicia en PENDING hasta que el colmado lo acepte o cotice
            val initialStatus = "PENDING"

            val newOrderRef = db.collection("orders").document()
            val orderData = hashMapOf(
                "id" to newOrderRef.id, "clientId" to userId, "colmadoId" to colmadoId,
                "colmadoName" to (CartManager.currentColmadoName ?: "Colmado"), "customerName" to customerName,
                "customerPhone" to phone, "products" to cartItems.map { it.toMap() },
                "subtotal" to subtotal, "deliveryFee" to deliveryFee, "total" to total,
                "address" to address, "note" to note, "status" to initialStatus,
                "paymentMethod" to paymentMethod, "timestamp" to System.currentTimeMillis()
            )
            transaction.set(newOrderRef, orderData)

            val notif = hashMapOf(
                "title" to "Nuevo Pedido", "message" to "Pedido de $customerName por RD$ $total",
                "type" to "NEW_ORDER", "targetId" to newOrderRef.id, "timestamp" to System.currentTimeMillis(), "read" to false
            )
            val notRef = db.collection("colmados_requests").document(colmadoId).collection("notifications").document()
            transaction.set(notRef, notif)
        }.addOnSuccessListener {
            CartManager.clearCart()
            navController.navigate(AppScreens.OrderSuccess.route) { popUpTo(AppScreens.ClientHome.route) { inclusive = false } }
        }.addOnFailureListener { isOrdering = false; Toast.makeText(context, "Error al enviar pedido", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Confirmar Pedido", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (!isOrdering) navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { paddingValues ->
        if (cartItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { Text("El carrito está vacío", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background).padding(16.dp).verticalScroll(rememberScrollState())) {
                if (!isColmadoOpen) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF4E342E) else Color(0xFFFFEBEE)), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Colmado Cerrado", fontWeight = FontWeight.Bold, color = Color.Red)
                                Text("No puedes hacer pedidos ahora.", fontSize = 12.sp, color = Color.Red.copy(alpha = 0.8f))
                            }
                        }
                    }
                }

                Text("Productos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        cartItems.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("${item.quantityDisplay} • ${item.formatUnitDisplay()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("RD$ ${item.calculateTotalPrice().toInt()}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                            if (cartItems.indexOf(item) < cartItems.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                Text("Entrega", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(address, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(phone, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Text("Nota", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(value = note, onValueChange = { note = it }, placeholder = { Text("Ej: Tocar timbre fuerte") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface))

                Text("Pago", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PaymentOption("Efectivo", "Paga al recibir", Icons.Default.AttachMoney, paymentMethod == "Efectivo") { paymentMethod = "Efectivo" }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        PaymentOption("Fiado", if(hasCreditAccount) "Disponible: RD$ $availableCredit" else "Sin crédito", Icons.Default.CreditCard, paymentMethod == "Fiado", hasCreditAccount && availableCredit >= total) { paymentMethod = "Fiado" }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("RD$ ${subtotal.toInt()}") }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Envío", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("RD$ $deliveryFee") }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("TOTAL", fontWeight = FontWeight.Black, fontSize = 18.sp); Text("RD$ ${total.toInt()}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0xFF4CAF50)) }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { placeOrder() }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !isOrdering && isColmadoOpen, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(16.dp)) {
                            if (isOrdering) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Text("ENVIAR PEDIDO", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PaymentOption(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelect() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(if (isSelected) Color(0xFF4CAF50).copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (isSelected) Color(0xFF4CAF50) else Color.Gray) }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
            Text(subtitle, fontSize = 12.sp, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
        RadioButton(selected = isSelected, onClick = if (enabled) onSelect else null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50)))
    }
}
