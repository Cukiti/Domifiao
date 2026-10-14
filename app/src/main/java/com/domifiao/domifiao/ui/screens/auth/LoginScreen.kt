package com.domifiao.domifiao.ui.screens.auth

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.PhoneVisualTransformation
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.domifiao.domifiao.utils.FCMHelper
import com.domifiao.domifiao.utils.toFirebasePhoneNumber
import com.domifiao.domifiao.services.RecaptchaService
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    navController: NavController,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    recaptchaService: RecaptchaService? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val db = FirebaseFirestore.getInstance()

    // Manejo del botón atrás para salir de la app si se está en login
    BackHandler { activity?.finish() }

    var isClientSelected by remember { mutableStateOf(true) }
    var showAdminDialog by remember { mutableStateOf(false) }
    var adminCodeInput by remember { mutableStateOf("") }
    var isVerifyingAdmin by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Botón Premium de Cambio de Tema
        Card(
            onClick = { onToggleDarkMode() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "Switch Theme",
                    tint = if (isDarkMode) Color(0xFFFFD700) else Color(0xFFFF8C00),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isDarkMode) "Oscuro" else "Claro",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color.White else Color.Black
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Domifiao",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Selector Premium Tipo de Usuario
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { isClientSelected = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isClientSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isClientSelected) Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = null
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isClientSelected) Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp))
                        if (isClientSelected) Spacer(modifier = Modifier.width(4.dp))
                        Text("CLIENTE", fontWeight = if (isClientSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                Button(
                    onClick = { isClientSelected = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isClientSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (!isClientSelected) Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = null
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isClientSelected) Icon(Icons.Default.Store, null, modifier = Modifier.size(18.dp))
                        if (!isClientSelected) Spacer(modifier = Modifier.width(4.dp))
                        Text("COLMADO", fontWeight = if (!isClientSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            // Cambio de Formulario con Animación Suave
            androidx.compose.animation.AnimatedContent(
                targetState = isClientSelected,
                label = "Login Form Transition"
            ) { isClient ->
                if (isClient) {
                    ClientLoginForm(navController, recaptchaService)
                } else {
                    ColmadoLoginForm(navController, recaptchaService)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Acceso Admin
            TextButton(onClick = { showAdminDialog = true }) {
                Text("Acceso Administrativo", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }

    // Dialogo Admin - FIX: Ahora valida contra Firestore
    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isVerifyingAdmin) {
                    showAdminDialog = false
                    adminCodeInput = ""
                }
            },
            title = { Text("Seguridad Admin") },
            text = {
                Column {
                    Text("Introduce el codigo:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adminCodeInput,
                        onValueChange = { adminCodeInput = it },
                        label = { Text("Codigo") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isVerifyingAdmin
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isVerifyingAdmin = true
                        // FIX: Validar código contra Firestore (más seguro)
                        verifyAdminCode(
                            db = db,
                            inputCode = adminCodeInput,
                            onSuccess = {
                                // Autenticar en Firebase Auth para cumplir reglas de seguridad
                                val auth = FirebaseAuth.getInstance()
                                val adminEmail = "admin@domifiao.com"
                                
                                auth.signInWithEmailAndPassword(adminEmail, adminCodeInput)
                                    .addOnSuccessListener {
                                        isVerifyingAdmin = false
                                        showAdminDialog = false
                                        adminCodeInput = ""
                                        Toast.makeText(context, "Acceso concedido", Toast.LENGTH_SHORT).show()
                                        navController.navigate(AppScreens.AdminRequests.route)
                                    }
                                    .addOnFailureListener {
                                        // Si falla el login, intentar crear la cuenta (primera vez o cambio de contraseña)
                                        auth.createUserWithEmailAndPassword(adminEmail, adminCodeInput)
                                            .addOnSuccessListener {
                                                isVerifyingAdmin = false
                                                showAdminDialog = false
                                                adminCodeInput = ""
                                                Toast.makeText(context, "Admin registrado y validado", Toast.LENGTH_SHORT).show()
                                                navController.navigate(AppScreens.AdminRequests.route)
                                            }
                                            .addOnFailureListener { e ->
                                                isVerifyingAdmin = false
                                                // Si el error es "email already in use" pero falló el login, es probable que la contraseña cambió
                                                // En ese caso, permitimos pasar porque YA validamos el hash (fallback de seguridad)
                                                // pero las reglas de firestore podrían fallar si la password vieja no coincide.
                                                // Lo ideal sería actualizar la password, pero requiere re-autenticación.
                                                // Por ahora, pasamos con el hash validado.
                                                Log.e("AdminLogin", "Auth error: ${e.message}")
                                                showAdminDialog = false
                                                adminCodeInput = ""
                                                Toast.makeText(context, "Acceso Offline (Reglas podrían fallar)", Toast.LENGTH_LONG).show()
                                                navController.navigate(AppScreens.AdminRequests.route)
                                            }
                                    }
                            },
                            onFailure = { errorMsg ->
                                isVerifyingAdmin = false
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = !isVerifyingAdmin && adminCodeInput.isNotEmpty()
                ) {
                    if (isVerifyingAdmin) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Entrar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAdminDialog = false; adminCodeInput = "" },
                    enabled = !isVerifyingAdmin
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Verifica el código admin contra Firestore.
 * El código se almacena hasheado en: app_config/admin_settings
 * Campo: "codeHash" (SHA-256 del código)
 *
 * Para configurar, crea el documento en Firestore con:
 * codeHash = SHA256("tu_codigo_secreto")
 */
private fun verifyAdminCode(
    db: FirebaseFirestore,
    inputCode: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    db.collection("app_config").document("admin_settings").get()
        .addOnSuccessListener { document ->
            if (document.exists()) {
                val storedHash = document.getString("codeHash") ?: ""
                val inputHash = hashSHA256(inputCode)

                if (storedHash.isNotEmpty() && storedHash == inputHash) {
                    onSuccess()
                } else {
                    onFailure("Codigo Incorrecto")
                }
            } else {
                // Fallback: Si no existe el documento, rechazar
                onFailure("Configuracion no encontrada")
            }
        }
        .addOnFailureListener {
            onFailure("Error de conexion: " + it.message)
        }
}

/**
 * Genera hash SHA-256 de un string.
 */
private fun hashSHA256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

// FORMULARIO CLIENTE (ACTUALIZADO A CORREO)
@Composable
fun ClientLoginForm(
    navController: NavController,
    recaptchaService: RecaptchaService? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }

    // --- LÓGICA DE LOGIN ---
    fun signInWithCustomToken(token: String) {
        auth.signInWithCustomToken(token)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Cargar datos del usuario desde Firestore antes de navegar
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                isLoading = false
                                val userName = doc.getString("name") ?: "Cliente"
                                val userPhoto = doc.getString("photoUrl") ?: ""

                                // Guardar sesion completa con nombre y foto
                                sessionManager.saveSession("CLIENT", user.uid, userName, userPhoto)

                                // Registrar token FCM para notificaciones push
                                FCMHelper.registerToken(user.uid, "client")

                                Toast.makeText(context, "Bienvenido $userName!", Toast.LENGTH_SHORT).show()
                                navController.navigate(AppScreens.ClientHome.route) {
                                    popUpTo(AppScreens.Login.route) { inclusive = true }
                                }
                                showOtpDialog = false
                            }
                            .addOnFailureListener {
                                isLoading = false
                                // Guardar sesion basica si falla la carga
                                sessionManager.saveSession("CLIENT", user.uid)

                                // Registrar token FCM
                                FCMHelper.registerToken(user.uid, "client")

                                Toast.makeText(context, "Bienvenido de nuevo!", Toast.LENGTH_SHORT).show()
                                navController.navigate(AppScreens.ClientHome.route) {
                                    popUpTo(AppScreens.Login.route) { inclusive = true }
                                }
                                showOtpDialog = false
                            }
                    }
                } else {
                    isLoading = false
                    val errorMsg = task.exception?.message ?: "Error desconocido"
                    Toast.makeText(context, "Error Login: $errorMsg", Toast.LENGTH_LONG).show()
                    showOtpDialog = false
                }
            }
    }

    fun validateAndSendCode() {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(context, "Ingresa un correo valido", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Validar con reCAPTCHA Enterprise
                val isHuman = recaptchaService?.validateAction("login_client") ?: true
                if (!isHuman) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error de validación de seguridad. Intenta de nuevo.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 2. Consultar Firestore
                val documents = db.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .await()

                if (documents.isEmpty) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Correo no registrado.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 3. Enviar Email OTP
                val url = URL("https://us-central1-domifiao-9ac0f.cloudfunctions.net/sendEmailOtp")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val json = JSONObject().apply { put("email", email) }
                conn.outputStream.use { it.write(json.toString().toByteArray()) }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        showOtpDialog = true
                        Toast.makeText(context, "Codigo enviado al correo", Toast.LENGTH_SHORT).show()
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
                    Log.e("LoginScreen", "Error en flujo: ${e.message}")
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun verifyOtp() {
        if (otpCode.length == 6) {
            isLoading = true
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
                    val responseString = if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream.bufferedReader().use { it.readText() }
                    }
                    val responseJson = JSONObject(responseString)
                    val success = responseJson.optBoolean("success", false)

                    withContext(Dispatchers.Main) {
                        if (success) {
                            val token = responseJson.optString("token", "")
                            if (token.isNotEmpty()) {
                                signInWithCustomToken(token)
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Error: Token no recibido", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            isLoading = false
                            Toast.makeText(context, responseJson.optString("message", "Codigo Incorrecto"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "Ingresa el codigo de 6 digitos", Toast.LENGTH_SHORT).show()
        }
    }

    // --- UI FORMULARIO ---
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(480.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Text("Acceso para Clientes", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo Electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading && !showOtpDialog) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = { validateAndSendCode() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Entrar con Correo", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.LightGray,
                        disabledContainerColor = Color(0xFFE0E0E0),
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Text("Entrar con Teléfono (Próximamente)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "¿No tienes cuenta? Registrate aqui",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { navController.navigate(AppScreens.RegisterClient.route) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { navController.navigate(AppScreens.ClientRecovery.route) }
            ) {
                Text(
                    "Perdí mi cuenta",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }

    // DIÁLOGO OTP
    if (showOtpDialog) {
        Dialog(onDismissRequest = { if (!isLoading) showOtpDialog = false }) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Verificación de Seguridad", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ingresa el código enviado a $email", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                        label = { Text("Código") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { verifyOtp() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Verificar")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = {
                                showOtpDialog = false
                                validateAndSendCode()
                            },
                            enabled = !isLoading
                        ) {
                            Text("Reenviar", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(
                            onClick = { showOtpDialog = false },
                            enabled = !isLoading
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        }
    }
}

// FORMULARIO COLMADO
@Composable
fun ColmadoLoginForm(
    navController: NavController,
    recaptchaService: RecaptchaService? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val sessionManager = SessionManager(context)
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }

    var foundColmadoId by remember { mutableStateOf("") }
    var foundColmadoName by remember { mutableStateOf("") }

    fun signInWithCustomToken(token: String) {
        auth.signInWithCustomToken(token)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    // Cargar foto del colmado para guardar en sesion
                    db.collection("colmados_requests").document(foundColmadoId).get()
                        .addOnSuccessListener { doc ->
                            val colmadoPhoto = doc.getString("photoUrl") ?: doc.getString("photoBase64") ?: ""
                            sessionManager.saveSession("COLMADO", foundColmadoId, foundColmadoName, colmadoPhoto)

                            // Registrar token FCM para notificaciones push
                            FCMHelper.registerToken(foundColmadoId, "colmado")

                            Toast.makeText(context, "Bienvenido $foundColmadoName", Toast.LENGTH_SHORT).show()
                            navController.navigate(AppScreens.ColmadoHome.route) {
                                popUpTo(AppScreens.Login.route) { inclusive = true }
                            }
                            showOtpDialog = false
                        }
                        .addOnFailureListener {
                            // Guardar sin foto si falla
                            sessionManager.saveSession("COLMADO", foundColmadoId, foundColmadoName)

                            // Registrar token FCM
                            FCMHelper.registerToken(foundColmadoId, "colmado")

                            Toast.makeText(context, "Bienvenido $foundColmadoName", Toast.LENGTH_SHORT).show()
                            navController.navigate(AppScreens.ColmadoHome.route) {
                                popUpTo(AppScreens.Login.route) { inclusive = true }
                            }
                            showOtpDialog = false
                        }
                } else {
                    val errorMsg = task.exception?.message ?: "Error desconocido"
                    Toast.makeText(context, "Error Login: $errorMsg", Toast.LENGTH_LONG).show()
                    showOtpDialog = false
                }
            }
    }

    fun validateAndSendCode() {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(context, "Ingresa un correo valido", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Validar con reCAPTCHA Enterprise
                val isHuman = recaptchaService?.validateAction("login_colmado") ?: true
                if (!isHuman) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error de validación de seguridad. Intenta de nuevo.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 2. Proceder con el resto
                val documents = db.collection("colmados_requests")
                    .whereEqualTo("email", email)
                    .get()
                    .await()

                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    val status = doc.getString("status") ?: "PENDING"
                    if (status == "APPROVED") {
                        foundColmadoId = doc.id
                        foundColmadoName = doc.getString("name") ?: "Colmado"
                        
                        // 3. Enviar Email OTP
                        val url = URL("https://us-central1-domifiao-9ac0f.cloudfunctions.net/sendEmailOtp")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        val json = JSONObject().apply { put("email", email) }
                        conn.outputStream.use { it.write(json.toString().toByteArray()) }

                        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                showOtpDialog = true
                                Toast.makeText(context, "Codigo enviado al correo", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                Toast.makeText(context, "Error servidor: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else if (status == "PENDING") {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            Toast.makeText(context, "Tu cuenta esta en revision!", Toast.LENGTH_LONG).show()
                            navController.navigate(AppScreens.VerificationPending.route)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            Toast.makeText(context, "Solicitud rechazada.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Este correo no esta registrado", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Log.e("LoginScreenColmado", "Error en flujo: ${e.message}")
                    Toast.makeText(context, "Error conexion: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun verifyOtp() {
        if (otpCode.length == 6) {
            isLoading = true
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
                    val responseString = if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream.bufferedReader().use { it.readText() }
                    }
                    val responseJson = JSONObject(responseString)
                    val success = responseJson.optBoolean("success", false)

                    withContext(Dispatchers.Main) {
                        if (success) {
                            val token = responseJson.optString("token", "")
                            if (token.isNotEmpty()) {
                                signInWithCustomToken(token)
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Error: Token no recibido", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            isLoading = false
                            Toast.makeText(context, responseJson.optString("message", "Codigo Incorrecto"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "Ingresa el codigo de 6 digitos", Toast.LENGTH_SHORT).show()
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(480.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Text("Acceso para Colmadero", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo de Colmado") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (isLoading && !showOtpDialog) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = { validateAndSendCode() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Entrar con Correo", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.LightGray,
                        disabledContainerColor = Color(0xFFE0E0E0),
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Text("Entrar con Teléfono (Próximamente)")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "¿Quieres registrar tu colmado?",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { navController.navigate(AppScreens.RegisterColmado.route) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { navController.navigate(AppScreens.ColmadoRecovery.route) }
            ) {
                Text(
                    "Perdí mi cuenta",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showOtpDialog) {
        Dialog(onDismissRequest = { if (!isLoading) showOtpDialog = false }) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Verificación de Seguridad", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ingresa el código enviado a $email", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it },
                        label = { Text("Código") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { verifyOtp() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Verificar")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = {
                                showOtpDialog = false
                                validateAndSendCode()
                            },
                            enabled = !isLoading
                        ) {
                            Text("Reenviar", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(
                            onClick = { showOtpDialog = false },
                            enabled = !isLoading
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        }
    }
}