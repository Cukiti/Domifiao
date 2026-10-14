package com.domifiao.domifiao.ui.screens.colmado

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.utils.ImageStorageManager
import com.domifiao.domifiao.ui.components.BarcodeScannerView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

data class GlobalSearchItem(
    val barcode: String,
    val name: String,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var colmadoName by remember { mutableStateOf("") }
    var colmadoProvince by remember { mutableStateOf("") }
    var colmadoMunicipality by remember { mutableStateOf("") }

    var barcode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Viveres") }
    var unitType by remember { mutableStateOf("UNIDAD") }
    var description by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    var showScanner by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Estado para el menú de unidades
    var unitMenuExpanded by remember { mutableStateOf(false) }

    val categories = listOf("Viveres", "Bebidas", "Alcohol", "Limpieza", "Embutidos", "Lacteos", "Snacks", "Higiene", "Otros")
    val unitTypes = listOf("UNIDAD", "LIBRA", "PAQUETE")

    LaunchedEffect(colmadoId) {
        if (colmadoId != null) {
            db.collection("colmados_requests").document(colmadoId).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    colmadoName = doc.getString("name") ?: ""
                    colmadoProvince = doc.getString("province") ?: ""
                    colmadoMunicipality = doc.getString("municipality") ?: ""
                }
            }
        }
    }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (showScanner) showScanner = false else if (!isLoading) navigateBackSecurely() }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photoUri = uri }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) showScanner = true }

    fun saveProduct() {
        if (colmadoId == null || name.isEmpty() || price.isEmpty()) {
            Toast.makeText(context, "Nombre y precio son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true
        scope.launch {
            try {
                var finalPhotoUrl = ""
                if (photoUri != null) {
                    finalPhotoUrl = ImageStorageManager.uploadImage(
                        context = context,
                        imageUri = photoUri!!,
                        ownerId = colmadoId,
                        imageType = ImageStorageManager.ImageType.COLMADO_PRODUCT
                    ) ?: ""
                }

                val productData = hashMapOf(
                    "colmadoId" to colmadoId, "colmadoName" to colmadoName, "colmadoProvince" to colmadoProvince,
                    "colmadoMunicipality" to colmadoMunicipality, "barcode" to barcode, "name" to name,
                    "price" to price.toDouble(), "category" to category, "unitType" to unitType,
                    "description" to description, "photoUrl" to finalPhotoUrl, "isAvailable" to true,
                    "isSelling" to true, "createdAt" to System.currentTimeMillis()
                )

                db.collection("colmado_products").add(productData)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Producto guardado", Toast.LENGTH_SHORT).show()
                        navigateBackSecurely()
                    }
                    .addOnFailureListener { e ->
                        isSaving = false
                        Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
                        android.util.Log.e("AddProductScreen", "Error: ", e)
                    }
            } catch (e: Exception) {
                isSaving = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Producto", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { saveProduct() },
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ) {
                if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("GUARDAR", fontWeight = FontWeight.Bold) }
            }
        }
    ) { padding ->
        if (showScanner) {
            BarcodeScannerView(onBarcodeDetected = { barcode = it; showScanner = false })
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // FOTO
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).clickable { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                    if (photoUri != null) {
                        androidx.compose.foundation.Image(painter = coil.compose.rememberAsyncImagePainter(photoUri), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("Agregar Foto", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                OutlinedTextField(
                    value = barcode, onValueChange = { barcode = it }, label = { Text("Código de Barras (Opcional)") },
                    trailingIcon = { IconButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) { Icon(Icons.Default.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary) } },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface)
                )

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre del Producto *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Precio RD$ *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface))
                    
                    ExposedDropdownMenuBox(
                        expanded = unitMenuExpanded,
                        onExpandedChange = { unitMenuExpanded = !unitMenuExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = unitType, 
                            onValueChange = { }, 
                            label = { Text("Unidad") }, 
                            modifier = Modifier.menuAnchor(), 
                            shape = RoundedCornerShape(12.dp), 
                            readOnly = true, 
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) }, 
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface)
                        )
                        ExposedDropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false }
                        ) {
                            unitTypes.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        unitType = selectionOption
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text("Categoría", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                ScrollableTabRow(selectedTabIndex = categories.indexOf(category), edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                    categories.forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) }, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }

                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción (Opcional)") }, modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface))
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
