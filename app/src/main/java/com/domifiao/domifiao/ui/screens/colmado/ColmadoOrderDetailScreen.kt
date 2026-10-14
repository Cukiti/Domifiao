package com.domifiao.domifiao.ui.screens.colmado

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.OrderProgressBar
import com.google.firebase.firestore.FirebaseFirestore

data class ColmadoOrderItem(
    val id: String,
    val name: String,
    val quantity: Double,
    val quantityDisplay: String,
    val price: Double,
    val unitType: String
) {
    fun formatTotal(): String = "RD$ ${(price * quantity).toInt()}"
}

data class ColmadoStatusConfig(
    val color: Color,
    val bgColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val actionRequired: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoOrderDetailScreen(navController: NavController, orderId: String?) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLoading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("PENDING") }
    var clientName by remember { mutableStateOf("Cliente") }
    var clientPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var total by remember { mutableIntStateOf(0) }
    var deliveryFee by remember { mutableIntStateOf(0) }
    var orderItems by remember { mutableStateOf<List<ColmadoOrderItem>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    var isFiado by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf("") }

    // Diálogos
    var showEditTotalDialog by remember { mutableStateOf(false) }
    var newTotalInput by remember { mutableStateOf("") }
    var showHousePhotoDialog by remember { mutableStateOf(false) }
    var housePhotoUrl by remember { mutableStateOf<String?>(null) }
    var houseDescription by remember { mutableStateOf("") }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    LaunchedEffect(orderId) {
        if (orderId != null) {
            db.collection("orders").document(orderId).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    status = doc.getString("status") ?: "PENDING"
                    clientName = doc.getString("customerName") ?: "Cliente"
                    clientPhone = doc.getString("customerPhone") ?: ""
                    address = doc.getString("address") ?: ""
                    total = doc.getLong("total")?.toInt() ?: 0
                    deliveryFee = doc.getLong("deliveryFee")?.toInt() ?: 0
                    note = doc.getString("note") ?: ""
                    isFiado = doc.getString("paymentMethod") == "Fiado"
                    clientId = doc.getString("clientId") ?: ""

                    val rawProducts = doc.get("products") as? List<Map<String, Any>> ?: emptyList()
                    orderItems = rawProducts.map {
                        ColmadoOrderItem(
                            id = it["id"] as? String ?: "",
                            name = it["name"] as? String ?: "Producto",
                            quantity = (it["quantity"] as? Number)?.toDouble() ?: 1.0,
                            quantityDisplay = it["quantityDisplay"] as? String ?: "",
                            price = (it["price"] as? Number)?.toDouble() ?: 0.0,
                            unitType = it["unitType"] as? String ?: "UNIDAD"
                        )
                    }

                    if (clientId.isNotEmpty()) {
                        db.collection("users").document(clientId).get().addOnSuccessListener { uDoc ->
                            housePhotoUrl = uDoc.getString("housePhotoUrl") ?: uDoc.getString("housePhotoBase64")
                            houseDescription = uDoc.getString("houseDescription") ?: ""
                        }
                    }
                    isLoading = false
                }
            }
        }
    }

    fun updateStatus(newStatus: String, toastMsg: String) {
        if (orderId == null) return
        db.collection("orders").document(orderId).update("status", newStatus).addOnSuccessListener {
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            if (newStatus == "CANCELLED") navigateBackSecurely()
        }
    }

    val allPricesPresent = orderItems.isNotEmpty() && orderItems.all { it.price > 0 }

    val statusConfig = when(status) {
        "PENDING" -> ColmadoStatusConfig(
            Color(0xFFFF9800), 
            Color(0xFFFFF3E0), 
            Icons.Default.Schedule, 
            "PENDIENTE", 
            if (allPricesPresent) "Nuevo pedido recibido" else "Faltan precios por cotizar", 
            true
        )
        "WAITING_APPROVAL" -> ColmadoStatusConfig(Color(0xFF2196F3), Color(0xFFE3F2FD), Icons.Default.HourglassEmpty, "ESPERANDO APROBACIÓN", "Esperando que el cliente acepte")
        "PREPARING" -> ColmadoStatusConfig(Color(0xFF4CAF50), Color(0xFFE8F5E9), Icons.Default.SoupKitchen, "PREPARANDO", "Empaca los productos ahora", true)
        "DELIVERING" -> ColmadoStatusConfig(Color(0xFF9C27B0), Color(0xFFF3E5F5), Icons.Default.DeliveryDining, "EN CAMINO", "El pedido va en el motor")
        "WAITING_CLIENT_CONFIRMATION" -> ColmadoStatusConfig(Color(0xFF2E7D32), Color(0xFFE8F5E9), Icons.Default.CheckCircle, "ESPERANDO CONFIRMACIÓN", "Esperando confirmación final")
        "COMPLETED" -> ColmadoStatusConfig(Color(0xFF4CAF50), Color(0xFFE8F5E9), Icons.Default.Verified, "ENTREGADO", "Venta finalizada con éxito")
        "CANCELLED" -> ColmadoStatusConfig(Color.Red, Color(0xFFFFEBEE), Icons.Default.Cancel, "CANCELADO", "Este pedido fue cancelado")
        else -> ColmadoStatusConfig(Color.Gray, Color.White, Icons.Default.Info, "PROCESANDO", "")
    }

    val animatedBgColor by animateColorAsState(targetValue = statusConfig.bgColor, label = "bg")
    val animatedColor by animateColorAsState(targetValue = statusConfig.color, label = "fg")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Venta", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    if (clientId.isNotEmpty()) {
                        IconButton(onClick = { navController.navigate(AppScreens.Chat.createRoute(clientId, "CLIENT")) }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(statusConfig.icon, null, tint = animatedColor, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(statusConfig.title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = animatedColor)
                        }
                        Text(statusConfig.subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        OrderProgressBar(status = status)
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Acciones según estado
                    if (status == "PENDING") {
                        if (allPricesPresent) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { updateStatus("PREPARING", "¡Pedido aceptado! Ya puedes prepararlo.") }, 
                                    modifier = Modifier.fillMaxWidth().height(50.dp), 
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("ACEPTAR PEDIDO", fontWeight = FontWeight.Bold)
                                }
                                
                                OutlinedButton(
                                    onClick = { navController.navigate(AppScreens.ColmadoProducts.createRoute(orderId!!)) }, 
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PriceCheck, null); Spacer(Modifier.width(8.dp)); Text("EDITAR PRECIOS")
                                }
                            }
                        } else {
                            Button(
                                onClick = { navController.navigate(AppScreens.ColmadoProducts.createRoute(orderId!!)) }, 
                                modifier = Modifier.fillMaxWidth().height(50.dp), 
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Icon(Icons.Default.PriceCheck, null); Spacer(Modifier.width(8.dp)); Text("COTIZAR / PONER PRECIOS")
                            }
                        }
                    } else if (status == "PREPARING") {
                        Button(onClick = { updateStatus("DELIVERING", "Pedido enviado") }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) {
                            Icon(Icons.Default.DeliveryDining, null); Spacer(Modifier.width(8.dp)); Text("ENVIAR CON DELIVERY")
                        }
                    } else if (status == "DELIVERING") {
                        Button(onClick = { updateStatus("WAITING_CLIENT_CONFIRMATION", "Llegó al destino") }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009688))) {
                            Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("MARCAR COMO LLEGADO")
                        }
                    } else if (status == "WAITING_CLIENT_CONFIRMATION") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "El cliente aún no confirma la recepción. Si ya entregaste el pedido, puedes finalizarlo manualmente.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { updateStatus("COMPLETED", "Pedido finalizado") }, 
                                modifier = Modifier.fillMaxWidth().height(50.dp), 
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Icon(Icons.Default.Verified, null); Spacer(Modifier.width(8.dp)); Text("FINALIZAR PEDIDO (Entrega Manual)")
                            }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(clientName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(address, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clientPhone"))) }) {
                                    Icon(Icons.Default.Call, null, tint = Color(0xFF4CAF50))
                                }
                            }
                            if (houseDescription.isNotEmpty() || housePhotoUrl != null) {
                                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showHousePhotoDialog = true }) {
                                    Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ver ubicación y foto de la casa", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("PRODUCTOS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            orderItems.forEach { item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                                    Text("${item.quantityDisplay} ${item.name}", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                    Text(item.formatTotal(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("TOTAL", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("RD$ $total", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF4CAF50))
                            }
                            Spacer(Modifier.height(12.dp))
                            Surface(color = if(isFiado) (if(isSystemInDarkTheme()) Color(0xFF4E342E) else Color(0xFFFFF3E0)) else (if(isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if(isFiado) Icons.Default.CreditCard else Icons.Default.AttachMoney, null, tint = if(isFiado) Color(0xFFFF9800) else Color(0xFF4CAF50))
                                    Spacer(Modifier.width(12.dp))
                                    Text(if(isFiado) "Pago: FIADO (A cuenta)" else "Pago: EFECTIVO", fontWeight = FontWeight.Bold, color = if(isFiado) Color(0xFFFF9800) else Color(0xFF4CAF50))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHousePhotoDialog) {
        Dialog(onDismissRequest = { showHousePhotoDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ubicación del Cliente", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    if (!housePhotoUrl.isNullOrEmpty()) {
                        com.domifiao.domifiao.ui.components.StorageImage(
                            imageData = housePhotoUrl!!,
                            contentDescription = "Foto de la casa",
                            modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    if (houseDescription.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(houseDescription, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showHousePhotoDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
                }
            }
        }
    }
}
