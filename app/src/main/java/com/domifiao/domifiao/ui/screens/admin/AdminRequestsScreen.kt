package com.domifiao.domifiao.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign
import com.domifiao.domifiao.model.ProductSeeder
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.domifiao.domifiao.model.GlobalProduct
import com.domifiao.domifiao.model.DefaultProduct
import com.domifiao.domifiao.navigation.AppScreens

// Modelo de datos completo para la solicitud
data class ColmadoRequest(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val province: String,
    val photoUrl: String,           // Foto del Local (URL o Base64)
    val ownerName: String,
    val ownerCedula: String,
    val ownerIdPhotoUrl: String,    // Foto de la Cedula (URL o Base64)
    val isIdentityVerified: Boolean,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRequestsScreen(navController: NavController) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Navegacion segura
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope() // Para ejecutar la inyección

    // Estado de la lista
    var colmadoRequests by remember { mutableStateOf<List<ColmadoRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Estado de Inyección de Productos
    var isSeeding by remember { mutableStateOf(false) }

    // Estado para el visor de fotos (Popup)
    var selectedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var selectedPhotoTitle by remember { mutableStateOf("") }

    // --- ESTADOS PARA DIÁLOGO DE RECHAZO ---
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectionReason by remember { mutableStateOf("") }
    var requestToRejectId by remember { mutableStateOf<String?>(null) }

    // --- CARGAR DATOS (SOLO COLMADOS PENDIENTES) ---
    LaunchedEffect(Unit) {
        db.collection("colmados_requests")
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    colmadoRequests = snapshot.documents.map { doc ->
                        ColmadoRequest(
                            id = doc.id,
                            name = doc.getString("name") ?: "Sin Nombre",
                            phone = doc.getString("phone") ?: "",
                            address = doc.getString("address") ?: "",
                            province = doc.getString("province") ?: "",
                            photoUrl = doc.getString("photoUrl")
                                ?: doc.getString("photoBase64") ?: "",
                            ownerName = doc.getString("ownerName") ?: "Desconocido",
                            ownerCedula = doc.getString("ownerCedula") ?: "N/A",
                            ownerIdPhotoUrl = doc.getString("ownerIdPhotoUrl")
                                ?: doc.getString("ownerIdPhotoBase64") ?: "",
                            isIdentityVerified = doc.getBoolean("isIdentityVerified") ?: false,
                            timestamp = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                }
                isLoading = false
            }
    }

    // --- LÓGICA DE APROBACIÓN / RECHAZO ---
    // --- FUNCIÓN DE INYECCIÓN AUTOMÁTICA DE PRODUCTOS ---
    suspend fun injectBasicCatalogToColmado(colmadoId: String): String {
        return try {
            // 1. Obtener productos del catálogo global
            val globalProducts = db.collection("global_products")
                .get()
                .await()

            if (globalProducts.isEmpty) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "⚠️ Catálogo vacío. Usa el botón ☁️ primero.", Toast.LENGTH_LONG).show()
                }
                return "Catálogo global vacío"
            }

            // 2. Filtrar categorías esenciales
            val essentialCategories = listOf("Bebidas", "Despensa", "Lácteos", "Snacks", "Higiene")
            val essentialProducts = globalProducts.documents.mapNotNull { doc ->
                try {
                    val product = doc.toObject(GlobalProduct::class.java)
                    if (product != null && product.category in essentialCategories) product else null
                } catch (e: Exception) { null }
            }

            if (essentialProducts.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "⚠️ Sin productos básicos", Toast.LENGTH_SHORT).show()
                }
                return "Sin productos básicos"
            }

            // 3. Copiar productos al inventario del colmado
            val batch = db.batch()
            var count = 0

            essentialProducts.forEach { product ->
                val colmadoProduct = hashMapOf(
                    "barcode" to product.barcode,
                    "name" to product.name,
                    "category" to product.category,
                    "searchName" to product.name.trim().lowercase(),
                    "price" to 0.0,
                    "stock" to 0,
                    "isAvailable" to false
                )

                val ref = db.collection("colmado_inventory")
                    .document(colmadoId)
                    .collection("products")
                    .document(product.barcode)

                batch.set(ref, colmadoProduct)
                count++
            }

            batch.commit().await()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "✅ $count productos inyectados", Toast.LENGTH_LONG).show()
            }

            "✅ $count productos inyectados"
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            "Error: ${e.message}"
        }
    }

    /**
     * Inyectar todos los productos por defecto a un colmado específico.
     * Se usa al aprobar un nuevo colmado.
     */
    suspend fun injectAllDefaultProductsToColmado(
        colmadoId: String,
        context: android.content.Context,
        db: FirebaseFirestore
    ) {
        return withContext(Dispatchers.IO) {
            try {
                // Obtener todos los productos por defecto activos
                val defaultProducts = db.collection("default_products")
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()

                if (defaultProducts.isEmpty) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No hay productos por defecto configurados", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                // Batch write para inyectar todos los productos
                val batch = db.batch()
                var count = 0

                defaultProducts.documents.forEach { doc ->
                    val product = doc.toObject(DefaultProduct::class.java)?.copy(id = doc.id)
                    if (product != null) {
                        // Verificar si ya existe (para evitar duplicados)
                        val existingProduct = db.collection("colmado_inventory")
                            .document(colmadoId)
                            .collection("products")
                            .document(doc.id)
                            .get()
                            .await()

                        if (!existingProduct.exists()) {
                            val productData = product.toColmadoInventoryProduct(colmadoId)

                            val ref = db.collection("colmado_inventory")
                                .document(colmadoId)
                                .collection("products")
                                .document(doc.id)

                            batch.set(ref, productData)
                            count++
                        }
                    }
                }

                batch.commit().await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ $count productos por defecto inyectados", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error inyectando productos por defecto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun processColmado(requestId: String, isApproved: Boolean, reason: String = "") {
        val newStatus = if (isApproved) "APPROVED" else "REJECTED"

        val updates = hashMapOf<String, Any>(
            "status" to newStatus
        )
        // Si es rechazo, guardamos la razón para que el usuario la vea
        if (!isApproved) {
            updates["rejectionReason"] = reason
        }

        db.collection("colmados_requests").document(requestId)
            .update(updates)
            .addOnSuccessListener {
                val msg = if (isApproved) "Colmado Aprobado ✅" else "Solicitud Rechazada"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                // NUEVO: Inyectar productos automáticamente al aprobar
                if (isApproved) {
                    scope.launch {
                        // Inyectar catálogo básico (función existente)
                        injectBasicCatalogToColmado(requestId)

                        // Inyectar productos por defecto
                        injectAllDefaultProductsToColmado(requestId, context, db)
                    }
                }

                // Limpiar estados de diálogo
                showRejectDialog = false
                rejectionReason = ""
                requestToRejectId = null
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al actualizar", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel Administrativo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // Botón para ir a gestión de productos por defecto
                    IconButton(onClick = {
                        navController.navigate(AppScreens.AdminDefaultProducts.route)
                    }) {
                        Icon(Icons.Default.Inventory, contentDescription = "Productos por Defecto")
                    }

                    // Botón existente de inyección de catálogo global
                    if (isSeeding) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            isSeeding = true
                            scope.launch {
                                val result = ProductSeeder.injectCatalog()
                                isSeeding = false
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Inyectar Catálogo Global")
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
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                if (colmadoRequests.isEmpty()) {
                    EmptyState("No hay solicitudes pendientes")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(colmadoRequests) { req ->
                            AdminColmadoCard(
                                request = req,
                                onViewFachada = {
                                    selectedPhotoUrl = req.photoUrl
                                    selectedPhotoTitle = "Local: ${req.name}"
                                },
                                onViewCedula = {
                                    selectedPhotoUrl = req.ownerIdPhotoUrl
                                    selectedPhotoTitle = "Cédula: ${req.ownerName}"
                                },
                                onApprove = { processColmado(req.id, true) },
                                onReject = {
                                    requestToRejectId = req.id
                                    showRejectDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- DIÁLOGO DE RECHAZO (NUEVO) ---
    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = {
                showRejectDialog = false
                rejectionReason = ""
            },
            title = { Text("Rechazar Solicitud", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Indica el motivo para que el usuario pueda corregirlo:", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Motivo del rechazo") },
                        placeholder = { Text("Ej: Foto de cédula borrosa") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejectionReason.isNotEmpty() && requestToRejectId != null) {
                            processColmado(requestToRejectId!!, false, rejectionReason)
                        } else {
                            Toast.makeText(context, "Debes escribir un motivo", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Confirmar Rechazo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false; rejectionReason = "" }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // --- DIÁLOGO VISOR DE FOTOS ---
    if (selectedPhotoUrl != null) {
        Dialog(onDismissRequest = { selectedPhotoUrl = null }) {
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                Column {
                    // Header del Popup
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = selectedPhotoTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                        IconButton(
                            onClick = { selectedPhotoUrl = null },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    // Imagen
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        com.domifiao.domifiao.ui.components.StorageImage(
                            imageData = selectedPhotoUrl!!,
                            contentDescription = selectedPhotoTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

// --- CARD DE COLMADO ---
@Composable
fun AdminColmadoCard(
    request: ColmadoRequest,
    onViewFachada: () -> Unit,
    onViewCedula: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabecera: Nombre y Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Store, null, tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = request.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Badge de Verificación IA
                if (request.isIdentityVerified) {
                    AssistChip(
                        onClick = {},
                        label = { Text("IA OK", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Verified, null, modifier = Modifier.size(12.dp), tint = Color(0xFF4CAF50)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFE8F5E9).copy(alpha = 0.2f), 
                            labelColor = Color(0xFF4CAF50)
                        ),
                        border = null,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text("MANUAL", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Warning, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF9800)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFFFF3E0).copy(alpha = 0.2f), 
                            labelColor = Color(0xFFFF9800)
                        ),
                        border = null,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Detalles del Dueño
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Propietario:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = request.ownerName,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, bottom = 2.dp)
            )
            Text(
                text = "Cédula: ${request.ownerCedula}",
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Detalles de Ubicación
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ubicación:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "${request.address}, ${request.province}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, bottom = 2.dp)
            )
            Text(
                text = "Tel: ${request.phone}",
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp),
                color = Color(0xFF2196F3)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de Fotos (Acciones de Verificación)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onViewFachada,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Storefront, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Local", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onViewCedula,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Badge, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Cédula", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botones de Decisión (Aprobar/Rechazar)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer, 
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Rechazar")
                }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Aprobar")
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircleOutline, 
            null, 
            tint = MaterialTheme.colorScheme.outlineVariant, 
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message, 
            color = MaterialTheme.colorScheme.onSurfaceVariant, 
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}