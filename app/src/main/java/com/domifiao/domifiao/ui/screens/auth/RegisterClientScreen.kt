@file:Suppress("DEPRECATION", "LABEL_NAME_CLASH")

package com.domifiao.domifiao.ui.screens.auth

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.services.RecaptchaService
import com.domifiao.domifiao.ui.components.CedulaVisualTransformation
import com.domifiao.domifiao.ui.components.PhoneVisualTransformation
import com.domifiao.domifiao.ui.components.dominicanProvinces
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.utils.IdentityVerifier
import com.domifiao.domifiao.utils.ImageStorageManager
import com.domifiao.domifiao.utils.toFirebasePhoneNumber
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterClientScreen(
    navController: NavController,
    recaptchaService: RecaptchaService? = null,
    sessionManager: SessionManager
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    val totalSteps = 5
    val context = LocalContext.current
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // -- ESTADOS DE DATOS --
    var name by rememberSaveable { mutableStateOf("") }
    var cedula by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var displayPhone by rememberSaveable { mutableStateOf("") }
    var isEmailMode by rememberSaveable { mutableStateOf(true) }
    var emailVerified by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var gender by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    
    // Ubicacion
    var address by rememberSaveable { mutableStateOf("") }
    var province by rememberSaveable { mutableStateOf("") }
    var municipality by rememberSaveable { mutableStateOf("") }
    var floorInfo by rememberSaveable { mutableStateOf("") }
    var houseDescription by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableDoubleStateOf(0.0) }
    var longitude by rememberSaveable { mutableDoubleStateOf(0.0) }
    
    // Fotos
    var housePhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var idPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Estados UI
    var isLoading by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var isIdentityValid by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    // --- FUNCIONES LOGICA ---

    fun sendEmailOtp(emailStr: String) {
        if (emailStr.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()) {
            Toast.makeText(context, "Ingresa un correo válido", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val isHuman = recaptchaService?.validateAction("send_email_otp") ?: true
                if (!isHuman) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error de seguridad", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val url = URL("https://us-central1-domifiao-9ac0f.cloudfunctions.net/sendEmailOtp")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val json = JSONObject().apply { put("email", emailStr) }
                conn.outputStream.use { it.write(json.toString().toByteArray()) }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        showOtpDialog = true
                        Toast.makeText(context, "Código enviado al correo", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error servidor: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
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
                val responseJson = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val success = responseJson.optBoolean("success", false)

                withContext(Dispatchers.Main) {
                    isVerifyingCode = false
                    if (success) {
                        emailVerified = true
                        showOtpDialog = false
                        step = 2
                    } else {
                        Toast.makeText(context, responseJson.optString("message", "Incorrecto"), Toast.LENGTH_SHORT).show()
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

    fun sendVerificationCode() {
        if (phone.length != 10) return
        isLoading = true
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential).addOnCompleteListener { 
                    isLoading = false
                    if (it.isSuccessful) { showOtpDialog = false; step = 2 } 
                }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                isLoading = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = vId
                resendToken = token
                isLoading = false
                showOtpDialog = true
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone.toFirebasePhoneNumber())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtpCode() {
        isVerifyingCode = true
        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
        auth.signInWithCredential(credential).addOnCompleteListener {
            isVerifyingCode = false
            if (it.isSuccessful) { showOtpDialog = false; step = 2 }
            else Toast.makeText(context, "Código incorrecto", Toast.LENGTH_SHORT).show()
        }
    }

    fun linkAndSaveUser() {
        isLoading = true
        scope.launch {
            try {
                var currentUid: String? = null
                if (isEmailMode && emailVerified) {
                    try {
                        currentUid = auth.createUserWithEmailAndPassword(email, password).await().user?.uid
                    } catch (e: Exception) {
                        currentUid = auth.signInWithEmailAndPassword(email, password).await().user?.uid
                    }
                } else {
                    currentUid = auth.currentUser?.uid
                }

                if (currentUid == null) throw Exception("No se pudo obtener el ID de usuario")

                // Verificar duplicados
                val cedulaExists = !db.collection("users").whereEqualTo("cedula", cedula).get().await().isEmpty
                if (cedulaExists) throw Exception("Cédula ya registrada")

                // Subir imagenes
                val houseUrl = if (housePhotoUri != null) ImageStorageManager.uploadImage(context, housePhotoUri!!, currentUid, ImageStorageManager.ImageType.USER_HOUSE) else ""
                val idUrl = if (idPhotoUri != null) ImageStorageManager.uploadImage(context, idPhotoUri!!, currentUid, ImageStorageManager.ImageType.USER_CEDULA) else ""

                val userData = hashMapOf(
                    "uid" to currentUid,
                    "name" to name,
                    "cedula" to cedula,
                    "phone" to phone,
                    "email" to email,
                    "address" to address,
                    "province" to province,
                    "municipality" to municipality,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "role" to "CLIENT",
                    "status" to "ACTIVE",
                    "housePhotoUrl" to houseUrl,
                    "idPhotoUrl" to idUrl,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )

                db.collection("users").document(currentUid).set(userData).await()
                
                withContext(Dispatchers.Main) {
                    isLoading = false
                    sessionManager.saveSession("CLIENT", currentUid)
                    navController.navigate(AppScreens.RegisterSuccess.createRoute(isIdentityValid)) {
                        popUpTo(AppScreens.Login.route) { inclusive = true }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- UI RENDERING ---

    BackHandler { if (step > 1) step-- else navController.popBackStack() }

    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { if(!isVerifyingCode) showOtpDialog = false },
            title = { Text(if(isEmailMode) "Verificar Correo" else "Verificar Teléfono") },
            text = {
                Column {
                    Text("Código enviado a ${if(isEmailMode) email else phone}")
                    OutlinedTextField(value = otpCode, onValueChange = { if(it.length<=6) otpCode=it }, label = { Text("Código") })
                }
            },
            confirmButton = {
                Button(onClick = { if(isEmailMode) verifyEmailOtpLocal() else verifyOtpCode() }) {
                    if(isVerifyingCode) CircularProgressIndicator(modifier=Modifier.size(20.dp), color=Color.White) else Text("Verificar")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (step > 1) step-- else navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
            Text("Registro Cliente", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        
        LinearProgressIndicator(progress = step.toFloat()/totalSteps, modifier = Modifier.fillMaxWidth(), color = Color(0xFF4CAF50))
        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4CAF50)) }
        } else {
            when (step) {
                1 -> StepOneContactVerification(isEmailMode, {isEmailMode=it}, phone, {phone=it; displayPhone=it}, displayPhone, email, {email=it}, { if(isEmailMode) sendEmailOtp(email) else sendVerificationCode() })
                2 -> StepTwoPersonalData(name, {name=it}, cedula, {cedula=it}, gender, {gender=it}, { if(name.length>3 && IdentityVerifier.isCedulaValid(cedula)) step=3 else Toast.makeText(context, "Datos inválidos", Toast.LENGTH_SHORT).show() })
                3 -> StepThreeLocation(address, {address=it}, province, {province=it}, municipality, {municipality=it}, { step=4 })
                4 -> StepFourSecurity(email, {email=it}, emailVerified, password, {password=it}, confirmPassword, {confirmPassword=it}, passwordVisible, {passwordVisible=!passwordVisible}, { if(password.length>=6 && password==confirmPassword) step=5 else Toast.makeText(context, "Error en contraseña", Toast.LENGTH_SHORT).show() })
                5 -> StepFivePhotos(housePhotoUri, {housePhotoUri=it}, idPhotoUri, {idPhotoUri=it}, { linkAndSaveUser() })
            }
        }
    }
}

// --- COMPONENTES DE PASOS (Versiones simplificadas para brevedad) ---

@Composable
fun StepOneContactVerification(isEmailMode: Boolean, onModeChange: (Boolean) -> Unit, phone: String, onPhoneChange: (String) -> Unit, displayPhone: String, email: String, onEmailChange: (String) -> Unit, onVerify: () -> Unit) {
    Column {
        TabRow(selectedTabIndex = if(isEmailMode) 0 else 1) {
            Tab(selected = isEmailMode, onClick = { onModeChange(true) }, text = { Text("Correo") })
            Tab(selected = !isEmailMode, onClick = { }, text = { Text("SMS (Próximamente)") }, enabled = false)
        }
        Spacer(Modifier.height(24.dp))
        if(isEmailMode) OutlinedTextField(value = email, onValueChange = onEmailChange, label = { Text("Correo") }, modifier = Modifier.fillMaxWidth())
        else OutlinedTextField(value = displayPhone, onValueChange = onPhoneChange, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = onVerify, modifier = Modifier.fillMaxWidth()) { Text("Enviar Código") }
    }
}

@Composable
fun StepTwoPersonalData(name: String, onNameChange: (String)->Unit, cedula: String, onCedulaChange: (String)->Unit, gender: String, onGenderChange: (String)->Unit, onNext: ()->Unit) {
    Column {
        OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Nombre Completo") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cedula, onValueChange = onCedulaChange, label = { Text("Cédula") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Siguiente") }
    }
}

@Composable
fun StepThreeLocation(address: String, onAddressChange: (String)->Unit, province: String, onProvinceChange: (String)->Unit, municipality: String, onMunicipalityChange: (String)->Unit, onNext: ()->Unit) {
    Column {
        OutlinedTextField(value = address, onValueChange = onAddressChange, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Siguiente") }
    }
}

@Composable
fun StepFourSecurity(email: String, onEmailChange: (String)->Unit, emailVerified: Boolean, password: String, onPasswordChange: (String)->Unit, confirmPassword: String, onConfirmChange: (String)->Unit, passwordVisible: Boolean, onToggleVisibility: ()->Unit, onNext: ()->Unit) {
    Column {
        OutlinedTextField(value = email, onValueChange = onEmailChange, label = { Text("Email") }, readOnly = emailVerified, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = onPasswordChange, label = { Text("Contraseña") }, visualTransformation = if(passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = confirmPassword, onValueChange = onConfirmChange, label = { Text("Confirmar") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Siguiente") }
    }
}

@Composable
fun StepFivePhotos(houseUri: Uri?, onHouseSelect: (Uri?)->Unit, idUri: Uri?, onIdSelect: (Uri?)->Unit, onFinish: ()->Unit) {
    Column {
        Text("Fotos finales")
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finalizar Registro") }
    }
}
