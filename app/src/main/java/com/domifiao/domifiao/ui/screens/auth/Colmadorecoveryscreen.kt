package com.domifiao.domifiao.ui.screens.auth

import android.app.Activity
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.PhoneVisualTransformation
import com.domifiao.domifiao.utils.IdentityVerifier
import com.domifiao.domifiao.utils.toFirebasePhoneNumber
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.domifiao.domifiao.services.RecaptchaService
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoRecoveryScreen(
    navController: NavController,
    recaptchaService: RecaptchaService? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Estados - 4 pasos ahora
    var step by remember { mutableIntStateOf(1) } // 1: Buscar, 2: Foto Cedula, 3: Nuevo Telefono, 4: Confirmar
    var isLoading by remember { mutableStateOf(false) }

    // Paso 1: Buscar colmado
    var searchQuery by remember { mutableStateOf("") }
    var searchType by remember { mutableStateOf("cedula") }
    var foundColmado by remember { mutableStateOf<Map<String, Any>?>(null) }
    var foundColmadoId by remember { mutableStateOf("") }
    var ownerCedula by remember { mutableStateOf("") }

    // Paso 2: Verificar cedula con foto
    var cedulaPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var isVerifyingCedula by remember { mutableStateOf(false) }
    var cedulaVerified by remember { mutableStateOf(false) }
    var verificationMessage by remember { mutableStateOf("") }

    // Paso 3: Nuevo telefono
    var newPhone by rememberSaveable { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var showOtpDialog by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }

    // Paso 4: Confirmacion
    var isUpdating by remember { mutableStateOf(false) }

    // Launcher para tomar foto
    var tempPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            cedulaPhotoUri = tempPhotoUri
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { cedulaPhotoUri = it }
    }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }

    fun createTempPhotoUri(): Uri {
        val imagesDir = File(context.cacheDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val photoFile = File.createTempFile("cedula_recovery_", ".jpg", imagesDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    // Buscar colmado en Firestore
    fun searchColmado() {
        if (searchQuery.isEmpty()) {
            Toast.makeText(context, "Ingresa un valor para buscar", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        val field = when (searchType) {
            "cedula" -> "ownerCedula"
            else -> "name"
        }

        db.collection("colmados_requests")
            .whereEqualTo(field, searchQuery)
            .whereEqualTo("status", "APPROVED")
            .get()
            .addOnSuccessListener { documents ->
                isLoading = false
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    foundColmadoId = doc.id
                    foundColmado = doc.data
                    ownerCedula = doc.getString("ownerCedula") ?: ""
                    step = 2
                    Toast.makeText(context, "Colmado encontrado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No se encontro ningun colmado con esos datos", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                isLoading = false
                Toast.makeText(context, "Error de conexion: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Verificar cedula con IA
    fun verifyCedulaPhoto() {
        val uri = cedulaPhotoUri
        if (uri == null) {
            Toast.makeText(context, "Toma una foto de tu cedula primero", Toast.LENGTH_SHORT).show()
            return
        }

        if (ownerCedula.isEmpty()) {
            Toast.makeText(context, "Error: No se encontro la cedula del dueno registrada", Toast.LENGTH_LONG).show()
            return
        }

        isVerifyingCedula = true

        scope.launch {
            try {
                // Convertir imagen a base64
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val imageBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                // Verificar con IA
                val result = IdentityVerifier.verifyCedulaNumber(imageBase64, ownerCedula)

                isVerifyingCedula = false

                if (result.isValid && result.cedulaMatch) {
                    cedulaVerified = true
                    verificationMessage = "Cedula verificada correctamente"
                    Toast.makeText(context, "Identidad verificada", Toast.LENGTH_SHORT).show()
                    step = 3
                } else if (!result.isValid) {
                    cedulaVerified = false
                    verificationMessage = "No se pudo leer la cedula. Toma otra foto con mejor luz."
                    Toast.makeText(context, verificationMessage, Toast.LENGTH_LONG).show()
                } else {
                    cedulaVerified = false
                    verificationMessage = "La cedula no coincide con la registrada para este colmado."
                    Toast.makeText(context, verificationMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isVerifyingCedula = false
                verificationMessage = "Error al verificar: ${e.message}"
                Toast.makeText(context, verificationMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Enviar codigo de verificacion al nuevo telefono
    fun sendVerificationCode() {
        if (newPhone.length != 10) {
            Toast.makeText(context, "Ingresa un numero de 10 digitos", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        scope.launch {
            try {
                // 1. Validar con reCAPTCHA Enterprise
                val isHuman = recaptchaService?.validateAction("colmado_recovery") ?: true
                if (!isHuman) {
                    isLoading = false
                    Toast.makeText(context, "Error de validacion de seguridad", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val formattedPhone = newPhone.toFirebasePhoneNumber()
                android.util.Log.d("ColmadoRecovery", "Enviando SMS a: $formattedPhone (original: $newPhone)")

                val phoneDocs = db.collection("colmados_requests")
                    .whereEqualTo("phone", newPhone)
                    .get()
                    .await()

                if (!phoneDocs.isEmpty && phoneDocs.documents[0].id != foundColmadoId) {
                    isLoading = false
                    Toast.makeText(context, "Este numero ya esta registrado en otro colmado", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val currentActivity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
                if (currentActivity != null) {
                    val options = PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(formattedPhone)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(currentActivity)
                        .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            override fun onVerificationCompleted(credential: PhoneAuthCredential) {}

                            override fun onVerificationFailed(e: FirebaseException) {
                                isLoading = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }

                            override fun onCodeSent(verId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                isLoading = false
                                verificationId = verId
                                showOtpDialog = true
                                Toast.makeText(context, "Codigo enviado", Toast.LENGTH_SHORT).show()
                            }
                        })
                        .build()

                    PhoneAuthProvider.verifyPhoneNumber(options)
                } else {
                    isLoading = false
                    Toast.makeText(context, "Error de sistema: Activity no encontrada", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isLoading = false
                Log.e("ColmadoRecovery", "Error en flujo: ${e.message}")
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Verificar codigo OTP
    fun verifyOtpCode() {
        if (otpCode.length != 6) {
            Toast.makeText(context, "Ingresa el codigo de 6 digitos", Toast.LENGTH_SHORT).show()
            return
        }

        isVerifyingCode = true
        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                isVerifyingCode = false
                if (task.isSuccessful) {
                    showOtpDialog = false
                    step = 4
                    Toast.makeText(context, "Telefono verificado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Codigo incorrecto", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Actualizar telefono del colmado
    fun updateColmadoPhone() {
        isUpdating = true

        db.collection("colmados_requests").document(foundColmadoId)
            .update(
                mapOf(
                    "phone" to newPhone,
                    "phoneUpdatedAt" to System.currentTimeMillis(),
                    "phoneRecoveryVerified" to true
                )
            )
            .addOnSuccessListener {
                isUpdating = false
                Toast.makeText(context, "Numero actualizado exitosamente", Toast.LENGTH_LONG).show()
                navController.navigate(AppScreens.Login.route) {
                    popUpTo(AppScreens.Login.route) { inclusive = true }
                }
            }
            .addOnFailureListener { e ->
                isUpdating = false
                Toast.makeText(context, "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuperar Acceso") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step > 1) step-- else navigateBackSecurely()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atras")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicador de progreso
            LinearProgressIndicator(
                progress = { step / 4f },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4CAF50)
            )

            Text(
                "Paso $step de 4",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                // PASO 1: Buscar Colmado
                1 -> {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Buscar tu Colmado", fontSize = 22.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Ingresa los datos de tu colmado para iniciar la recuperacion",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Buscar por:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = searchType == "cedula",
                            onClick = { searchType = "cedula"; searchQuery = "" },
                            label = { Text("Cedula Dueno") }
                        )
                        FilterChip(
                            selected = searchType == "nombre",
                            onClick = { searchType = "nombre"; searchQuery = "" },
                            label = { Text("Nombre Colmado") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = when (searchType) {
                                "cedula" -> it.filter { c -> c.isDigit() }.take(11)
                                else -> it
                            }
                        },
                        label = {
                            Text(
                                when (searchType) {
                                    "cedula" -> "Cedula del Dueno (11 digitos)"
                                    else -> "Nombre del Colmado"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                when (searchType) {
                                    "cedula" -> Icons.Default.Badge
                                    else -> Icons.Default.Store
                                },
                                null
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (searchType == "cedula") KeyboardType.Number else KeyboardType.Text
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { searchColmado() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        enabled = !isLoading && searchQuery.isNotEmpty()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Buscar Colmado", fontSize = 16.sp)
                        }
                    }
                }

                // PASO 2: Verificar Cedula con Foto
                2 -> {
                    val colmadoName = foundColmado?.get("name") as? String ?: "Colmado"
                    val ownerName = foundColmado?.get("ownerName") as? String ?: ""

                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Verificar Identidad", fontSize = 22.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Toma una foto de tu cedula para verificar que eres el dueno del colmado",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Info del colmado encontrado
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(colmadoName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (ownerName.isNotEmpty()) {
                                Text("Dueno: $ownerName", fontSize = 14.sp, color = Color.Gray)
                            }
                            Text("Cedula: ${ownerCedula.take(3)}****${ownerCedula.takeLast(2)}", fontSize = 14.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Area de foto
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF5F5F5))
                            .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cedulaPhotoUri != null) {
                            AsyncImage(
                                model = cedulaPhotoUri,
                                contentDescription = "Foto de cedula",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Badge,
                                    null,
                                    modifier = Modifier.size(60.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Foto de tu cedula", color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botones de camara y galeria
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                tempPhotoUri = createTempPhotoUri()
                                cameraLauncher.launch(tempPhotoUri!!)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Camara")
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Galeria")
                        }
                    }

                    if (verificationMessage.isNotEmpty() && !cedulaVerified) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFD32F2F))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(verificationMessage, fontSize = 14.sp, color = Color(0xFFD32F2F))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { verifyCedulaPhoto() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        enabled = !isVerifyingCedula && cedulaPhotoUri != null
                    ) {
                        if (isVerifyingCedula) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verificando...", fontSize = 16.sp)
                        } else {
                            Icon(Icons.Default.Verified, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verificar Cedula", fontSize = 16.sp)
                        }
                    }
                }

                // PASO 3: Nuevo numero de telefono
                3 -> {
                    val colmadoName = foundColmado?.get("name") as? String ?: "Colmado"

                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Nuevo Telefono", fontSize = 22.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Verificacion exitosa
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Identidad verificada", fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32))
                                Text(colmadoName, fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Ingresa tu nuevo numero de telefono. Te enviaremos un codigo para verificarlo.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) newPhone = it },
                        label = { Text("Nuevo Numero de Telefono") },
                        leadingIcon = { Text("+1", modifier = Modifier.padding(start = 12.dp)) },
                        visualTransformation = PhoneVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { sendVerificationCode() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        enabled = !isLoading && newPhone.length == 10
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Enviar Codigo", fontSize = 16.sp)
                        }
                    }
                }

                // PASO 4: Confirmacion
                4 -> {
                    val colmadoName = foundColmado?.get("name") as? String ?: "Colmado"

                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(100.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Todo Verificado",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Store, null, tint = Color(0xFF4CAF50))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(colmadoName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Badge, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cedula verificada", fontSize = 14.sp, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Nuevo: +1 $newPhone", fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Al confirmar, tu numero sera actualizado y podras iniciar sesion con el nuevo telefono.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { updateColmadoPhone() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        enabled = !isUpdating
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Confirmar y Actualizar", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialogo OTP
    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { if (!isVerifyingCode) showOtpDialog = false },
            title = { Text("Codigo de Verificacion") },
            text = {
                Column {
                    Text("Ingresa el codigo de 6 digitos enviado a +1 $newPhone")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                        label = { Text("Codigo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { verifyOtpCode() },
                    enabled = !isVerifyingCode && otpCode.length == 6
                ) {
                    if (isVerifyingCode) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Verificar")
                    }
                }
            },
            dismissButton = {
                if (!isVerifyingCode) {
                    TextButton(onClick = { showOtpDialog = false }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}