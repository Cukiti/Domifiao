package com.domifiao.domifiao.ui.screens.colmado

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.model.CartManager
import com.google.firebase.firestore.FirebaseFirestore

data class ProductQuote(
    val id: String,
    val name: String,
    val priceInput: String,
    val isFixed: Boolean,
    val quantity: Double,
    val quantityDisplay: String,
    val unitType: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoProductsScreen(navController: NavController, orderId: String?) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val lifecycleOwner = LocalLifecycleOwner.current

    var productsList by remember { mutableStateOf<List<ProductQuote>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var deliveryFee by remember { mutableIntStateOf(0) }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (!isSaving) navigateBackSecurely() }

    LaunchedEffect(orderId) {
        if (orderId != null) {
            db.collection("orders").document(orderId).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    deliveryFee = doc.getLong("deliveryFee")?.toInt() ?: 0
                    val rawProducts = doc.get("products") as? List<Map<String, Any>> ?: emptyList()
                    
                    productsList = rawProducts.map {
                        val price = (it["price"] as? Number)?.toDouble() ?: 0.0
                        ProductQuote(
                            id = it["id"] as? String ?: "",
                            name = it["name"] as? String ?: "Producto",
                            priceInput = if (price > 0) price.toInt().toString() else "",
                            isFixed = price > 0,
                            quantity = (it["quantity"] as? Number)?.toDouble() ?: 1.0,
                            quantityDisplay = it["quantityDisplay"] as? String ?: "",
                            unitType = it["unitType"] as? String ?: "UNIDAD"
                        )
                    }
                }
                isLoading = false
            }
        }
    }

    val subtotal = productsList.sumOf { (it.priceInput.toDoubleOrNull() ?: 0.0) * it.quantity }.toInt()
    val total = subtotal + deliveryFee
    val isValid = productsList.all { it.priceInput.toDoubleOrNull() != null && it.priceInput.toDoubleOrNull()!! > 0 }

    fun sendQuote() {
        if (orderId == null) return
        isSaving = true

        val updatedProducts = productsList.map { p ->
            mapOf(
                "id" to p.id,
                "name" to p.name,
                "price" to (p.priceInput.toDoubleOrNull() ?: 0.0),
                "quantity" to p.quantity,
                "quantityDisplay" to p.quantityDisplay,
                "unitType" to p.unitType
            )
        }

        db.collection("orders").document(orderId).update(
            "products", updatedProducts,
            "subtotal", subtotal.toDouble(),
            "total", total.toDouble(),
            "status", "WAITING_APPROVAL"
        ).addOnSuccessListener {
            Toast.makeText(context, "Cotización enviada al cliente", Toast.LENGTH_SHORT).show()
            navigateBackSecurely()
        }.addOnFailureListener {
            isSaving = false
            Toast.makeText(context, "Error al enviar", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Cotizar Pedido", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding() // FIX: Evita que los botones del sistema tapen el botón
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("RD$ $subtotal", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Envío", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("RD$ $deliveryFee", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("TOTAL A COTIZAR", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("RD$ $total", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF4CAF50))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { sendQuote() },
                        enabled = isValid && !isSaving,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else { 
                            Icon(Icons.AutoMirrored.Filled.Send, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ENVIAR AL CLIENTE", fontWeight = FontWeight.Bold) 
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { 
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) 
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp), 
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if(isSystemInDarkTheme()) Color(0xFF0D47A1).copy(0.3f) else Color(0xFFE3F2FD)
                        ), 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Los productos con precio fijo no se pueden editar.", 
                                fontSize = 12.sp, 
                                color = if(isSystemInDarkTheme()) Color(0xFFBBDEFB) else Color(0xFF1565C0)
                            )
                        }
                    }
                }
                items(productsList) { product ->
                    ProductQuoteCard(product) { newVal ->
                        productsList = productsList.map { if (it.id == product.id) it.copy(priceInput = newVal) else it }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductQuoteCard(product: ProductQuote, onPriceChange: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(product.quantityDisplay, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = product.priceInput,
                onValueChange = { if (it.all { c -> c.isDigit() }) onPriceChange(it) },
                enabled = !product.isFixed,
                label = { Text("Precio RD$") },
                modifier = Modifier.width(120.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if(product.isFixed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    focusedTextColor = if(product.isFixed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
