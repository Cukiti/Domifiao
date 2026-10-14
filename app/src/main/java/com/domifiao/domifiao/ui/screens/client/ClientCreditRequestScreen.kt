package com.domifiao.domifiao.ui.screens.client

import android.app.DatePickerDialog
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.model.CreditRequest
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientCreditRequestScreen(
    navController: NavController,
    colmadoId: String,
    colmadoName: String
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estados de Datos (Solo Lectura)
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var isClientVerified by remember { mutableStateOf(false) }

    // Estado de Fecha (Condición de Pago)
    var paymentDeadline by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { if (!isSending) navigateBackSecurely() }

    // --- CARGAR DATOS DEL PERFIL ---
    LaunchedEffect(userId) {
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        clientName = document.getString("name") ?: ""
                        clientPhone = document.getString("phone") ?: ""
                        clientAddress = document.getString("address") ?: ""
                        isClientVerified = document.getBoolean("isVerified") ?: false
                    }
                    isLoading = false
                }
                .addOnFailureListener {
                    isLoading = false
                    Toast.makeText(context, "Error al cargar perfil", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // --- SELECCIONAR FECHA (CALENDARIO) ---
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            // Formato DD/MM/AAAA
            paymentDeadline = "$dayOfMonth/${month + 1}/$year"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    // No permitir fechas pasadas
    datePickerDialog.datePicker.minDate = System.currentTimeMillis()

    // --- ENVIAR SOLICITUD ---
    fun sendRequest() {
        // Validaciones estrictas
        if (clientPhone.isEmpty() || clientAddress.isEmpty()) {
            Toast.makeText(context, "Faltan datos en tu perfil. Ve a 'Editar Perfil' antes de solicitar.", Toast.LENGTH_LONG).show()
            return
        }
        if (paymentDeadline.isEmpty()) {
            Toast.makeText(context, "Debes seleccionar una fecha limite de pago", Toast.LENGTH_SHORT).show()
            return
        }

        isSending = true
        val newRequestRef = db.collection("credit_requests").document()

        val request = CreditRequest(
            id = newRequestRef.id,
            clientId = userId ?: "",
            clientName = clientName,
            clientAddress = clientAddress,
            clientPhone = clientPhone,
            colmadoId = colmadoId,
            colmadoName = colmadoName,
            requestDate = System.currentTimeMillis(),
            paymentDeadline = paymentDeadline,
            status = "PENDING",
            approvedLimit = 0,
            isClientVerified = isClientVerified
        )

        newRequestRef.set(request)
            .addOnSuccessListener {
                val notif = hashMapOf(
                    "title" to "Nueva Solicitud de Fiado",
                    "message" to "$clientName solicita crédito. Promete pagar el $paymentDeadline.",
                    "type" to "CREDIT_REQUEST",
                    "targetId" to newRequestRef.id,
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false
                )
                db.collection("colmados_requests").document(colmadoId).collection("notifications").add(notif)

                Toast.makeText(context, "Solicitud enviada exitosamente", Toast.LENGTH_LONG).show()
                navigateBackSecurely()
            }
            .addOnFailureListener {
                isSending = false
                Toast.makeText(context, "Error al enviar: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Solicitar Fiado") },
                navigationIcon = {
                    IconButton(onClick = { if (!isSending) navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Colmado
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Store, null, tint = Color(0xFF4CAF50))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Solicitud para:", fontSize = 12.sp, color = Color.Gray)
                                Text(colmadoName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Header Datos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tus Datos (Verificados)", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { navController.navigate(AppScreens.ClientEditProfile.route) }) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar Perfil", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Formulario de Datos (BLOQUEADO / READ ONLY)
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Nombre
                            OutlinedTextField(
                                value = clientName,
                                onValueChange = {},
                                label = { Text("Nombre Completo") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                trailingIcon = { Icon(Icons.Default.Lock, null, tint = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true, // NO EDITABLE
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Gray,
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedTextColor = Color.DarkGray,
                                    unfocusedTextColor = Color.DarkGray
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Teléfono
                            OutlinedTextField(
                                value = clientPhone,
                                onValueChange = {},
                                label = { Text("Teléfono Registrado") },
                                leadingIcon = { Icon(Icons.Default.Phone, null) },
                                trailingIcon = { Icon(Icons.Default.Lock, null, tint = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true, // NO EDITABLE
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Gray,
                                    unfocusedBorderColor = Color.LightGray
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Dirección
                            OutlinedTextField(
                                value = clientAddress,
                                onValueChange = {},
                                label = { Text("Dirección de Entrega") },
                                leadingIcon = { Icon(Icons.Default.Place, null) },
                                trailingIcon = { Icon(Icons.Default.Lock, null, tint = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true, // NO EDITABLE
                                maxLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Gray,
                                    unfocusedBorderColor = Color.LightGray
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Compromiso de Pago", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    // SELECCIÓN DE FECHA (CALENDARIO)
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Usamos un Box invisible sobre el TextField para detectar el click
                            Box {
                                OutlinedTextField(
                                    value = paymentDeadline,
                                    onValueChange = { },
                                    label = { Text("Fecha Límite de Pago") },
                                    placeholder = { Text("Selecciona una fecha") },
                                    leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF4CAF50)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = true, // El usuario no escribe, selecciona
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF4CAF50),
                                        focusedLabelColor = Color(0xFF4CAF50)
                                    )
                                )
                                // Capa transparente clickable
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (!isSending) datePickerDialog.show()
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Al enviar esta solicitud, te comprometes a saldar tu deuda antes de la fecha seleccionada.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Botón Enviar
                    Button(
                        onClick = { sendRequest() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.CreditScore, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ENVIAR SOLICITUD", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}