@file:Suppress("DEPRECATION", "LABEL_NAME_CLASH")

package com.domifiao.domifiao.ui.screens.auth

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import androidx.compose.ui.window.PopupProperties
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.CedulaVisualTransformation
import com.domifiao.domifiao.ui.components.PhoneVisualTransformation
import com.domifiao.domifiao.ui.components.dominicanProvinces
import com.domifiao.domifiao.utils.IdentityVerifier
import com.domifiao.domifiao.utils.ImageStorageManager
import com.domifiao.domifiao.utils.toFirebasePhoneNumber
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.domifiao.domifiao.services.RecaptchaService
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit


@Composable
fun RegisterColmadoScreen(
    navController: NavController,
    recaptchaService: RecaptchaService? = null
) {
    // 4 PASOS: Datos (+Dueno) -> Mapa -> Foto Local -> Foto Cedula Dueno (IA)
    var step by rememberSaveable { mutableIntStateOf(1) }
    val totalSteps = 4
    val context = LocalContext.current
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLoading by remember { mutableStateOf(false) }

    // Navegacion segura para volver al login
    fun navigateToLoginSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.navigate(AppScreens.Login.route) {
                popUpTo(AppScreens.Login.route) { inclusive = true }
            }
        }
    }

    // --- NAVEGACION SEGURA ---
    BackHandler(enabled = true) {
        if (step > 1) {
            step -= 1
        } else {
            navigateToLoginSecurely()
        }
    }

    // -- Datos del Negocio & Dueno --
    var colmadoName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var ownerName by rememberSaveable { mutableStateOf("") }
    var ownerCedula by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    // -- Verificacion --
    var isEmailMode by rememberSaveable { mutableStateOf(true) }
    var emailVerified by rememberSaveable { mutableStateOf(false) }
    var isPhoneVerified by rememberSaveable { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var otpCode by remember { mutableStateOf("") }
    var showOtpDialog by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    // -- Ubicacion --
    var address by rememberSaveable { mutableStateOf("") }
    var province by rememberSaveable { mutableStateOf("") }
    var municipality by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableDoubleStateOf(0.0) }
    var longitude by rememberSaveable { mutableDoubleStateOf(0.0) }

    // -- Fotos --
    var colmadoPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var ownerIdPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // -- Estados IA --
    var aiVerificationStatus by remember { mutableStateOf<String?>(null) }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var isIdentityValid by remember { mutableStateOf(false) }

    // --- FUNCIONES GPS ---
    fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Obteniendo ubicación...", Toast.LENGTH_SHORT).show()
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        latitude = location.latitude
                        longitude = location.longitude

                        scope.launch {
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val detectedAddress = addresses[0]
                                    // Lógica de dirección mejorada
                                    val street = detectedAddress.thoroughfare
                                    val number = detectedAddress.subThoroughfare
                                    val feature = detectedAddress.featureName

                                    val addrText = when {
                                        !street.isNullOrEmpty() && !number.isNullOrEmpty() -> "$street #$number"
                                        !street.isNullOrEmpty() -> street
                                        !feature.isNullOrEmpty() && feature != detectedAddress.subAdminArea && feature != detectedAddress.locality -> feature
                                        else -> {
                                            val line = detectedAddress.getAddressLine(0) ?: ""
                                            val firstPart = line.split(",").firstOrNull()?.trim() ?: ""
                                            if (firstPart.contains("+") && firstPart.length < 15) "" else firstPart
                                        }
                                    }
                                    val adminArea = detectedAddress.adminArea ?: detectedAddress.subAdminArea
                                    // NUEVO: Detectar municipio
                                    val detectedMunicipality = detectedAddress.locality ?: detectedAddress.subAdminArea ?: ""

                                    withContext(Dispatchers.Main) {
                                        address = addrText
                                        municipality = detectedMunicipality
                                        if (adminArea != null) {
                                            // ACTUALIZADO: Usar .keys.find
                                            val match = dominicanProvinces.keys.find { adminArea.contains(it, ignoreCase = true) }
                                            province = match ?: adminArea
                                        }
                                        Toast.makeText(context, "Ubicación detectada: $municipality, $province", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Coordenadas guardadas", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Enciende el GPS e intenta de nuevo", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) getCurrentLocation()
    }

    // --- LOGICA SMS ---
    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {

                    // IMPORTANTE: Iniciar sesión automáticamente para que auth.currentUser no sea null

                    auth.signInWithCredential(credential).addOnCompleteListener { task ->

                        isLoading = false

                        if (task.isSuccessful) {

                            isPhoneVerified = true

                            showOtpDialog = false

                            Toast.makeText(context, "Verificado automaticamente", Toast.LENGTH_SHORT).show()

                        } else {

                            Toast.makeText(context, "Error auto-login: ${task.exception?.message}", Toast.LENGTH_LONG).show()

                        }

                    }

                }
        override fun onVerificationFailed(e: FirebaseException) {
            isLoading = false
            Toast.makeText(context, "Error SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
        override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = vId
            resendToken = token  // Guardar token para reenvío
            isLoading = false
            showOtpDialog = true
            Toast.makeText(context, "Codigo enviado", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendVerificationCode() {
        if (phone.length != 10) {
            Toast.makeText(context, "Ingresa un numero valido (10 digitos)", Toast.LENGTH_SHORT).show()
            return
        }

        // Si ya hay un código enviado y el diálogo está cerrado, mostrar el diálogo
        if (verificationId.isNotEmpty() && !showOtpDialog) {
            showOtpDialog = true
            Toast.makeText(context, "Ingresa el codigo enviado anteriormente", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        // Verificar si ya existe el teléfono antes de enviar SMS
        scope.launch {
            // Timeout de seguridad: si en 15 segundos no ha pasado nada, liberar isLoading
            val timeoutJob = launch {
                kotlinx.coroutines.delay(15000)
                if (isLoading) {
                    isLoading = false
                    android.util.Log.w("RegisterColmado", "Timeout de seguridad alcanzado enviando SMS")
                }
            }

            // 1. Validar con reCAPTCHA Enterprise
            val isHuman = recaptchaService?.validateAction("register_colmado") ?: true
            if (!isHuman) {
                isLoading = false
                timeoutJob.cancel()
                Toast.makeText(context, "Error de validacion de seguridad", Toast.LENGTH_LONG).show()
                return@launch
            }

            // 2. Proceder con el resto
            db.collection("colmados_requests").whereEqualTo("phone", phone).get()
                .addOnSuccessListener { phoneDocs ->
                    var canProceed = true
                    if (!phoneDocs.isEmpty) {
                        val existingDoc = phoneDocs.documents[0]
                        val status = existingDoc.getString("status")
                        if (status != "REJECTED") {
                            canProceed = false
                            isLoading = false
                            timeoutJob.cancel()
                            Toast.makeText(context, "Este teléfono ya tiene una solicitud ($status)", Toast.LENGTH_LONG).show()
                        }
                    }

                    if (canProceed) {
                        val formattedPhone = phone.toFirebasePhoneNumber()
                        android.util.Log.d("RegisterColmado", "Enviando SMS a: $formattedPhone (original: $phone)")

                        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(formattedPhone)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(activity)
                            .setCallbacks(callbacks)

                        // Si tenemos token de reenvío, usarlo
                        if (resendToken != null) {
                            optionsBuilder.setForceResendingToken(resendToken!!)
                        }

                        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
                    }
                }
                .addOnFailureListener { e ->
                    isLoading = false
                    timeoutJob.cancel()
                    Toast.makeText(context, "Error conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun sendEmailOtp(emailStr: String) {
        if (emailStr.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()) {
            Toast.makeText(context, "Ingresa un correo válido", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                // Validar reCAPTCHA antes de enviar correo
                val isHuman = recaptchaService?.validateAction("send_email_otp_colmado") ?: true
                if (!isHuman) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error de validación de seguridad", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val url = URL("https://us-central1-domifiao-9ac0f.cloudfunctions.net/sendEmailOtp")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = JSONObject().apply {
                    put("email", emailStr)
                }

                conn.outputStream.use { it.write(json.toString().toByteArray()) }

                val responseCode = conn.responseCode
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        showOtpDialog = true
                        Toast.makeText(context, "Código enviado al correo", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Error al enviar correo: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun verifyEmailOtpLocal() {
        if (otpCode.length != 6) return
        
        isVerifyingCode = true
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://us-central1-domifiao-9ac0f.cloudfunctions.net/verifyEmailOtp")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = JSONObject().apply {
                    put("email", email)
                    put("otp", otpCode)
                }

                conn.outputStream.use { it.write(json.toString().toByteArray()) }

                val responseCode = conn.responseCode
                val responseString = if (responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream.bufferedReader().use { it.readText() }
                }

                val responseJson = JSONObject(responseString)
                val success = responseJson.optBoolean("success", false)

                withContext(Dispatchers.Main) {
                    isVerifyingCode = false
                    if (success) {
                        emailVerified = true
                        showOtpDialog = false
                        // Para colmados, la verificación exitosa permite avanzar
                        Toast.makeText(context, "Correo verificado!", Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = responseJson.optString("message", "Código incorrecto")
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isVerifyingCode = false
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun verifyOtpCode() {
        if (otpCode.isNotEmpty() && verificationId.isNotEmpty()) {
            isVerifyingCode = true
            val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                isVerifyingCode = false
                if (task.isSuccessful) {
                    // NO hacer signOut - necesitamos estar autenticados para las consultas
                    isPhoneVerified = true
                    showOtpDialog = false
                    Toast.makeText(context, "Telefono Verificado!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Codigo incorrecto", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Ingresa el codigo", Toast.LENGTH_SHORT).show()
        }
    }

    // --- GUARDADO FINAL ---
    fun submitRegistration() {
        android.util.Log.d("RegisterColmado", "submitRegistration llamado")

        if (colmadoPhotoUri == null || ownerIdPhotoUri == null) {
            Toast.makeText(context, "Faltan las fotos (Local o Cedula)", Toast.LENGTH_SHORT).show()
            return
        }

        // Verificar que el usuario está autenticado
        val currentUser = auth.currentUser
        android.util.Log.d("RegisterColmado", "Usuario actual: ${currentUser?.uid ?: "NULL"}")

        if (currentUser == null) {
            Toast.makeText(context, "Error: Debes verificar tu telefono primero", Toast.LENGTH_LONG).show()
            return
        }

        isLoading = true

        // 1. Verificar SOLO si ya existe un COLMADO con este telefono
        android.util.Log.d("RegisterColmado", "Buscando telefono: $phone")
        db.collection("colmados_requests").whereEqualTo("phone", phone).get()
            .addOnSuccessListener { phoneDocs ->

                if (!phoneDocs.isEmpty) {
                    val existingDoc = phoneDocs.documents[0]
                    val status = existingDoc.getString("status")

                    if (status == "REJECTED") {
                        val reason = existingDoc.getString("rejectionReason") ?: "Sin motivo especificado"
                        isLoading = false
                        Toast.makeText(
                            context,
                            "Solicitud anterior RECHAZADA: $reason. Puedes enviar una nueva.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Eliminamos la vieja para "limpiar" y permitir reintento
                        existingDoc.reference.delete().addOnSuccessListener {
                            submitRegistration() // Reintentar guardar como nueva
                        }
                        return@addOnSuccessListener
                    } else {
                        isLoading = false
                        Toast.makeText(
                            context,
                            "Ya existe una solicitud pendiente o aprobada con este teléfono.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addOnSuccessListener
                    }
                }

                // 2. Verificar SOLO si ya existe un COLMADO con esta cédula
                db.collection("colmados_requests").whereEqualTo("ownerCedula", ownerCedula).get()
                    .addOnSuccessListener { cedulaDocs ->
                        if (!cedulaDocs.isEmpty) {
                            isLoading = false
                            Toast.makeText(
                                context,
                                "Esta cédula ya tiene un colmado registrado.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }

                        // 3. Proceder al registro con Firebase Storage
                        scope.launch {
                            try {
                                // Crear documento primero para obtener ID
                                                                val colmadoData = hashMapOf(
                                                                    "name" to colmadoName,
                                                                    "ownerName" to ownerName,
                                                                    "ownerCedula" to ownerCedula,
                                                                    "ownerId" to currentUser.uid, // Vincular documento con Auth UID
                                                                    "phone" to phone,
                                                                    "isPhoneVerified" to true,
                                                                    "address" to address,
                                    "province" to province,
                                    "municipality" to municipality,
                                    "latitude" to latitude,
                                    "longitude" to longitude,
                                    "photoUrl" to "",
                                    "ownerIdPhotoUrl" to "",
                                    "status" to "PENDING",
                                    "role" to "COLMADO",
                                    "rating" to 0.0,
                                    "reviewCount" to 0,
                                    "totalStars" to 0,
                                    "isOpen" to true,
                                    "isHiringDelivery" to false,
                                    "isAcceptingCredit" to true,
                                    "isIdentityVerified" to isIdentityValid,
                                    "createdAt" to com.google.firebase.Timestamp.now()
                                )

                                val docRef = db.collection("colmados_requests").add(colmadoData).await()
                                val colmadoId = docRef.id
                                android.util.Log.d("RegisterColmado", "Documento creado con ID: $colmadoId")

                                // Subir imagenes a Firebase Storage
                                var logoUrl = ""
                                var cedulaUrl = ""

                                if (colmadoPhotoUri != null) {
                                    logoUrl = ImageStorageManager.uploadImage(
                                        context = context,
                                        imageUri = colmadoPhotoUri!!,
                                        ownerId = colmadoId,
                                        imageType = ImageStorageManager.ImageType.COLMADO_LOGO
                                    ) ?: ""
                                }

                                if (ownerIdPhotoUri != null) {
                                    cedulaUrl = ImageStorageManager.uploadImage(
                                        context = context,
                                        imageUri = ownerIdPhotoUri!!,
                                        ownerId = colmadoId,
                                        imageType = ImageStorageManager.ImageType.COLMADO_CEDULA
                                    ) ?: ""
                                }

                                // Actualizar documento con URLs
                                if (logoUrl.isNotEmpty() || cedulaUrl.isNotEmpty()) {
                                    val updates = mutableMapOf<String, Any>()
                                    if (logoUrl.isNotEmpty()) updates["photoUrl"] = logoUrl
                                    if (cedulaUrl.isNotEmpty()) updates["ownerIdPhotoUrl"] = cedulaUrl
                                    docRef.update(updates).await()
                                }

                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    navController.navigate(AppScreens.VerificationPending.route) {
                                        popUpTo(AppScreens.Login.route) { inclusive = true }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }.addOnFailureListener { e ->
                        android.util.Log.e("RegisterColmado", "Error verificando cedula: ${e.message}")
                        isLoading = false
                        Toast.makeText(context, "Error verificando cedula: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }.addOnFailureListener { e ->
                android.util.Log.e("RegisterColmado", "Error verificando telefono: ${e.message}")
                isLoading = false
                Toast.makeText(context, "Error verificando telefono: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { if(!isVerifyingCode) showOtpDialog = false },
            title = { Text(if(isEmailMode) "Verificar Correo" else "Verificar Teléfono") },
            text = {
                Column {
                    Text("Código enviado a ${if(isEmailMode) email else phone}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if(it.length<=6) otpCode=it },
                        label = { Text("Código de 6 dígitos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { if(isEmailMode) verifyEmailOtpLocal() else verifyOtpCode() }) {
                    if(isVerifyingCode) {
                        CircularProgressIndicator(
                            modifier=Modifier.size(20.dp),
                            color=Color.White
                        )
                    } else {
                        Text("Verificar")
                    }
                }
            },
            dismissButton = {
                if(!isVerifyingCode) {
                    Row {
                        TextButton(
                            onClick = {
                                if (isEmailMode) {
                                    sendEmailOtp(email)
                                } else {
                                    verificationId = ""
                                    resendToken = null
                                    showOtpDialog = false
                                    sendVerificationCode()
                                }
                            }
                        ) {
                            Text("Reenviar", color = Color(0xFF4CAF50))
                        }
                        TextButton(onClick = { showOtpDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    if (step > 1) {
                        step -= 1
                    } else {
                        navController.popBackStack(AppScreens.Login.route, inclusive = false)
                    }
                }
            ) {
                Icon(Icons.Default.ArrowBack, "Atrás")
            }
            Text(
                "Registro de Colmado",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = step.toFloat() / totalSteps.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (step) {
            1 -> ColmadoDataStep(
                colmadoName, { colmadoName=it },
                ownerName, { ownerName=it },
                ownerCedula, { if(it.length<=11 && it.all{ c->c.isDigit() }) ownerCedula=it },
                phone, { if(it.length<=10 && it.all{ c->c.isDigit() }) phone=it },
                email, { email=it },
                isEmailMode, { isEmailMode=it },
                isLoading,
                isPhoneVerified || emailVerified, 
                { if(isEmailMode) sendEmailOtp(email) else sendVerificationCode() },
                    onNext = {
                        if(colmadoName.isNotEmpty() && ownerName.isNotEmpty() &&
                            IdentityVerifier.isCedulaValid(ownerCedula)) {
                            if (isPhoneVerified || emailVerified) {
                                step = 2
                            } else {
                                val msg = if(isEmailMode) "Verifica tu correo" else "Verifica tu teléfono"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Cédula inválida o datos incompletos",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                2 -> ColmadoMapStep(
                    address, { address=it },
                    province, { province=it },
                    municipality, { municipality=it }, // NUEVO
                    {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    {
                        if(province.isNotEmpty() && municipality.isNotEmpty()) {
                            step=3
                        } else {
                            Toast.makeText(
                                context,
                                "Ubicación completa necesaria (provincia y municipio)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    { lat, lng, muni ->
                        latitude=lat
                        longitude=lng
                        if (muni.isNotEmpty()) municipality = muni
                    }
                )
                3 -> ColmadoFachadaStep(
                    photoUri = colmadoPhotoUri,
                    onPhotoSelected = { uri -> colmadoPhotoUri = uri },
                    onNext = { step = 4 }
                )
                4 -> ColmadoOwnerIdStep(
                    ownerIdPhotoUri,
                    { uri ->
                        ownerIdPhotoUri = uri
                        if(uri!=null) {
                            isAiAnalyzing = true
                            scope.launch {
                                val result = IdentityVerifier.verifyImageContent(
                                    context,
                                    uri,
                                    ownerCedula,
                                    ownerName
                                )
                                isAiAnalyzing = false
                                if(result is IdentityVerifier.VerificationResult.Verified ||
                                    result is IdentityVerifier.VerificationResult.Partial) {
                                    aiVerificationStatus="¡Verificación Exitosa!"
                                    isIdentityValid=true
                                } else {
                                    aiVerificationStatus="Verificación Fallida"
                                    isIdentityValid=false
                                }
                            }
                        }
                    },
                    isAiAnalyzing,
                    aiVerificationStatus,
                    { submitRegistration() }
                )
        }
    }
}

// --- SUB-COMPONENTES ---

@Composable
fun ColmadoDataStep(
    colmadoName: String,
    onColmadoNameChange: (String)->Unit,
    ownerName: String,
    onOwnerNameChange: (String)->Unit,
    ownerCedula: String,
    onOwnerCedulaChange: (String)->Unit,
    phone: String,
    onPhoneChange: (String)->Unit,
    email: String,
    onEmailChange: (String)->Unit,
    isEmailMode: Boolean,
    onModeChange: (Boolean)->Unit,
    isLoading: Boolean,
    isVerified: Boolean,
    onVerify: ()->Unit,
    onNext: ()->Unit
) {
    Text("Datos del Negocio", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = colmadoName,
        onValueChange = onColmadoNameChange,
        label = { Text("Nombre del Colmado") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text("Datos del Propietario", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = ownerName,
        onValueChange = onOwnerNameChange,
        label = { Text("Nombre del Dueño") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = ownerCedula,
        onValueChange = onOwnerCedulaChange,
        label = { Text("Cédula del Dueño") },
        visualTransformation = CedulaVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text("Verificación de Contacto", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))

    // Selector de Modo (Pestañas)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray.copy(alpha = 0.3f))
            .padding(4.dp)
    ) {
        Button(
            onClick = { },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.Gray,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.Gray
            ),
            shape = RoundedCornerShape(6.dp),
            elevation = null,
            enabled = false
        ) {
            Text("SMS (Próximamente)", fontSize = 12.sp)
        }
        Button(
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isEmailMode) Color(0xFF4CAF50) else Color.Transparent,
                contentColor = if (isEmailMode) Color.White else Color.Gray
            ),
            shape = RoundedCornerShape(6.dp),
            elevation = null
        ) {
            Text("Correo", fontSize = 14.sp)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (isEmailMode) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Correo Electrónico") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f),
                enabled = !isVerified
            )
            if (isVerified) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = { Text("Teléfono") },
                visualTransformation = PhoneVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
                enabled = !isVerified
            )
            if (isVerified) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
            }
        }
    }

    if (!isVerified) {
        Spacer(modifier = Modifier.height(12.dp))
        val isEnabled = if (isEmailMode) {
            android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        } else {
            phone.length == 10
        }
        Button(
            onClick = onVerify,
            enabled = isEnabled && !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text(if (isEmailMode) "Enviar Código al Correo" else "Enviar Código SMS")
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
    ) {
        Text("Siguiente")
    }
}

@Composable
fun ColmadoMapStep(
    address: String,
    onAddressChange: (String)->Unit,
    province: String,
    onProvinceChange: (String)->Unit,
    municipality: String,
    onMunicipalityChange: (String)->Unit,
    onGetLocation: ()->Unit,
    onNext: ()->Unit,
    onLocationUpdate: (Double, Double, String)->Unit
) {
    val context = LocalContext.current
    var expandedProvince by remember { mutableStateOf(false) }
    var expandedMunicipality by remember { mutableStateOf(false) }
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val scope = rememberCoroutineScope()
    var showAddressDropdown by remember { mutableStateOf(false) }
    var addressSuggestions by remember { mutableStateOf<List<Address>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Obtener municipios de la provincia seleccionada
    val availableMunicipalities = remember(province) {
        if (province.isNotEmpty()) {
            dominicanProvinces[province] ?: emptyList()
        } else {
            emptyList()
        }
    }

    Text("Ubicacion del Colmado", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        "Selecciona tu provincia y municipio para que los clientes de tu zona puedan encontrarte.",
        fontSize = 14.sp,
        color = Color.Gray
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 1. BOTON GPS
    Button(
        onClick = onGetLocation,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
    ) {
        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Detectar Ubicacion con GPS")
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text("O ingresa manualmente:", fontSize = 14.sp, color = Color.Gray)

    Spacer(modifier = Modifier.height(12.dp))

    // 2. SELECTOR DE PROVINCIA (PRIMERO)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = province,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provincia *") },
            trailingIcon = {
                IconButton(onClick = { expandedProvince = !expandedProvince }) {
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedProvince = true }
        )
        DropdownMenu(
            expanded = expandedProvince,
            onDismissRequest = { expandedProvince = false },
            modifier = Modifier.height(300.dp)
        ) {
            dominicanProvinces.keys.toList().sorted().forEach { prov ->
                DropdownMenuItem(
                    text = { Text(text = prov) },
                    onClick = {
                        onProvinceChange(prov)
                        onMunicipalityChange("") // Limpiar municipio al cambiar provincia
                        expandedProvince = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 3. SELECTOR DE MUNICIPIO (SEGUNDO - depende de provincia)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = municipality,
            onValueChange = {},
            readOnly = true,
            label = { Text("Municipio *") },
            enabled = province.isNotEmpty(),
            trailingIcon = {
                if (province.isNotEmpty()) {
                    IconButton(onClick = { expandedMunicipality = !expandedMunicipality }) {
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = province.isNotEmpty()) { expandedMunicipality = true }
        )
        DropdownMenu(
            expanded = expandedMunicipality,
            onDismissRequest = { expandedMunicipality = false },
            modifier = Modifier.height(300.dp)
        ) {
            availableMunicipalities.sorted().forEach { muni ->
                DropdownMenuItem(
                    text = { Text(text = muni) },
                    onClick = {
                        onMunicipalityChange(muni)
                        expandedMunicipality = false
                    }
                )
            }
        }
    }

    if (province.isEmpty()) {
        Text(
            "Selecciona una provincia primero",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 4. CAMPO DE DIRECCION CON AUTOCOMPLETADO (TERCERO)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = address,
            onValueChange = { query ->
                onAddressChange(query)
                if (query.length > 3 && province.isNotEmpty()) {
                    isSearching = true
                    scope.launch {
                        try {
                            // Buscar en la provincia seleccionada
                            val searchQuery = "$query, $municipality, $province, Republica Dominicana"
                            @Suppress("DEPRECATION")
                            val results = geocoder.getFromLocationName(searchQuery, 5)
                            withContext(Dispatchers.Main) {
                                addressSuggestions = results ?: emptyList()
                                showAddressDropdown = addressSuggestions.isNotEmpty()
                                isSearching = false
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                isSearching = false
                            }
                        }
                    }
                } else {
                    showAddressDropdown = false
                }
            },
            label = { Text("Direccion / Calle") },
            placeholder = { Text("Ej: Calle Principal #25") },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = municipality.isNotEmpty()
        )

        DropdownMenu(
            expanded = showAddressDropdown,
            onDismissRequest = { showAddressDropdown = false },
            properties = PopupProperties(focusable = false)
        ) {
            addressSuggestions.forEach { suggestion ->
                val addressLine = suggestion.getAddressLine(0) ?: ""
                DropdownMenuItem(
                    text = {
                        Text(
                            addressLine,
                            maxLines = 2,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onAddressChange(addressLine)
                        val detectedMuni = suggestion.locality ?: suggestion.subAdminArea ?: municipality
                        onLocationUpdate(suggestion.latitude, suggestion.longitude, detectedMuni)
                        showAddressDropdown = false
                    }
                )
            }
        }
    }

    if (municipality.isEmpty()) {
        Text(
            "Selecciona un municipio primero",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Mostrar resumen de ubicacion
    if (province.isNotEmpty() && municipality.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column {
                Text("Ubicacion seleccionada:", fontSize = 12.sp, color = Color.Gray)
                Text(
                    "$municipality, $province",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E7D32)
                )
                if (address.isNotEmpty()) {
                    Text(address, fontSize = 14.sp, color = Color.DarkGray)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
        enabled = province.isNotEmpty() && municipality.isNotEmpty()
    ) {
        Text("Siguiente")
    }
}

@Composable
fun ColmadoFachadaStep(
    photoUri: Uri?,
    onPhotoSelected: (Uri?) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var tempPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Launcher de Cámara con manejo correcto de URI
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val uri = tempPhotoUri
            if (success && uri != null) {
                onPhotoSelected(uri)
            }
        }
    )

        // Función para crear archivo y lanzar cámara


        val launchCamera = {


            try {


                val imagesDir = File(context.cacheDir, "images")


                if (!imagesDir.exists()) imagesDir.mkdirs()


                val photoFile = File.createTempFile("IMG_LOCAL_", ".jpg", imagesDir)


                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)


                tempPhotoUri = uri


                cameraLauncher.launch(uri)


            } catch (e: Exception) {


                Toast.makeText(context, "Error cámara: ${e.message}", Toast.LENGTH_SHORT).show()


            }


        }

    // Permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Permiso requerido", Toast.LENGTH_LONG).show()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Foto del Negocio", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(16.dp))

        // Área Clickeable para Foto
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray)
                .clickable {
                    val permissionCheckResult = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    )
                    if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                        launchCamera()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Storefront,
                        null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Tocar para tomar foto", color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNext,
            enabled = photoUri != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Siguiente")
        }
    }
}

@Composable
fun ColmadoOwnerIdStep(
    photoUri: Uri?,
    onPhotoSelected: (Uri?) -> Unit,
    isAnalyzing: Boolean,
    verificationStatus: String?,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var tempPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val uri = tempPhotoUri
            if (success && uri != null) {
                onPhotoSelected(uri)
            }
        }
    )

        val launchCamera = {


            try {


                val imagesDir = File(context.cacheDir, "images")


                if (!imagesDir.exists()) imagesDir.mkdirs()


                val photoFile = File.createTempFile("ID_", ".jpg", imagesDir)


                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)


                tempPhotoUri = uri


                cameraLauncher.launch(uri)


            } catch (e: Exception) {


                Toast.makeText(context, "Error cámara: ${e.message}", Toast.LENGTH_SHORT).show()


            }


        }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Permiso requerido", Toast.LENGTH_LONG).show()
        }
    }

    Text("Verificar Identidad", fontSize = 18.sp)
    Spacer(modifier = Modifier.height(16.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray)
            .clickable {
                val permissionCheckResult = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                )
                if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                    launchCamera()
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if(photoUri!=null) {
            AsyncImage(
                model=photoUri,
                contentDescription=null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if(isAnalyzing) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Badge,
                    null,
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Text("Tocar para foto Cédula", color = Color.Gray)
            }
        }
    }

    if(verificationStatus!=null) {
        val textColor = if (verificationStatus == "¡Verificación Exitosa!") Color(0xFF4CAF50) else Color.Red
        Text(
            text = verificationStatus, 
            modifier = Modifier.padding(top = 8.dp),
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onFinish,
        enabled = photoUri != null && !isAnalyzing && verificationStatus != "Verificación Fallida",
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text("Enviar Solicitud")
    }
}