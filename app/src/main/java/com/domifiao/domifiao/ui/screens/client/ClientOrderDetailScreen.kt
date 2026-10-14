package com.domifiao.domifiao.ui.screens.client

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.OrderProgressBar
import com.google.firebase.firestore.FirebaseFirestore

data class ClientOrderItem(
    val name: String,
    val quantity: Double,
    val quantityDisplay: String,
    val price: Double,
    val unit: String
) {
    fun calculateTotal(): Double = price * quantity
    fun formatTotal(): String = "RD$ ${calculateTotal().toInt()}"
}

data class StatusConfig(
    val color: Color,
    val bgColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientOrderDetailScreen(navController: NavController, orderId: String?) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val currentUserId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLoading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("PENDING") }
    var colmadoName by remember { mutableStateOf("Colmado") }
    var colmadoId by remember { mutableStateOf("") }
    var total by remember { mutableIntStateOf(0) }
    var deliveryFee by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<ClientOrderItem>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Efectivo") }
    var address by remember { mutableStateOf("") }

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
                    colmadoName = doc.getString("colmadoName") ?: "Colmado"
                    colmadoId = doc.getString("colmadoId") ?: ""
                    total = doc.getLong("total")?.toInt() ?: 0
                    deliveryFee = doc.getLong("deliveryFee")?.toInt() ?: 0
                    note = doc.getString("note") ?: ""
                    paymentMethod = doc.getString("paymentMethod") ?: "Efectivo"
                    address = doc.getString("address") ?: ""

                    val rawProducts = doc.get("products") as? List<Map<String, Any>> ?: emptyList()
                    items = rawProducts.map {
                        ClientOrderItem(
                            name = it["name"] as? String ?: "",
                            quantity = (it["quantity"] as? Number)?.toDouble() ?: 0.0,
                            quantityDisplay = it["quantityDisplay"] as? String ?: "",
                            price = (it["price"] as? Number)?.toDouble() ?: 0.0,
                            unit = it["unitType"] as? String ?: "Unidad"
                        )
                    }
                    isLoading = false
                }
            }
        }
    }

    val statusConfig = when(status) {
        "PENDING" -> StatusConfig(Color(0xFFFF9800), Color(0xFFFFF3E0), Icons.Default.Schedule, "PENDIENTE", "Esperando al colmado")
        "WAITING_APPROVAL" -> StatusConfig(Color(0xFFD32F2F), Color(0xFFFFEBEE), Icons.Default.PriceCheck, "ESPERANDO APROBACIÓN", "Revisa el nuevo precio")
        "PREPARING" -> StatusConfig(Color(0xFF2196F3), Color(0xFFE3F2FD), Icons.Default.SoupKitchen, "PREPARANDO", "Tu pedido se está preparando")
        "DELIVERING" -> StatusConfig(Color(0xFF9C27B0), Color(0xFFF3E5F5), Icons.Default.DeliveryDining, "EN CAMINO", "El delivery va hacia ti")
        "WAITING_CLIENT_CONFIRMATION" -> StatusConfig(Color(0xFF009688), Color(0xFFE0F2F1), Icons.Default.CheckCircle, "ESPERANDO CONFIRMACIÓN", "El pedido llegó a su destino")
        "COMPLETED" -> StatusConfig(Color(0xFF4CAF50), Color(0xFFE8F5E9), Icons.Default.CheckCircle, "ENTREGADO", "¡Gracias por tu compra!")
        "CANCELLED" -> StatusConfig(Color.Red, Color(0xFFFFEBEE), Icons.Default.Cancel, "CANCELADO", "El pedido fue cancelado")
        else -> StatusConfig(Color.Gray, Color.White, Icons.Default.Info, "PROCESANDO", "")
    }

    val animatedBgColor by animateColorAsState(targetValue = statusConfig.bgColor, label = "bg")
    val animatedColor by animateColorAsState(targetValue = statusConfig.color, label = "fg")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Pedido", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    if (colmadoId.isNotEmpty()) {
                        IconButton(onClick = { navController.navigate(AppScreens.Chat.createColmadoChat(colmadoId)) }) {
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
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("RESUMEN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            items.forEach { item ->
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
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("ENTREGA", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(colmadoName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(address, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (note.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Nota", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text(note, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (status == "WAITING_APPROVAL" && orderId != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "El colmado ha enviado el precio final. ¿Deseas proceder con el pedido?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedButton(
                                onClick = {
                                    db.collection("orders").document(orderId).update("status", "CANCELLED")
                                        .addOnSuccessListener { Toast.makeText(context, "Pedido cancelado", Toast.LENGTH_SHORT).show() }
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                            ) {
                                Text("Rechazar")
                            }
                            Button(
                                onClick = {
                                    db.collection("orders").document(orderId).update("status", "PREPARING")
                                        .addOnSuccessListener { Toast.makeText(context, "Cotización aceptada", Toast.LENGTH_SHORT).show() }
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("Aceptar y Pedir", color = Color.White)
                            }
                        }
                    }

                    if (status == "WAITING_CLIENT_CONFIRMATION" && orderId != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "El colmado indicó que el pedido ha llegado. ¿Confirmas la recepción?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                // Aquí podrías querer actualizar también la deuda si es fiado, 
                                // pero según el diseño actual, el colmado es el que finaliza o lo cobra en WAITING_CLIENT_CONFIRMATION
                                // O si el cliente lo pasa a COMPLETED directamente, hay que asegurarse de que el colmado cobre.
                                // Por simplicidad, pasamos a COMPLETED.
                                db.collection("orders").document(orderId).update("status", "COMPLETED")
                                    .addOnSuccessListener { Toast.makeText(context, "Pedido recibido", Toast.LENGTH_SHORT).show() }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("CONFIRMAR RECEPCIÓN", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
