package com.domifiao.domifiao.ui.screens.client

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.ProductImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// Modelo local para búsqueda
data class SearchResult(
    val productId: String,
    val productName: String,
    val productPrice: Int,
    val productPhoto: String,
    val colmadoId: String,
    val colmadoName: String,
    val colmadoMunicipality: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var userMunicipality by remember { mutableStateOf("") }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    // Cargar municipio del usuario para priorizar
    LaunchedEffect(Unit) {
        val uid = sessionManager.getUserId()
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                userMunicipality = doc.getString("municipality") ?: ""
            }
        }
    }

    fun performSearch() {
        if (searchText.trim().length < 2) {
            Toast.makeText(context, "Ingresa al menos 2 letras", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        errorMessage = ""
        
        // Búsqueda simple: Por nombre exacto o prefijo (limitación Firestore)
        // Optimizamos buscando en la colección global de productos
        db.collectionGroup("products")
            .whereGreaterThanOrEqualTo("name", searchText.trim())
            .whereLessThanOrEqualTo("name", searchText.trim() + "\uf8ff")
            .limit(40)
            .get()
            .addOnSuccessListener { snapshot ->
                val results = snapshot.documents.mapNotNull { doc ->
                    try {
                        val isVisible = doc.getBoolean("isVisibleFromAdmin") ?: true
                        val isAvailable = doc.getBoolean("isAvailable") ?: true
                        if (!isVisible || !isAvailable) return@mapNotNull null

                        SearchResult(
                            productId = doc.id,
                            productName = doc.getString("name") ?: "",
                            productPrice = doc.getLong("price")?.toInt() ?: 0,
                            productPhoto = doc.getString("photoUrl") ?: "",
                            colmadoId = doc.getString("colmadoId") ?: "",
                            colmadoName = doc.getString("colmadoName") ?: "Colmado",
                            colmadoMunicipality = doc.getString("colmadoMunicipality") ?: ""
                        )
                    } catch (e: Exception) { null }
                }

                // Priorizar los del mismo municipio
                searchResults = if (userMunicipality.isNotEmpty()) {
                    results.sortedWith(compareByDescending<SearchResult> { it.colmadoMunicipality == userMunicipality }.thenBy { it.productName })
                } else {
                    results.sortedBy { it.productName }
                }

                hasSearched = true
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
                errorMessage = "Error al buscar. Revisa tu conexión."
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Buscador Global", fontWeight = FontWeight.Bold)
                        if (userMunicipality.isNotEmpty()) {
                            Text(userMunicipality, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // BARRA DE BÚSQUEDA
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Ej: Cerveza, Arroz...") },
                leadingIcon = {
                    IconButton(onClick = { performSearch() }) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            searchResults = emptyList()
                            hasSearched = false
                            errorMessage = ""
                        }) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { performSearch() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // MENSAJE INFORMATIVO
            if (userMunicipality.isNotEmpty() && searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemInDarkTheme()) Color(0xFF0D47A1).copy(alpha = 0.3f) else Color(0xFFE3F2FD)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Mostrando primero colmados de $userMunicipality",
                            fontSize = 12.sp,
                            color = if(isSystemInDarkTheme()) Color(0xFFBBDEFB) else Color(0xFF1565C0)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // RESULTADOS
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (errorMessage.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMessage, color = Color.Red)
                }
            } else if (hasSearched && searchResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No encontramos lo que buscas", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults) { result ->
                        SearchItemCard(result) {
                            navController.navigate(AppScreens.ClientColmadoProducts.createRoute(result.colmadoId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchItemCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (result.productPhoto.isNotEmpty()) {
                ProductImage(
                    imageData = result.productPhoto,
                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(70.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(result.productName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(result.colmadoName, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.colmadoMunicipality, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("RD$ ${result.productPrice}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF4CAF50))
                Text("Ir al colmado", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
