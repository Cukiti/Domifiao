package com.domifiao.domifiao.ui.screens.admin

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.domifiao.domifiao.model.DefaultProduct
import com.domifiao.domifiao.utils.CameraHelper
import com.domifiao.domifiao.utils.ImageStorageManager
import com.domifiao.domifiao.ui.components.ProductImage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog

/**
 * Pantalla administrativa para gestionar productos por defecto.
 *
 * Los productos por defecto se inyectan automáticamente a todos los colmados
 * (existentes y nuevos) con un precio sugerido que pueden modificar.
 *
 * Funcionalidades:
 * - Listar productos por defecto
 * - Crear nuevo producto
 * - Editar producto existente
 * - Eliminar producto (soft delete)
 * - Activar/Desactivar producto
 * - Sincronización manual a todos los colmados
 * - Búsqueda y filtros por categoría
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDefaultProductsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()

    // Estados
    var products by remember { mutableStateOf<List<DefaultProduct>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    var showDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<DefaultProduct?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<DefaultProduct?>(null) }

    // Cargar productos desde Firestore (parseo manual para mejor control)
    LaunchedEffect(Unit) {
        db.collection("default_products")
            .orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    isLoading = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    products = snapshot.documents.mapNotNull { doc ->
                        try {
                            DefaultProduct(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                description = doc.getString("description") ?: "",
                                category = doc.getString("category") ?: "",
                                suggestedPrice = doc.getDouble("suggestedPrice") ?: 0.0,
                                unitType = doc.getString("unitType") ?: "UNIDAD",
                                photoUrl = doc.getString("photoUrl") ?: "",
                                barcode = doc.getString("barcode") ?: "",
                                isActive = doc.getBoolean("isActive") ?: true,
                                isVisible = doc.getBoolean("isVisible") ?: true,
                                createdAt = doc.getTimestamp("createdAt"),
                                updatedAt = doc.getTimestamp("updatedAt"),
                                createdBy = doc.getString("createdBy") ?: ""
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                isLoading = false
            }
    }

    // Filtrar productos
    val filteredProducts = products.filter { product ->
        val matchesSearch = product.name.contains(searchText, ignoreCase = true) ||
                product.category.contains(searchText, ignoreCase = true)
        val matchesCategory = selectedCategory == "Todos" || product.category == selectedCategory

        matchesSearch && matchesCategory
    }

    // Categorías con contador
    val categories = listOf("Todos") + DefaultProduct.CATEGORIES
    val categoryCounts = products.groupingBy { it.category }.eachCount()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productos por Defecto", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    // Botón de sincronización manual
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                syncAllDefaultProducts(
                                    db = db,
                                    products = products.filter { it.isActive },
                                    onStart = { isSyncing = true },
                                    onComplete = {
                                        isSyncing = false
                                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                    },
                                    onError = {
                                        isSyncing = false
                                        Toast.makeText(context, "Error: $it", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }) {
                            Icon(Icons.Default.Sync, "Sincronizar Todo")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    productToEdit = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, "Agregar Producto")
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Barra de búsqueda
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Buscar producto...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Buscar") },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Default.Clear, "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Filtros por categoría
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val count = if (category == "Todos") {
                            products.size
                        } else {
                            categoryCounts[category] ?: 0
                        }

                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text("$category ($count)") },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lista de productos
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (filteredProducts.isEmpty()) {
                    EmptyProductsState(
                        message = if (searchText.isNotEmpty() || selectedCategory != "Todos") {
                            "No se encontraron productos"
                        } else {
                            "No hay productos por defecto.\nAgrega uno presionando el botón +"
                        }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts) { product ->
                            ProductDefaultCard(
                                product = product,
                                onEdit = {
                                    productToEdit = product
                                    showDialog = true
                                },
                                onDelete = {
                                    productToDelete = product
                                    showDeleteConfirm = true
                                },
                                onToggleActive = {
                                    scope.launch {
                                        toggleProductActive(db, product, context)
                                    }
                                },
                                onToggleVisibility = {
                                    scope.launch {
                                        toggleProductVisibility(db, product, context)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo de crear/editar producto
    if (showDialog) {
        DefaultProductDialog(
            productToEdit = productToEdit,
            onDismiss = {
                showDialog = false
                productToEdit = null
            },
            onSave = { product, imageUri ->
                scope.launch {
                    if (productToEdit == null) {
                        createProduct(db, product, imageUri, context) { newProductId, newPhotoUrl ->
                            // Crear copia con el ID y URL correctos para la inyección
                            val productWithId = product.copy(id = newProductId, photoUrl = newPhotoUrl)

                            // Inyectar a todos los colmados
                            scope.launch {
                                injectDefaultProductToAllColmados(
                                    db = db,
                                    productId = newProductId,
                                    product = productWithId,
                                    context = context
                                )
                            }
                        }
                    } else {
                        updateProduct(db, product, imageUri, context)
                    }
                    showDialog = false
                    productToEdit = null
                }
            }
        )
    }

    // Confirmación de eliminación PERMANENTE
    if (showDeleteConfirm && productToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                productToDelete = null
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "ELIMINAR PERMANENTEMENTE",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "'${productToDelete?.name}'",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Esta acción es IRREVERSIBLE:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Se eliminará de la base de datos")
                    Text("• Se eliminará de TODOS los colmados")
                    Text("• NO se puede recuperar")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Usa OCULTAR si solo quieres esconderlo temporalmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            deleteProduct(db, productToDelete!!.id, context)
                            showDeleteConfirm = false
                            productToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ELIMINAR PERMANENTEMENTE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDeleteConfirm = false
                    productToDelete = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Card para mostrar un producto por defecto
 */
@Composable
fun ProductDefaultCard(
    product: DefaultProduct,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    val isHidden = !product.isVisible
    val isInactive = !product.isActive

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isInactive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                isHidden -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Banner de estado si está oculto o inactivo
            if (isHidden || isInactive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isInactive) MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.tertiary
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isInactive) Icons.Default.Block else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (isInactive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isInactive) "PRODUCTO INACTIVO" else "OCULTO EN COLMADOS",
                        color = if (isInactive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Fila Superior: Imagen + Info Principal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Imagen
                    if (product.photoUrl.isNotEmpty()) {
                        ProductImage(
                            imageData = product.photoUrl,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, null, tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Info Texto
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            lineHeight = 20.sp
                        )

                        if (product.description.isNotEmpty()) {
                            Text(
                                text = product.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Chips de Categoría y Unidad (Scrollable si es necesario)
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { },
                                label = { Text(product.category, fontSize = 10.sp) },
                                modifier = Modifier.height(24.dp),
                                border = null,
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFEEEEEE))
                            )

                            AssistChip(
                                onClick = { },
                                label = { Text(product.unitType, fontSize = 10.sp) },
                                modifier = Modifier.height(24.dp),
                                border = null,
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFEEEEEE))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Fila Media: Precio y Estado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Precio
                    Column {
                        Text(
                            text = "Precio Sugerido",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "RD$ ${product.suggestedPrice.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Badges de Estado (Columna derecha)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Badge Activo/Inactivo
                        ContainerBadge(
                            text = if (product.isActive) "Activo" else "Inactivo",
                            color = if (product.isActive) Color(0xFF4CAF50) else Color(0xFFFF5722)
                        )
                        // Badge Visible/Oculto
                        ContainerBadge(
                            text = if (product.isVisible) "Visible" else "Oculto",
                            color = if (product.isVisible) Color(0xFF2196F3) else Color(0xFF9E9E9E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fila Inferior: Botones de Acción (Distribuidos uniformemente)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Espaciado uniforme
                ) {
                    // Botón Editar (Peso 1)
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    }

                    // Botón Visibilidad (Peso 1)
                    OutlinedButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (product.isVisible) Color(0xFFEF6C00) else Color(0xFF1976D2)
                        )
                    ) {
                        Icon(
                            if (product.isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Botón Activar/Desactivar (Peso 1)
                    OutlinedButton(
                        onClick = onToggleActive,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (product.isActive) Color(0xFFC62828) else Color(0xFF2E7D32)
                        )
                    ) {
                        Icon(
                            if (product.isActive) Icons.Default.Block else Icons.Default.CheckCircle,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Botón Eliminar (Peso 0.8 - un poco más pequeño visualmente o igual)
                    FilledTonalIconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(40.dp), // Cuadrado para eliminar
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFE53935)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ContainerBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = color
        )
    }
}

/**
 * Diálogo para crear o editar producto
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultProductDialog(
    productToEdit: DefaultProduct?,
    onDismiss: () -> Unit,
    onSave: (DefaultProduct, Uri?) -> Unit
) {
    val context = LocalContext.current
    
    // Estados del formulario
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var description by remember { mutableStateOf(productToEdit?.description ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "Víveres") }
    var suggestedPrice by remember { mutableStateOf(productToEdit?.suggestedPrice?.toString() ?: "") }
    var unitType by remember { mutableStateOf(productToEdit?.unitType ?: "UNIDAD") }
    var barcode by remember { mutableStateOf(productToEdit?.barcode ?: "") }
    
    // Estados de UI
    var expandedCategory by remember { mutableStateOf(false) }
    
    // Estados de Imagen
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoUrl by remember { mutableStateOf(productToEdit?.photoUrl ?: "") }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success && tempCameraUri != null) photoUri = tempCameraUri }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val (file, uri) = CameraHelper.createImageFile(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
        }
    }

    val isValid = name.isNotEmpty() && suggestedPrice.toDoubleOrNull() != null

    // Diálogo a Pantalla Completa para experiencia "Premium"
    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (productToEdit != null) "Editar Producto" else "Nuevo Producto") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val product = DefaultProduct(
                                    id = productToEdit?.id ?: "",
                                    name = name.trim(),
                                    description = description.trim(),
                                    category = category,
                                    suggestedPrice = suggestedPrice.toDoubleOrNull() ?: 0.0,
                                    unitType = unitType,
                                    photoUrl = photoUrl,
                                    barcode = barcode.trim(),
                                    isActive = productToEdit?.isActive ?: true,
                                    isVisible = productToEdit?.isVisible ?: true,
                                    createdAt = productToEdit?.createdAt ?: Timestamp.now(),
                                    updatedAt = Timestamp.now(),
                                    createdBy = productToEdit?.createdBy ?: "admin"
                                )
                                onSave(product, photoUri)
                            },
                            enabled = isValid
                        ) {
                            Text("Guardar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                )
            },
            containerColor = Color.White
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Selector de Imagen "Premium"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5F5))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { showImageSourceDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else if (photoUrl.isNotEmpty()) {
                        ProductImage(imageData = photoUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Agregar Foto del Producto", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // 2. Información Básica
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre del Producto") },
                    placeholder = { Text("Ej: Arroz Selecto") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // 3. Selector de Tipo de Venta (Visual)
                Column {
                    Text("¿Cómo se vende este producto?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("UNIDAD", "LIBRA", "PAQUETE").forEach { type ->
                            val isSelected = unitType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { unitType = type },
                                label = { 
                                    Text(
                                        type, 
                                        modifier = Modifier.fillMaxWidth(), 
                                        textAlign = TextAlign.Center,
                                        fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = if(isSelected) MaterialTheme.colorScheme.primary else Color.LightGray)
                            )
                        }
                    }
                }

                // 4. Precio Dinámico según Tipo
                val priceLabel = when (unitType) {
                    "LIBRA" -> "Precio por Libra (RD$)"
                    "PAQUETE" -> "Precio por Paquete (RD$)"
                    else -> "Precio por Unidad (RD$)"
                }
                
                OutlinedTextField(
                    value = suggestedPrice, onValueChange = { suggestedPrice = it },
                    label = { Text(priceLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Text("$", fontWeight = FontWeight.Bold) }
                )

                // 5. Categoría
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = it }
                ) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        DefaultProduct.CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = { category = cat; expandedCategory = false }
                            )
                        }
                    }
                }

                // 6. Detalles Adicionales
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descripción (Opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = barcode, onValueChange = { barcode = it },
                    label = { Text("Código de Barras (Opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.QrCode, null) }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Diálogo de selección de foto
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Foto del Producto") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Tomar Foto") },
                        leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                        modifier = Modifier.clickable {
                            showImageSourceDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Galería") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                        modifier = Modifier.clickable {
                            showImageSourceDialog = false
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImageSourceDialog = false }) { Text("Cancelar") } }
        )
    }
}

/**
 * Estado vacío para productos por defecto
 */
@Composable
fun EmptyProductsState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Inventory,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Gray
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    }
}

// ========================================
// FUNCIONES DE FIRESTORE
// ========================================

/**
 * Crear producto por defecto
 */
suspend fun createProduct(
    db: FirebaseFirestore,
    product: DefaultProduct,
    imageUri: Uri?,
    context: android.content.Context,
    onSuccess: (String, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            // Usar mapa explícito para garantizar que todos los campos se guarden
            val productData = hashMapOf(
                "name" to product.name,
                "description" to product.description,
                "category" to product.category,
                "suggestedPrice" to product.suggestedPrice,
                "unitType" to product.unitType,
                "photoUrl" to "",
                "barcode" to product.barcode,
                "isActive" to true,
                "isVisible" to true,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "updatedAt" to com.google.firebase.Timestamp.now(),
                "createdBy" to product.createdBy
            )

            val docRef = db.collection("default_products")
                .add(productData)
                .await()

            val productId = docRef.id
            var finalPhotoUrl = ""

            // Subir imagen si existe
            if (imageUri != null) {
                val photoUrl = ImageStorageManager.uploadImage(
                    context = context,
                    imageUri = imageUri,
                    ownerId = "admin",
                    imageType = ImageStorageManager.ImageType.DEFAULT_PRODUCT,
                    productId = productId
                )

                if (photoUrl != null) {
                    docRef.update("photoUrl", photoUrl).await()
                    finalPhotoUrl = photoUrl
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Producto creado exitosamente", Toast.LENGTH_SHORT).show()
                onSuccess(productId, finalPhotoUrl)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Actualizar producto por defecto
 */
suspend fun updateProduct(
    db: FirebaseFirestore,
    product: DefaultProduct,
    imageUri: Uri?,
    context: android.content.Context
) {
    withContext(Dispatchers.IO) {
        try {
            var photoUrl = product.photoUrl

            // Subir nueva imagen si se seleccionó una
            if (imageUri != null) {
                val newPhotoUrl = ImageStorageManager.uploadImage(
                    context = context,
                    imageUri = imageUri,
                    ownerId = "admin",
                    imageType = ImageStorageManager.ImageType.DEFAULT_PRODUCT,
                    productId = product.id
                )
                if (newPhotoUrl != null) {
                    photoUrl = newPhotoUrl
                }
            }

            // Usar mapa explícito para garantizar que todos los campos se actualicen
            val productData = hashMapOf(
                "name" to product.name,
                "description" to product.description,
                "category" to product.category,
                "suggestedPrice" to product.suggestedPrice,
                "unitType" to product.unitType,
                "photoUrl" to photoUrl,
                "barcode" to product.barcode,
                "isActive" to product.isActive,
                "isVisible" to product.isVisible,
                "updatedAt" to com.google.firebase.Timestamp.now(),
                "createdBy" to product.createdBy
            )

            db.collection("default_products")
                .document(product.id)
                .update(productData as Map<String, Any>)
                .await()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Producto actualizado", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Eliminar producto PERMANENTEMENTE de la base de datos.
 * Primero elimina de todos los colmados, luego elimina el producto default.
 * Esta acción NO se puede deshacer.
 */
suspend fun deleteProduct(
    db: FirebaseFirestore,
    productId: String,
    context: android.content.Context
) {
    withContext(Dispatchers.IO) {
        try {
            // Primero eliminar de todos los colmados
            propagateDeleteToAllColmados(db, productId, context)

            // Luego eliminar el producto default de la base de datos (HARD DELETE)
            db.collection("default_products")
                .document(productId)
                .delete()
                .await()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Producto eliminado completamente", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Activar/Desactivar producto
 */
suspend fun toggleProductActive(
    db: FirebaseFirestore,
    product: DefaultProduct,
    context: android.content.Context
) {
    withContext(Dispatchers.IO) {
        try {
            db.collection("default_products")
                .document(product.id)
                .update("isActive", !product.isActive)
                .await()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (product.isActive) "Producto desactivado" else "Producto activado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Ocultar/Mostrar producto en todos los colmados.
 * Actualiza el campo isVisible en el producto default y propaga el cambio
 * automáticamente a todos los colmados que tienen este producto.
 */
suspend fun toggleProductVisibility(
    db: FirebaseFirestore,
    product: DefaultProduct,
    context: android.content.Context
) {
    withContext(Dispatchers.IO) {
        try {
            val newVisibility = !product.isVisible

            // Actualizar el producto default
            db.collection("default_products")
                .document(product.id)
                .update(
                    mapOf(
                        "isVisible" to newVisibility,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (newVisibility) "Mostrando producto..." else "Ocultando producto...",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Propagar el cambio a todos los colmados
            propagateVisibilityToAllColmados(db, product.id, newVisibility, context)

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Inyectar producto por defecto a todos los colmados
 */
suspend fun injectDefaultProductToAllColmados(
    db: FirebaseFirestore,
    productId: String,
    product: DefaultProduct,
    context: android.content.Context
) {
    withContext(Dispatchers.IO) {
        try {
            // Obtener todos los colmados aprobados
            val colmadosSnapshot = db.collection("colmados_requests")
                .whereEqualTo("status", "APPROVED")
                .get()
                .await()

            if (colmadosSnapshot.isEmpty) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No hay colmados aprobados", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            if (colmadosSnapshot.isEmpty) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No hay colmados aprobados para inyectar", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            // Batch write para inyectar a todos
            val batch = db.batch()
            var count = 0

            // Pre-calcular datos del producto para optimizar
            // Iterar sobre cada colmado
            for (colmadoDoc in colmadosSnapshot.documents) {
                val colmadoId = colmadoDoc.id

                // 1. Inyectar en colmado_inventory (Legacy/Backup)
                val inventoryRef = db.collection("colmado_inventory")
                    .document(colmadoId)
                    .collection("products")
                    .document(productId)
                
                val productData = product.toColmadoInventoryProduct(colmadoId)
                batch.set(inventoryRef, productData)

                // 2. Inyectar en colmado_products (NUEVO - Lo que ven los clientes)
                // Usamos ID determinista. Al ser un producto NUEVO, asumimos que no existe colisión.
                val deterministicId = "${colmadoId}_${productId}"
                val productsRef = db.collection("colmado_products").document(deterministicId)
                
                // Escritura directa sin lectura previa para velocidad y fiabilidad
                batch.set(productsRef, productData)
                count++
            }

            batch.commit().await()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "✅ Producto inyectado a $count colmados",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Propagar visibilidad de un producto a todos los colmados.
 * Actualiza el campo isVisibleFromAdmin en todos los productos de colmados
 * que tienen defaultProductId igual al producto.
 */
suspend fun propagateVisibilityToAllColmados(
    db: FirebaseFirestore,
    productId: String,
    isVisible: Boolean,
    context: android.content.Context
) {
    withContext(Dispatchers.IO) {
        try {
            // Buscar todos los productos de colmados que vienen de este default product
            val colmadosSnapshot = db.collection("colmados_requests")
                .whereEqualTo("status", "APPROVED")
                .get()
                .await()

            if (colmadosSnapshot.isEmpty) {
                return@withContext
            }

            var updatedCount = 0
            val batch = db.batch()

            for (colmadoDoc in colmadosSnapshot.documents) {
                val colmadoId = colmadoDoc.id

                // Buscar el producto en el inventario del colmado
                val productDoc = db.collection("colmado_inventory")
                    .document(colmadoId)
                    .collection("products")
                    .document(productId)
                    .get()
                    .await()

                if (productDoc.exists()) {
                    val ref = db.collection("colmado_inventory")
                        .document(colmadoId)
                        .collection("products")
                        .document(productId)

                    batch.update(ref, mapOf(
                        "isVisibleFromAdmin" to isVisible,
                        "updatedAt" to System.currentTimeMillis()
                    ))
                    updatedCount++
                }
            }

            // También buscar en colmado_products (estructura alternativa)
            val colmadoProductsSnapshot = db.collection("colmado_products")
                .whereEqualTo("defaultProductId", productId)
                .get()
                .await()

            for (doc in colmadoProductsSnapshot.documents) {
                val ref = db.collection("colmado_products").document(doc.id)
                batch.update(ref, mapOf(
                    "isVisibleFromAdmin" to isVisible,
                    "updatedAt" to System.currentTimeMillis()
                ))
                updatedCount++
            }

            if (updatedCount > 0) {
                batch.commit().await()
            }

            withContext(Dispatchers.Main) {
                val action = if (isVisible) "mostrado" else "ocultado"
                Toast.makeText(
                    context,
                    "Producto $action en $updatedCount colmados",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al propagar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Eliminar PERMANENTEMENTE un producto de todos los colmados.
 * Busca en colmado_inventory y colmado_products y elimina los documentos.
 */
suspend fun propagateDeleteToAllColmados(
    db: FirebaseFirestore,
    productId: String,
    context: android.content.Context
) {
    withContext(Dispatchers.IO) {
        try {
            var deletedCount = 0

            // Eliminar de colmado_inventory (estructura jerárquica)
            val colmadosSnapshot = db.collection("colmados_requests")
                .whereEqualTo("status", "APPROVED")
                .get()
                .await()

            for (colmadoDoc in colmadosSnapshot.documents) {
                val colmadoId = colmadoDoc.id

                val productDoc = db.collection("colmado_inventory")
                    .document(colmadoId)
                    .collection("products")
                    .document(productId)
                    .get()
                    .await()

                if (productDoc.exists()) {
                    // HARD DELETE - Eliminar el documento
                    db.collection("colmado_inventory")
                        .document(colmadoId)
                        .collection("products")
                        .document(productId)
                        .delete()
                        .await()
                    deletedCount++
                }
            }

            // También eliminar de colmado_products (estructura plana)
            val colmadoProductsSnapshot = db.collection("colmado_products")
                .whereEqualTo("defaultProductId", productId)
                .get()
                .await()

            for (doc in colmadoProductsSnapshot.documents) {
                // HARD DELETE - Eliminar el documento
                db.collection("colmado_products")
                    .document(doc.id)
                    .delete()
                    .await()
                deletedCount++
            }

            withContext(Dispatchers.Main) {
                if (deletedCount > 0) {
                    Toast.makeText(
                        context,
                        "Eliminado de $deletedCount colmados",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al eliminar de colmados: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Sincronizar todos los productos por defecto a todos los colmados
 */
suspend fun syncAllDefaultProducts(
    db: FirebaseFirestore,
    products: List<DefaultProduct>,
    onStart: () -> Unit,
    onComplete: (String) -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) { onStart() }

            val colmadosSnapshot = db.collection("colmados_requests")
                .whereEqualTo("status", "APPROVED")
                .get()
                .await()

            if (colmadosSnapshot.isEmpty) {
                withContext(Dispatchers.Main) {
                    onError("No hay colmados aprobados")
                }
                return@withContext
            }

            var totalInjected = 0
            var totalRepaired = 0

            products.forEach { product ->
                val batch = db.batch()
                var batchCount = 0

                for (colmadoDoc in colmadosSnapshot.documents) {
                    val colmadoId = colmadoDoc.id
                    val deterministicId = "${colmadoId}_${product.id}"
                    
                    // Referencia a la colección REAL que usan los clientes
                    val productsRef = db.collection("colmado_products").document(deterministicId)
                    
                    val existingDoc = productsRef.get().await()

                    if (!existingDoc.exists()) {
                        // 1. Inyectar nuevo
                        val productData = product.toColmadoInventoryProduct(colmadoId)
                        batch.set(productsRef, productData)
                        batchCount++
                        totalInjected++
                    } else {
                        // 2. Reparaciones
                        var needsUpdate = false
                        val updates = mutableMapOf<String, Any>()

                        // Reparar ID roto
                        val currentDefaultId = existingDoc.getString("defaultProductId") ?: ""
                        if (currentDefaultId != product.id) {
                            updates["defaultProductId"] = product.id
                            needsUpdate = true
                        }

                        // Reparar visibilidad "atascada" (Si stock es 0 y está oculto, probablemente fue una inyección fallida anterior)
                        // Esto fuerza la visibilidad para productos nuevos que no se vieron
                        val isAvailable = existingDoc.getBoolean("isAvailable") ?: false
                        val stock = existingDoc.getLong("stock") ?: 0L
                        
                        if (!isAvailable && stock == 0L) {
                            updates["isAvailable"] = true
                            needsUpdate = true
                        }

                        if (needsUpdate) {
                            batch.update(productsRef, updates)
                            batchCount++
                            totalRepaired++
                        }
                    }
                    
                    // También actualizar inventory legacy por consistencia (opcional, pero rápido en batch)
                    val inventoryRef = db.collection("colmado_inventory")
                        .document(colmadoId)
                        .collection("products")
                        .document(product.id)
                    // Solo set si no existe para no borrar data
                    // (Omitido para ahorrar operaciones de lectura, nos enfocamos en colmado_products)
                }

                if (batchCount > 0) {
                    batch.commit().await()
                }
            }

            withContext(Dispatchers.Main) {
                onComplete("✅ Sincronizados: $totalInjected nuevos, $totalRepaired reparados/activados")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Error desconocido")
            }
        }
    }
}
