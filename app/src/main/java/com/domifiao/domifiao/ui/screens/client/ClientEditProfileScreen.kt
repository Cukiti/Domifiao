package com.domifiao.domifiao.ui.screens.client

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Geocoder
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.ui.components.dominicanProvinces
import com.domifiao.domifiao.utils.ImageStorageManager
import com.domifiao.domifiao.utils.toFirebasePhoneNumber
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.domifiao.domifiao.services.RecaptchaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditProfileScreen(
    navController: NavController,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    recaptchaService: RecaptchaService? = null
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid
    val sessionManager = SessionManager(context)
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Estados
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var cedula by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var newPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var houseDescription by remember { mutableStateOf("") }
    var housePhotoUrl by remember { mutableStateOf("") }
    var newHousePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var address by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var municipality by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var phoneNumberInput by remember { mutableStateOf("") }
    var isCedulaVerified by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isGpsLoading by remember { mutableStateOf(false) }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (!isSaving) navigateBackSecurely() }

    LaunchedEffect(userId) {
        if (userId != null) {
            db.collection("users").document(userId).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    name = doc.getString("name") ?: ""
                    email = doc.getString("email") ?: ""
                    cedula = doc.getString("cedula") ?: ""
                    address = doc.getString("address") ?: ""
                    province = doc.getString("province") ?: ""
                    municipality = doc.getString("municipality") ?: ""
                    latitude = doc.getDouble("latitude") ?: 0.0
                    longitude = doc.getDouble("longitude") ?: 0.0
                    photoUrl = doc.getString("photoUrl") ?: doc.getString("photoBase64") ?: ""
                    housePhotoUrl = doc.getString("housePhotoUrl") ?: doc.getString("housePhotoBase64") ?: ""
                    houseDescription = doc.getString("houseDescription") ?: ""
                    phoneNumberInput = (doc.getString("phone") ?: "").replace("+1", "")
                    isCedulaVerified = doc.getBoolean("isIdentityVerified") ?: false
                }
                isLoading = false
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { newPhotoUri = it } }
    val houseImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { newHousePhotoUri = it } }

    fun saveProfile() {
        if (userId == null) return
        isSaving = true
        scope.launch {
            try {
                val updates = mutableMapOf<String, Any>(
                    "name" to name, "email" to email, "address" to address,
                    "province" to province, "municipality" to municipality,
                    "latitude" to latitude, "longitude" to longitude,
                    "houseDescription" to houseDescription
                )

                if (newPhotoUri != null) {
                    val url = ImageStorageManager.uploadImage(context, newPhotoUri!!, userId, ImageStorageManager.ImageType.USER_PROFILE)
                    if (url != null) updates["photoUrl"] = url
                }
                if (newHousePhotoUri != null) {
                    val url = ImageStorageManager.uploadImage(context, newHousePhotoUri!!, userId, ImageStorageManager.ImageType.USER_HOUSE)
                    if (url != null) updates["housePhotoUrl"] = url
                }

                db.collection("users").document(userId).update(updates).addOnSuccessListener {
                    Toast.makeText(context, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    navigateBackSecurely()
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
                title = { Text("Mi Perfil", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (!isSaving) navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    Surface(
                        onClick = { onToggleDarkMode() },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF5F5F5),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkMode) Color(0xFFFFD700).copy(alpha = 0.5f) else Color.LightGray),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = if (isDarkMode) Color(0xFFFFD700) else Color(0xFFFF8C00), modifier = Modifier.size(16.dp))
                            Text(if (isDarkMode) "Oscuro" else "Claro", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // FOTO DE PERFIL
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.padding(16.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, MaterialTheme.colorScheme.primary.copy(0.5f), CircleShape)
                                .clickable { imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, 
                            contentAlignment = Alignment.Center
                        ) {
                            if (newPhotoUri != null) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(newPhotoUri), 
                                    contentDescription = null, 
                                    modifier = Modifier.fillMaxSize(), 
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else if (photoUrl.isNotEmpty()) com.domifiao.domifiao.ui.components.StorageImage(imageData = photoUrl, modifier = Modifier.fillMaxSize(), contentDescription = "perfil")
                            else Icon(Icons.Default.Person, null, Modifier.size(50.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)) { Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre Completo") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                }

                // INFO PERSONAL
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Información de Seguridad", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email de Recuperación") }, leadingIcon = { Icon(Icons.Default.Email, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = cedula, onValueChange = { }, label = { Text("Cédula") }, leadingIcon = { Icon(Icons.Default.Badge, null) }, readOnly = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), trailingIcon = { Icon(if(isCedulaVerified) Icons.Default.VerifiedUser else Icons.Default.Warning, null, tint = if(isCedulaVerified) Color(0xFF4CAF50) else Color(0xFFFF9800)) })
                    }
                }

                // UBICACIÓN
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Ubicación", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Dirección Exacta") }, leadingIcon = { Icon(Icons.Default.LocationOn, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        
                        // FOTO DE LA CASA
                        Text("Referencia de Fachada", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { houseImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (newHousePhotoUri != null) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(newHousePhotoUri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else if (housePhotoUrl.isNotEmpty()) com.domifiao.domifiao.ui.components.StorageImage(imageData = housePhotoUrl, modifier = Modifier.fillMaxSize(), contentDescription = "casa")
                            else Icon(Icons.Default.AddHomeWork, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Button(
                    onClick = { saveProfile() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text("GUARDAR CAMBIOS", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
