package com.domifiao.domifiao.ui.screens.colmado

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.utils.ImageStorageManager
import com.domifiao.domifiao.utils.toFirebasePhoneNumber
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.domifiao.domifiao.services.RecaptchaService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoSettingsScreen(
    navController: NavController,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    recaptchaService: RecaptchaService? = null
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

        // Estados de Datos


        var colmadoName by remember { mutableStateOf("") }


        var email by remember { mutableStateOf("") }


        var address by remember { mutableStateOf("") }


        var province by remember { mutableStateOf("") } // NUEVO
    var municipality by remember { mutableStateOf("") } // NUEVO

    // Gestión de Teléfono
    var currentSavedPhone by remember { mutableStateOf("") }
    var phoneNumberInput by remember { mutableStateOf("") }
    var isPhoneVerified by remember { mutableStateOf(true) }

    // Estados SMS
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var isSendingSms by remember { mutableStateOf(false) }

    // Configuración Negocio
    var deliveryFee by remember { mutableStateOf("0") }
    var hasDeliveryService by remember { mutableStateOf(true) }
    var isAcceptingCredit by remember { mutableStateOf(true) }
    var isHiringDelivery by remember { mutableStateOf(false) }
    var isOpen by remember { mutableStateOf(true) }
    var photoUrl by remember { mutableStateOf("") }
    var newPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val scope = rememberCoroutineScope()

    // Contador de solicitudes
    var pendingApplicationsCount by remember { mutableIntStateOf(0) }

    // Estado para confirmación de logout
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (!isSaving) navigateBackSecurely() }

    // --- CARGAR DATOS ---
    LaunchedEffect(colmadoId) {
        if (colmadoId != null) {
            // 1. Datos del Colmado
            db.collection("colmados_requests").document(colmadoId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                                                colmadoName = doc.getString("name") ?: ""

                                                email = doc.getString("email") ?: ""

                                                address = doc.getString("address") ?: ""

                                                province = doc.getString("province") ?: "" // NUEVO
                        municipality = doc.getString("municipality") ?: "" // NUEVO
                        val phoneDb = doc.getString("phone") ?: ""
                        currentSavedPhone = phoneDb
                        phoneNumberInput = phoneDb
                        photoUrl = doc.getString("photoUrl")
                            ?: doc.getString("photoBase64") ?: ""

                        deliveryFee = (doc.getLong("deliveryFee") ?: 50).toString()
                        hasDeliveryService = doc.getBoolean("hasDeliveryService") ?: true
                        isAcceptingCredit = doc.getBoolean("isAcceptingCredit") ?: true
                        isHiringDelivery = doc.getBoolean("isHiringDelivery") ?: false
                        isOpen = doc.getBoolean("isOpen") ?: true
                    }
                    isLoading = false
                }

            // 2. Contar Solicitudes de Empleo Pendientes
            db.collection("delivery_applications")
                .whereEqualTo("colmadoId", colmadoId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snap, _ ->
                    pendingApplicationsCount = snap?.size() ?: 0
                }
        }
    }

    // --- PROCESAMIENTO DE IMAGEN ---
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            newPhotoUri = it
            Toast.makeText(context, "Imagen seleccionada", Toast.LENGTH_SHORT).show()
        }
    }

    // --- GUARDAR CAMBIOS ---
    fun saveSettings() {
        if (colmadoName.isEmpty() || address.isEmpty()) {
            Toast.makeText(context, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isPhoneVerified && phoneNumberInput != currentSavedPhone) {
            Toast.makeText(context, "Verifica el telefono nuevo", Toast.LENGTH_LONG).show()
            return
        }

        isSaving = true

        scope.launch {
                        try {

                            val updates = hashMapOf<String, Any>(

                                "name" to colmadoName,

                                "email" to email,

                                "address" to address,

                                "province" to province,
                    "municipality" to municipality,
                    "phone" to phoneNumberInput,
                    "deliveryFee" to (deliveryFee.toIntOrNull() ?: 0),
                    "hasDeliveryService" to hasDeliveryService,
                    "isAcceptingCredit" to isAcceptingCredit,
                    "isHiringDelivery" to isHiringDelivery,
                    "isOpen" to isOpen
                )

                // Subir nueva foto si se selecciono
                if (newPhotoUri != null && colmadoId != null) {
                    val uploadedUrl = ImageStorageManager.uploadImage(
                        context = context,
                        imageUri = newPhotoUri!!,
                        ownerId = colmadoId,
                        imageType = ImageStorageManager.ImageType.COLMADO_LOGO
                    )
                    if (uploadedUrl != null) {
                        updates["photoUrl"] = uploadedUrl
                    }
                }

                if (colmadoId != null) {
                    db.collection("colmados_requests").document(colmadoId)
                        .set(updates, SetOptions.merge())
                        .await()

                    isSaving = false
                    Toast.makeText(context, "Configuracion guardada", Toast.LENGTH_SHORT).show()
                    currentSavedPhone = phoneNumberInput
                    isPhoneVerified = true
                }
            } catch (e: Exception) {
                isSaving = false
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- VERIFICACIÓN SMS ---
    fun sendVerificationCode() {
        if (phoneNumberInput.length != 10) {
            Toast.makeText(context, "Número inválido", Toast.LENGTH_SHORT).show()
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, "Error: Activity no encontrada", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        isSendingSms = true

        scope.launch {
            // 1. Validar con reCAPTCHA Enterprise
            val isHuman = recaptchaService?.validateAction("update_phone_colmado") ?: true
            if (!isHuman) {
                isLoading = false
                isSendingSms = false
                Toast.makeText(context, "Error de validacion de seguridad", Toast.LENGTH_LONG).show()
                return@launch
            }

            // 2. Proceder con el SMS
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    isSendingSms = false
                    isPhoneVerified = true
                    Toast.makeText(context, "Verificado automáticamente", Toast.LENGTH_SHORT).show()
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    isSendingSms = false
                    Toast.makeText(context, "Error SMS: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vId
                    isSendingSms = false
                    showOtpDialog = true
                    Toast.makeText(context, "Código enviado", Toast.LENGTH_SHORT).show()
                }
            }

            val formattedPhone = phoneNumberInput.toFirebasePhoneNumber()
            android.util.Log.d("ColmadoSettings", "Enviando SMS a: $formattedPhone (original: $phoneNumberInput)")

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    fun verifyOtp() {
        if (otpCode.length != 6 || verificationId.isEmpty()) {
            Toast.makeText(context, "Código inválido", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                auth.signOut()
                isPhoneVerified = true
                showOtpDialog = false
                Toast.makeText(context, "¡Teléfono verificado!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Código incorrecto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- LOGOUT ---
    fun logout() {
        auth.signOut()
        sessionManager.clearSession()
        navController.navigate(AppScreens.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    // --- UI ---
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
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
            if (!isLoading) {
                ExtendedFloatingActionButton(
                    onClick = { saveSettings() },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Cambios", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 1. FOTO
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            newPhotoUri != null -> {
                                coil.compose.AsyncImage(
                                    model = newPhotoUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            photoUrl.isNotEmpty() -> {
                                com.domifiao.domifiao.ui.components.StorageImage(
                                    imageData = photoUrl,
                                    contentDescription = "Logo del colmado",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Icon(Icons.Default.AddAPhoto, null, tint = Color.Gray)
                            }
                        }
                    }
                    Text(
                        "Toca para cambiar foto",
                        fontSize = 12.sp,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. INFORMACIÓN
                SettingsSection("Información") {
                    OutlinedTextField(
                        value = colmadoName,
                        onValueChange = { colmadoName = it },
                        label = { Text("Nombre del Colmado") },
                        leadingIcon = { Icon(Icons.Default.Store, null) },
                        modifier = Modifier.fillMaxWidth(),
                                                singleLine = true

                                            )

                        

                                            Spacer(modifier = Modifier.height(12.dp))

                        

                                            OutlinedTextField(

                                                value = email,

                                                onValueChange = { email = it },

                                                label = { Text("Correo Electrónico") },

                                                leadingIcon = { Icon(Icons.Default.Email, null) },

                                                modifier = Modifier.fillMaxWidth(),

                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),

                                                singleLine = true

                                            )

                        

                                            Spacer(modifier = Modifier.height(12.dp))

                        

                                            OutlinedTextField(

                                                value = address,
                        onValueChange = { address = it },
                        label = { Text("Dirección") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // NUEVO: Campo de Municipio
                        OutlinedTextField(
                            value = municipality,
                            onValueChange = { municipality = it },
                            label = { Text("Municipio") },
                            leadingIcon = { Icon(Icons.Default.LocationCity, null) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        // NUEVO: Campo de Provincia
                        OutlinedTextField(
                            value = province,
                            onValueChange = { province = it },
                            label = { Text("Provincia") },
                            leadingIcon = { Icon(Icons.Default.Map, null) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = phoneNumberInput,
                            onValueChange = {
                                if (it.length <= 12 && it.all { c -> c.isDigit() }) {
                                    phoneNumberInput = it
                                    isPhoneVerified = it == currentSavedPhone
                                }
                            },
                            label = { Text("Teléfono") },
                            leadingIcon = { Icon(Icons.Default.Phone, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isPhoneVerified || phoneNumberInput == currentSavedPhone) {
                                    Color(0xFF4CAF50)
                                } else {
                                    Color.Red
                                }
                            )
                        )
                        if (phoneNumberInput != currentSavedPhone && !isPhoneVerified) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { sendVerificationCode() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                enabled = !isSendingSms
                            ) {
                                if (isSendingSms) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Text("Verificar", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. OPERACIONES
                SettingsSection("Operaciones") {
                    SettingsSwitchRow(
                        title = if (isOpen) "Colmado ABIERTO" else "Colmado CERRADO",
                        subtitle = if (isOpen) "Visible para clientes" else "No recibirás pedidos",
                        icon = Icons.Default.Store,
                        checked = isOpen,
                        onCheckedChange = { isOpen = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color.LightGray.copy(alpha = 0.3f)
                    )

                    SettingsSwitchRow(
                        title = "Servicio a Domicilio",
                        subtitle = "Activar delivery",
                        icon = Icons.Default.Moped,
                        checked = hasDeliveryService,
                        onCheckedChange = { hasDeliveryService = it }
                    )

                    if (hasDeliveryService) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = deliveryFee,
                            onValueChange = { if (it.all { char -> char.isDigit() }) deliveryFee = it },
                            label = { Text("Costo de Envío (RD$)") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. PREFERENCIAS
                SettingsSection("Preferencias") {
                    SettingsSwitchRow(
                        title = "Aceptar Fiado",
                        subtitle = "Permitir crédito",
                        icon = Icons.Default.CreditScore,
                        checked = isAcceptingCredit,
                        onCheckedChange = { isAcceptingCredit = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color.LightGray.copy(alpha = 0.3f)
                    )

                    SettingsSwitchRow(
                        title = "Buscando Personal",
                        subtitle = "Habilitar vacante de Delivery",
                        icon = Icons.Default.PersonAdd,
                        checked = isHiringDelivery,
                        onCheckedChange = { isHiringDelivery = it }
                    )

                    if (isHiringDelivery) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { navController.navigate(AppScreens.ColmadoStaff.route) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Icon(Icons.Default.Badge, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver Solicitantes ($pendingApplicationsCount)")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. INTERFAZ
                SettingsSection("Interfaz") {
                    SettingsSwitchRow(
                        title = "Modo Oscuro",
                        subtitle = "Cambiar apariencia visual",
                        icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        checked = isDarkMode,
                        onCheckedChange = { onToggleDarkMode() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. PROGRAMA DE PUNTOS
                SettingsSection("Programa de Lealtad") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        onClick = { navController.navigate(AppScreens.ColmadoLoyaltySettings.route) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Programa de Puntos",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Fideliza a tus clientes",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón de logout
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar Sesión")
                }

                // Espacio extra para el FAB
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Diálogo de confirmación para cerrar sesión
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.Red) },
            title = { Text("Cerrar Sesión") },
            text = { Text("¿Estás seguro de que quieres cerrar sesión?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Sí, Cerrar Sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIÁLOGO OTP
    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { showOtpDialog = false },
            title = { Text("Código de Verificación") },
            text = {
                Column {
                    Text("Ingresa el código SMS enviado:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                otpCode = it
                            }
                        },
                        label = { Text("Código") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { verifyOtp() },
                    enabled = otpCode.length == 6,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOtpDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// --- FUNCIONES AUXILIARES ---

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = if (checked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF4CAF50),
                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
            )
        )
    }
}