package com.domifiao.domifiao.ui.screens.colmado

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.BarcodeScannerView
import com.google.firebase.firestore.FirebaseFirestore

// Modelo Local
data class ProductItem(
    val id: String,
    val name: String,
    val price: Int,
    val category: String,
    val unit: String,
    val isAvailable: Boolean,
    val photoUrl: String,
    val barcode: String = "",
    val isVisibleFromAdmin: Boolean = true,
    val deletedFromAdmin: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()

    var products by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }

    // Estados Edición
    var showEditDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductItem?>(null) }
    var editPrice by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }

    // Estados Eliminar
    var showDeleteDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductItem?>(null) }

    // Estados Escáner
    var showScanner by remember { mutableStateOf(false) }

    fun navigateBack() { navController.popBackStack() }
    BackHandler {
        if (showScanner) showScanner = false
        else navigateBack()
    }

    // Permiso Cámara
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) showScanner = true
    }

    // Cargar productos
    LaunchedEffect(colmadoId) {
        if (colmadoId != null) {
            db.collection("colmado_products")
                .whereEqualTo("colmadoId", colmadoId)
                .whereEqualTo("isSelling", true)
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            // Filtrar productos eliminados por admin
                            val deletedFromAdmin = doc.getBoolean("deletedFromAdmin") ?: false
                            if (deletedFromAdmin) return@mapNotNull null

                            ProductItem(
                                id = doc.id,
                                name = doc.getString("name") ?: "Producto",
                                price = doc.getLong("price")?.toInt() ?: 0,
                                category = doc.getString("category") ?: "Varios",
                                unit = doc.getString("unit") ?: "Unidad",
                                isAvailable = doc.getBoolean("isAvailable") ?: true,
                                photoUrl = doc.getString("photoUrl") ?: doc.getString("photoBase64") ?: "",
                                barcode = doc.getString("barcode") ?: "",
                                isVisibleFromAdmin = doc.getBoolean("isVisibleFromAdmin") ?: true,
                                deletedFromAdmin = false
                            )
                        }
                        products = list.sortedBy { it.name }
                        isLoading = false
                    }
                }
        }
    }

    // Acciones
    fun toggleAvailability(product: ProductItem) {
        db.collection("colmado_products").document(product.id).update("isAvailable", !product.isAvailable)
    }

    fun deleteProduct() {
        val prod = productToDelete ?: return
        db.collection("colmado_products").document(prod.id).update("isSelling", false)
            .addOnSuccessListener {
                Toast.makeText(context, "Producto eliminado", Toast.LENGTH_SHORT).show()
                showDeleteDialog = false
                productToDelete = null
            }
    }

    fun updateProduct() {
        val product = productToEdit ?: return
        val newPrice = editPrice.toIntOrNull() ?: 0
        if (editName.isEmpty()) return

        db.collection("colmado_products").document(product.id)
            .update(mapOf("name" to editName, "searchName" to editName.lowercase(), "price" to newPrice))
            .addOnSuccessListener {
                Toast.makeText(context, "Actualizado", Toast.LENGTH_SHORT).show()
                showEditDialog = false
            }
    }

    // Filtros
    val categories = remember(products) {
        val cats = products.map { it.category }.distinct().sorted().toMutableList()
        cats.add(0, "Todos")
        cats
    }
    val filteredList = products.filter { product ->
        val matchesSearch = product.name.contains(searchText, ignoreCase = true) || product.barcode.contains(searchText)
        val matchesCategory = selectedCategory == "Todos" || product.category == selectedCategory
        matchesSearch && matchesCategory
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Inventario", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    // Contador discreto
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = "${filteredList.size} Items",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            // --- BOTÓN FLOTANTE PREMIUM ---
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(AppScreens.AddProduct.route) },
                containerColor = MaterialTheme.colorScheme.primary, // Verde Premium
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("AGREGAR PRODUCTO", fontWeight = FontWeight.Bold) },
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            )
        },
        floatingActionButtonPosition = FabPosition.End // Posición estándar pero con botón extendido
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
            // 1. BUSCADOR CON ESCÁNER
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("Buscar nombre o código...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                }
            }

            // 2. FILTROS POR CATEGORÍA
            if (!isLoading && products.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if(isSelected) Color.Transparent else Color.LightGray.copy(0.5f)
                            ),
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            // 3. LISTA DE PRODUCTOS
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else if (filteredList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ShoppingBag, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No encontrado", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp), // Espacio para el FAB grande
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList) { product ->
                        InventoryItemCard(
                            product = product,
                            onEdit = {
                                productToEdit = product
                                editName = product.name
                                editPrice = product.price.toString()
                                showEditDialog = true
                            },
                            onDelete = {
                                productToDelete = product
                                showDeleteDialog = true
                            },
                            onToggle = { toggleAvailability(product) }
                        )
                    }
                }
            }
        }
    }

    // DIÁLOGO DE EDICIÓN
    if (showEditDialog && productToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Editar Producto") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { if(it.all { c -> c.isDigit() }) editPrice = it },
                        label = { Text("Precio (RD$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { updateProduct() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancelar") } }
        )
    }

    // --- DIÁLOGO DE SEGURIDAD PARA ELIMINAR ---
    if (showDeleteDialog && productToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(32.dp)) },
            title = { Text("¿Eliminar Producto?", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = {
                Text(
                    "Estás a punto de eliminar \"${productToDelete?.name}\" de tu inventario.\n\nEsta acción no se puede deshacer.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { deleteProduct() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SÍ, ELIMINAR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar", color = Color.Gray) }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Escáner Full Screen
    if (showScanner) {
        Dialog(
            onDismissRequest = { showScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                BarcodeScannerView(onBarcodeDetected = { code ->
                    searchText = code
                    showScanner = false
                })
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
                Text(
                    "Escanea para buscar",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun InventoryItemCard(product: ProductItem, onEdit: () -> Unit, onDelete: () -> Unit, onToggle: () -> Unit) {
    val isHiddenByAdmin = !product.isVisibleFromAdmin

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHiddenByAdmin) {
                if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF4E342E) else Color(0xFFFFF3E0)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Banner de advertencia si está oculto por admin
            if (isHiddenByAdmin) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF9800))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Oculto por administrador - No visible para clientes",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (product.photoUrl.isNotEmpty()) {
                    com.domifiao.domifiao.ui.components.ProductImage(
                        imageData = product.photoUrl,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if(product.isAvailable && !isHiddenByAdmin) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${product.category} • ${product.unit}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "RD$ ${product.price}",
                        fontWeight = FontWeight.Bold,
                        color = if(product.isAvailable && !isHiddenByAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = product.isAvailable,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.scale(0.8f),
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        enabled = !isHiddenByAdmin // Deshabilitar si está oculto por admin
                    )
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}