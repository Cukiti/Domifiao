package com.domifiao.domifiao.ui.screens.client

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.model.CartManager
import com.domifiao.domifiao.model.ClientProduct
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientColmadoProductsScreen(navController: NavController, colmadoId: String?) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val userId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Estados de Datos
    var products by remember { mutableStateOf<List<ClientProduct>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Info del Colmado
    var colmadoName by remember { mutableStateOf("Cargando...") }
    var colmadoAddress by remember { mutableStateOf("") }
    var colmadoProvince by remember { mutableStateOf("") }
    var colmadoMunicipality by remember { mutableStateOf("") }
    var colmadoPhoto by remember { mutableStateOf("") }
    var colmadoRating by remember { mutableDoubleStateOf(5.0) }
    var deliveryFee by remember { mutableIntStateOf(0) }
    var isHiring by remember { mutableStateOf(false) }
    var isAcceptingCredit by remember { mutableStateOf(false) }
    var isOpen by remember { mutableStateOf(false) }

    // Estado de favorito
    var isFavorite by remember { mutableStateOf(false) }

    // Estados del Usuario
    var myName by remember { mutableStateOf("") }
    var myPhone by remember { mutableStateOf("") }
    var hasAppliedJob by remember { mutableStateOf(false) }
    var deliveryApplicationStatus by remember { mutableStateOf("NONE") }

    // Estados de Crédito
    var creditStatus by remember { mutableStateOf("NONE") }
    var creditLimit by remember { mutableIntStateOf(0) }
    var currentDebt by remember { mutableIntStateOf(0) }

    // UI & Filtros
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    var showCartConflictDialog by remember { mutableStateOf(false) }
    var pendingProductToAdd by remember { mutableStateOf<ClientProduct?>(null) }
    var pendingQuantity by remember { mutableDoubleStateOf(1.0) }
    var pendingQuantityDisplay by remember { mutableStateOf("1") }
    var pendingSelectionMode by remember { mutableStateOf("UNIDAD") }
    var showJobDialog by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }

    // Estado para el Sheet de Selección de Cantidad
    var selectedProductForDetail by remember { mutableStateOf<ClientProduct?>(null) }
    var showProductDetailSheet by remember { mutableStateOf(false) }

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }

    BackHandler {
        if (showProductDetailSheet) {
            showProductDetailSheet = false
        } else if (!showCartConflictDialog && !showJobDialog) {
            navigateBackSecurely()
        }
    }

    // Función para agregar/quitar favorito
    fun toggleFavorite() {
        if (userId == null) {
            Toast.makeText(context, "Debes iniciar sesión para guardar favoritos", Toast.LENGTH_SHORT).show()
            return
        }
        if (colmadoId == null) return
        
        if (!isFavorite && colmadoName == "Cargando...") {
            Toast.makeText(context, "Espera a que carguen los datos", Toast.LENGTH_SHORT).show()
            return
        }
        
        val favRef = db.collection("users").document(userId).collection("favorites").document(colmadoId)
        val wasFavorite = isFavorite
        isFavorite = !wasFavorite // Optimistic UI update

        if (wasFavorite) {
            favRef.delete().addOnSuccessListener { 
                Toast.makeText(context, "Eliminado de favoritos", Toast.LENGTH_SHORT).show() 
            }.addOnFailureListener {
                isFavorite = true // Revert on failure
                Toast.makeText(context, "Error al eliminar", Toast.LENGTH_SHORT).show()
            }
        } else {
            val favoriteData = hashMapOf(
                "colmadoId" to colmadoId,
                "name" to colmadoName,
                "province" to colmadoProvince,
                "municipality" to colmadoMunicipality,
                "photoUrl" to colmadoPhoto,
                "addedAt" to com.google.firebase.Timestamp.now()
            )
            favRef.set(favoriteData).addOnSuccessListener { 
                Toast.makeText(context, "Agregado a favoritos", Toast.LENGTH_SHORT).show() 
            }.addOnFailureListener {
                isFavorite = false // Revert on failure
                Toast.makeText(context, "Error al agregar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- CARGAR DATOS ---
    LaunchedEffect(colmadoId, userId) {
        if (colmadoId != null) {
            db.collection("colmados_requests").document(colmadoId).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    colmadoName = doc.getString("name") ?: "Colmado"
                    colmadoAddress = doc.getString("address") ?: ""
                    colmadoProvince = doc.getString("province") ?: ""
                    colmadoMunicipality = doc.getString("municipality") ?: ""
                    colmadoPhoto = doc.getString("photoUrl") ?: doc.getString("photoBase64") ?: ""
                    isHiring = doc.getBoolean("isHiringDelivery") ?: false
                    isAcceptingCredit = doc.getBoolean("isAcceptingCredit") ?: false
                    colmadoRating = doc.getDouble("rating") ?: 5.0
                    deliveryFee = doc.getLong("deliveryFee")?.toInt() ?: 0
                    isOpen = doc.getBoolean("isOpen") ?: true
                }
            }

            if (userId != null) {
                db.collection("users").document(userId).collection("favorites").document(colmadoId)
                    .addSnapshotListener { doc, _ -> isFavorite = doc != null && doc.exists() }

                db.collection("users").document(userId).get().addOnSuccessListener { uDoc ->
                    myName = uDoc.getString("name") ?: "Usuario"
                    myPhone = uDoc.getString("phone") ?: ""
                }

                db.collection("debts").whereEqualTo("clientId", userId).whereEqualTo("colmadoId", colmadoId)
                    .addSnapshotListener { snap, _ ->
                        if (snap != null && !snap.isEmpty) {
                            creditStatus = "APPROVED"
                            val debtDoc = snap.documents[0]
                            creditLimit = debtDoc.getLong("limit")?.toInt() ?: 0
                            currentDebt = debtDoc.getLong("amount")?.toInt() ?: 0
                        } else {
                            db.collection("credit_requests").whereEqualTo("clientId", userId).whereEqualTo("colmadoId", colmadoId).whereEqualTo("status", "PENDING").get()
                                .addOnSuccessListener { reqSnap -> creditStatus = if (!reqSnap.isEmpty) "PENDING" else "NONE" }
                        }
                    }

                db.collection("delivery_applications").whereEqualTo("userId", userId).whereEqualTo("colmadoId", colmadoId)
                    .addSnapshotListener { snap, _ ->
                        if (snap != null && !snap.isEmpty) {
                            val doc = snap.documents[0]
                            val status = doc.getString("status") ?: "NONE"
                            deliveryApplicationStatus = status
                            hasAppliedJob = status == "PENDING" || status == "APPROVED"
                        } else {
                            hasAppliedJob = false
                            deliveryApplicationStatus = "NONE"
                        }
                    }
            }

            db.collection("colmado_products").whereEqualTo("colmadoId", colmadoId).whereEqualTo("isSelling", true).whereEqualTo("isAvailable", true)
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        products = snapshot.documents.mapNotNull { doc ->
                            val isVisibleFromAdmin = doc.getBoolean("isVisibleFromAdmin") ?: true
                            val deletedFromAdmin = doc.getBoolean("deletedFromAdmin") ?: false
                            if (!isVisibleFromAdmin || deletedFromAdmin) return@mapNotNull null
                            try {
                                ClientProduct(
                                    id = doc.id,
                                    name = doc.getString("name") ?: "Producto",
                                    price = doc.getDouble("price") ?: 0.0,
                                    category = doc.getString("category") ?: "Varios",
                                    unitType = doc.getString("unitType") ?: "UNIDAD",
                                    photoUrl = doc.getString("photoUrl") ?: doc.getString("photoBase64") ?: "",
                                    quantityInCart = 0.0,
                                    quantityDisplay = ""
                                )
                            } catch (e: Exception) { null }
                        }.sortedBy { it.name }
                        isLoading = false
                    }
                }
        }
    }

    val categories = remember(products) {
        val cats = products.map { it.category }.distinct().sorted().toMutableList()
        cats.add(0, "Todos")
        cats
    }

    val filteredList = products.filter { it.name.contains(searchText, ignoreCase = true) && (selectedCategory == "Todos" || it.category == selectedCategory) }
    val cartTotalItems = CartManager.items.sumOf { it.quantityInCart }
    val cartTotal = CartManager.items.sumOf { it.calculateTotalPrice() }

    fun addToCart(product: ClientProduct, quantity: Double, quantityDisplay: String, selectionMode: String) {
        val currentCart = CartManager.currentColmadoId
        if (currentCart != null && currentCart != colmadoId) {
            pendingProductToAdd = product; pendingQuantity = quantity; pendingQuantityDisplay = quantityDisplay; pendingSelectionMode = selectionMode
            showCartConflictDialog = true
        } else if (colmadoId != null) {
            CartManager.addProduct(product, colmadoId, colmadoName, quantity, quantityDisplay, selectionMode)
        }
    }

    fun applyForJob() {
        if (userId == null || colmadoId == null) return
        isApplying = true
        db.collection("delivery_applications").whereEqualTo("userId", userId).whereEqualTo("colmadoId", colmadoId).get().addOnSuccessListener { existing ->
            if (!existing.isEmpty) {
                db.collection("delivery_applications").document(existing.documents[0].id).update("status", "PENDING", "reappliedAt", com.google.firebase.Timestamp.now())
                    .addOnSuccessListener { isApplying = false; showJobDialog = false; Toast.makeText(context, "Solicitud reenviada", Toast.LENGTH_SHORT).show() }
            } else {
                val app = hashMapOf("userId" to userId, "colmadoId" to colmadoId, "userName" to myName, "userPhone" to myPhone, "status" to "PENDING", "appliedAt" to com.google.firebase.Timestamp.now())
                db.collection("delivery_applications").add(app).addOnSuccessListener { isApplying = false; showJobDialog = false; Toast.makeText(context, "Solicitud enviada", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = colmadoName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    val scale by animateFloatAsState(
                        targetValue = if (isFavorite) 1.2f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "heart_scale"
                    )
                    IconButton(onClick = { toggleFavorite() }) { 
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                            null, 
                            tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.scale(if (isFavorite) scale else 1f)
                        ) 
                    }
                    IconButton(onClick = { if (colmadoId != null) navController.navigate(AppScreens.Chat.createColmadoChat(colmadoId)) }) { Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        floatingActionButton = {
            if (cartTotalItems > 0) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(AppScreens.ClientCheckout.route) },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.ShoppingCart, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$cartTotalItems items • RD$ $cartTotal", fontWeight = FontWeight.Bold)
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
                item {
                    ColmadoPremiumHeader(
                        photoUrl = colmadoPhoto, rating = colmadoRating, address = colmadoAddress, province = colmadoProvince,
                        municipality = colmadoMunicipality, deliveryFee = deliveryFee, isHiring = isHiring, hasApplied = hasAppliedJob,
                        deliveryApplicationStatus = deliveryApplicationStatus, isAcceptingCredit = isAcceptingCredit, creditStatus = creditStatus,
                        creditLimit = creditLimit, currentDebt = currentDebt, isOpen = isOpen, colmadoId = colmadoId, navController = navController,
                        onJobClick = { showJobDialog = true },
                        onRequestCredit = { if (colmadoId != null) navController.navigate(AppScreens.ClientCreditRequest.createRoute(colmadoId, colmadoName)) },
                        onViewDebt = { if (colmadoId != null) navController.navigate(AppScreens.ClientDebtDetail.createRoute(colmadoId, colmadoName, currentDebt, creditLimit)) }
                    )
                }

                item {
                    OutlinedTextField(
                        value = searchText, onValueChange = { searchText = it }, placeholder = { Text("Buscar productos...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4CAF50), unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
                        singleLine = true
                    )
                }

                item {
                    ScrollableTabRow(selectedTabIndex = categories.indexOf(selectedCategory), edgePadding = 16.dp, containerColor = Color.Transparent, divider = {}) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = category == selectedCategory, onClick = { selectedCategory = category }, label = { Text(category) },
                                modifier = Modifier.padding(horizontal = 4.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50), selectedLabelColor = Color.White, containerColor = MaterialTheme.colorScheme.surface, labelColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }

                if (filteredList.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ShoppingBag, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No hay productos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(filteredList) { product ->
                        val quantityInCart = CartManager.items.find { it.id == product.id }?.quantityInCart ?: 0.0
                        ClientProductCard(product, quantityInCart, onAdd = { selectedProductForDetail = product; showProductDetailSheet = true }, onRemove = { CartManager.removeItem(product.id) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showProductDetailSheet && selectedProductForDetail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showProductDetailSheet = false; selectedProductForDetail = null },
            sheetState = sheetState
        ) {
            ProductQuantitySelector(product = selectedProductForDetail!!, colmadoName = colmadoName,
                onConfirm = { p, q, d, m -> addToCart(p, q, d, m); showProductDetailSheet = false; selectedProductForDetail = null },
                onDismiss = { showProductDetailSheet = false; selectedProductForDetail = null }
            )
        }
    }

    if (showCartConflictDialog) {
        AlertDialog(onDismissRequest = { showCartConflictDialog = false }, title = { Text("Cambiar de Colmado") },
            text = { Text("¿Deseas vaciar tu carrito para agregar productos de $colmadoName?") },
            confirmButton = { TextButton(onClick = { CartManager.clearCart(); if (colmadoId != null && pendingProductToAdd != null) addToCart(pendingProductToAdd!!, pendingQuantity, pendingQuantityDisplay, pendingSelectionMode); showCartConflictDialog = false }) { Text("Sí, vaciar") } },
            dismissButton = { TextButton(onClick = { showCartConflictDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showJobDialog) {
        AlertDialog(onDismissRequest = { if (!isApplying) showJobDialog = false }, title = { Text("Solicitar Trabajo") },
            text = { Text("¿Quieres aplicar para ser delivery en $colmadoName?") },
            confirmButton = { Button(onClick = { applyForJob() }, enabled = !isApplying) { if (isApplying) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White) else Text("Enviar") } },
            dismissButton = { if (!isApplying) TextButton(onClick = { showJobDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun ProductQuantitySelector(product: ClientProduct, colmadoName: String, onConfirm: (ClientProduct, Double, String, String) -> Unit, onDismiss: () -> Unit) {
    val isLibra = product.unitType.uppercase() == "LIBRA"
    var mode by remember { mutableStateOf(if (isLibra) "POR_LIBRA" else "UNIDAD") }
    var qty by remember { mutableDoubleStateOf(if (isLibra) 0.5 else 1.0) }
    var display by remember { mutableStateOf(if (isLibra) "½ Libra" else "1") }
    val finalPrice = if (mode == "AL_DETALLE") qty else product.price * qty

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // FIX: Evitar que la barra del sistema tape el botón
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false) // FIX: Permitir que se ajuste al espacio sin desbordarse
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (product.photoUrl.isNotEmpty()) com.domifiao.domifiao.ui.components.ProductImage(product.photoUrl, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            else Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }

            Spacer(modifier = Modifier.height(16.dp))
            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(product.category, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(if (isLibra) "RD$ ${product.price.toInt()} / Libra" else "RD$ ${product.price.toInt()} c/u", fontSize = 16.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(20.dp))
            if (isLibra) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(4.dp)) {
                        val btnMod = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).padding(vertical = 10.dp)
                        Box(modifier = btnMod.background(if(mode=="POR_LIBRA") Color(0xFF4CAF50) else Color.Transparent).clickable { mode="POR_LIBRA"; qty=0.5; display="½ Libra" }, contentAlignment = Alignment.Center) { Text("Por Libra", color = if(mode=="POR_LIBRA") Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                        Box(modifier = btnMod.background(if(mode=="AL_DETALLE") Color(0xFF4CAF50) else Color.Transparent).clickable { mode="AL_DETALLE"; qty=50.0; display="RD$ 50" }, contentAlignment = Alignment.Center) { Text("Al Detalle", color = if(mode=="AL_DETALLE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (mode == "POR_LIBRA") LibraOptions(qty, onSelect = { v, d -> qty = v; display = d })
                    else DetalleOptions(qty.toInt(), product.price, onSelect = { v, d -> qty = v.toDouble(); display = d })
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    UnidadSelector(qty.toInt(), onQtyChange = { qty = it.toDouble(); display = it.toString() })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onConfirm(product, qty, display, mode) }, 
            modifier = Modifier.fillMaxWidth().height(56.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), 
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("AGREGAR - RD$ ${finalPrice.toInt()}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun LibraOptions(selected: Double, onSelect: (Double, String) -> Unit) {
    val opts = listOf(0.25 to "¼ Libra", 0.5 to "½ Libra", 0.75 to "¾ Libra", 1.0 to "1 Libra", 1.5 to "1½ Lb", 2.0 to "2 Libras")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { opts.take(3).forEach { (v, d) -> OptionChip(d, selected == v, { onSelect(v, d) }, Modifier.weight(1f)) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { opts.drop(3).forEach { (v, d) -> OptionChip(d, selected == v, { onSelect(v, d) }, Modifier.weight(1f)) } }
    }
}

@Composable
fun DetalleOptions(selected: Int, price: Double, onSelect: (Int, String) -> Unit) {
    val opts = listOf(25, 50, 75, 100, 150, 200)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { opts.take(3).forEach { v -> OptionChip("RD$ $v", selected == v, { onSelect(v, "RD$ $v") }, Modifier.weight(1f)) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { opts.drop(3).forEach { v -> OptionChip("RD$ $v", selected == v, { onSelect(v, "RD$ $v") }, Modifier.weight(1f)) } }
    }
}

@Composable
fun UnidadSelector(quantity: Int, onQtyChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if(quantity > 1) onQtyChange(quantity-1) }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) { Icon(Icons.Default.Remove, null) }
            Text("$quantity", fontSize = 36.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 32.dp), color = Color(0xFF4CAF50))
            IconButton(onClick = { if(quantity < 99) onQtyChange(quantity+1) }, modifier = Modifier.size(48.dp).background(Color(0xFF4CAF50), CircleShape)) { Icon(Icons.Default.Add, null, tint = Color.White) }
        }
        Text("unidades", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OptionChip(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (isSelected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).clickable(onClick = onClick).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ColmadoPremiumHeader(photoUrl: String, rating: Double, address: String, province: String, municipality: String, deliveryFee: Int, isHiring: Boolean, hasApplied: Boolean, deliveryApplicationStatus: String, isAcceptingCredit: Boolean, creditStatus: String, creditLimit: Int, currentDebt: Int, isOpen: Boolean, colmadoId: String?, navController: NavController, onJobClick: () -> Unit, onRequestCredit: () -> Unit, onViewDebt: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            if (photoUrl.isNotEmpty()) com.domifiao.domifiao.ui.components.ColmadoImage(photoUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Color.Gray), contentAlignment = Alignment.Center) { Icon(Icons.Default.Store, null, tint = Color.LightGray, modifier = Modifier.size(80.dp)) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)), 300f)))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                    Text(String.format("%.1f", rating), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text(" • Envío RD$ $deliveryFee", color = Color.White, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(address, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (municipality.isNotEmpty()) Text("$municipality, $province", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Surface(color = if (isOpen) Color(0xFF4CAF50) else Color.Red, shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Text(if (isOpen) "ABIERTO" else "CERRADO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isAcceptingCredit) {
                val color = if(creditStatus=="APPROVED") Color(0xFF4CAF50) else Color(0xFFFF9800)
                Button(onClick = { if(creditStatus=="APPROVED") onViewDebt() else onRequestCredit() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.1f), contentColor = color), border = androidx.compose.foundation.BorderStroke(1.dp, color), enabled = creditStatus != "PENDING") {
                    Text(if(creditStatus=="APPROVED") "Ver Deuda" else if(creditStatus=="PENDING") "Pendiente..." else "Pedir Fiado", fontWeight = FontWeight.Bold)
                }
            }
            if (isHiring) {
                val color = if(deliveryApplicationStatus=="APPROVED") Color(0xFF4CAF50) else Color(0xFF2196F3)
                Button(onClick = { if(deliveryApplicationStatus=="APPROVED") navController.navigate("delivery_dashboard") else onJobClick() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = color), enabled = deliveryApplicationStatus != "PENDING") {
                    Text(if(deliveryApplicationStatus=="APPROVED") "Entregar" else if(deliveryApplicationStatus=="PENDING") "En revisión" else "Trabajar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.background)
    }
}

@Composable
fun ClientProductCard(product: ClientProduct, quantityInCart: Double, onAdd: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onAdd() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (product.photoUrl.isNotEmpty()) com.domifiao.domifiao.ui.components.ProductImage(product.photoUrl, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            else Box(Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(product.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(product.formatUnitDisplay(), fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF4CAF50))
            }
            if (quantityInCart > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }
                    Text("${if(quantityInCart >= 1) quantityInCart.toInt() else quantityInCart}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onAdd, modifier = Modifier.size(32.dp).background(Color(0xFF4CAF50), CircleShape)) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            } else {
                Box(Modifier.background(Color(0xFF4CAF50).copy(0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Default.Add, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
