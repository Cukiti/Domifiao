package com.domifiao.domifiao.ui.screens.colmado

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import com.domifiao.domifiao.data.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Modelo local para la UI
data class DeliveryStaffUI(
    val id: String, // ID de la solicitud
    val userId: String, // ID del usuario
    val name: String,
    val phone: String,
    val status: String,
    val vehicleType: String = "Delivery"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoStaffScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()

    // Seguridad de ciclo de vida
    val lifecycleOwner = LocalLifecycleOwner.current

    var activeStaff by remember { mutableStateOf<List<DeliveryStaffUI>>(emptyList()) }
    var pendingRequests by remember { mutableStateOf<List<DeliveryStaffUI>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Estado para tabs (0 = Activos, 1 = Solicitudes)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialogo Confirmación
    var showFireDialog by remember { mutableStateOf(false) }
    var staffToFire by remember { mutableStateOf<DeliveryStaffUI?>(null) }

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    // --- CARGA SEGURA DE DATOS ---
    DisposableEffect(colmadoId) {
        var listener: ListenerRegistration? = null

        if (colmadoId != null) {
            listener = db.collection("delivery_applications")
                .whereEqualTo("colmadoId", colmadoId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        isLoading = false
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val allApps = snapshot.documents.mapNotNull { doc ->
                            try {
                                DeliveryStaffUI(
                                    id = doc.id,
                                    userId = doc.getString("userId") ?: "",
                                    name = doc.getString("userName") ?: "Delivery",
                                    phone = doc.getString("userPhone") ?: "",
                                    status = doc.getString("status") ?: "PENDING",
                                    vehicleType = doc.getString("vehicleType") ?: "Motor"
                                )
                            } catch (e: Exception) { null }
                        }

                        // Filtramos en memoria
                        activeStaff = allApps.filter { it.status == "APPROVED" }
                        pendingRequests = allApps.filter { it.status == "PENDING" }
                        isLoading = false
                    }
                }
        } else {
            isLoading = false
        }

        onDispose {
            listener?.remove()
        }
    }

    // --- ACCIONES (CORREGIDAS: ACTUALIZAN PERFIL DE USUARIO) ---

    fun updateStatus(staff: DeliveryStaffUI, newStatus: String) {
        if (colmadoId == null) return

        val batch = db.batch()

        // 1. Actualizar estado en la solicitud
        val appRef = db.collection("delivery_applications").document(staff.id)
        batch.update(appRef, "status", newStatus)

        // 2. Actualizar perfil del usuario (Permisos)
        val userRef = db.collection("users").document(staff.userId)

        if (newStatus == "APPROVED") {
            // Contratado
            batch.update(userRef, mapOf(
                "isDelivery" to true,
                "workingAtColmadoId" to colmadoId
            ))
        } else if (newStatus == "REJECTED") {
            // Rechazado (No cambiamos su perfil, solo la solicitud)
        }

        batch.commit().addOnSuccessListener {
            val msg = if(newStatus == "APPROVED") "Delivery contratado" else "Solicitud rechazada"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

            // Notificar al usuario
            val notif = hashMapOf(
                "title" to if(newStatus=="APPROVED") "¡Contratado!" else "Solicitud Rechazada",
                "message" to if(newStatus=="APPROVED") "Ya puedes acceder al panel de Delivery." else "El colmado ha rechazado tu solicitud.",
                "type" to "INFO",
                "targetId" to colmadoId,
                "timestamp" to System.currentTimeMillis(),
                "read" to false
            )
            db.collection("users").document(staff.userId).collection("notifications").add(notif)
        }
    }

    fun fireDelivery() {
        val staff = staffToFire ?: return
        val batch = db.batch()

        // 1. Marcar solicitud como terminada/rechazada
        val appRef = db.collection("delivery_applications").document(staff.id)
        batch.update(appRef, "status", "FIRED")

        // 2. Quitar permisos al usuario (Campo a null)
        val userRef = db.collection("users").document(staff.userId)
        batch.update(userRef, mapOf(
            "isDelivery" to false,
            "workingAtColmadoId" to null
        ))

        batch.commit().addOnSuccessListener {
            Toast.makeText(context, "Delivery eliminado del equipo", Toast.LENGTH_SHORT).show()
            showFireDialog = false
            staffToFire = null
        }
    }

    fun callDelivery(phone: String) {
        if (phone.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Sin número de teléfono", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Equipo de Delivery", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // TABS
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Activos (${activeStaff.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Solicitudes", fontWeight = FontWeight.Bold)
                            if(pendingRequests.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(modifier = Modifier.background(Color.Red, CircleShape).size(8.dp))
                            }
                        }
                    }
                )
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val listToShow = if (selectedTab == 0) activeStaff else pendingRequests

                if (listToShow.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Moped, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if(selectedTab == 0) "No tienes deliveries activos" else "No hay solicitudes nuevas",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(listToShow) { staff ->
                            StaffCard(
                                staff = staff,
                                isRequest = selectedTab == 1,
                                onAccept = { updateStatus(staff, "APPROVED") },
                                onReject = { updateStatus(staff, "REJECTED") },
                                onFire = {
                                    staffToFire = staff
                                    showFireDialog = true
                                },
                                onCall = { callDelivery(staff.phone) }
                            )
                        }
                    }
                }
            }
        }
    }
    }

    @Composable
    fun StaffCard(
    staff: DeliveryStaffUI,
    isRequest: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onFire: () -> Unit,
    onCall: () -> Unit
    ) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = Color(0xFF4CAF50))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(staff.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(staff.vehicleType, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!isRequest) {
                    IconButton(onClick = onCall) {
                        Icon(Icons.Default.Phone, null, tint = Color(0xFF2196F3))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isRequest) {
                Row {
                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF4E342E) else Color(0xFFFFEBEE), 
                            contentColor = Color.Red
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rechazar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aceptar", color = Color.White)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onFire,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Eliminar del Equipo")
                }
            }
        }
    }
    }