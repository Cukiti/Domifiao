package com.domifiao.domifiao.ui.screens.colmado

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar

// Modelo local
data class ColmadoNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val targetId: String,
    val timestamp: Long,
    val read: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoNotificationsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifications by remember { mutableStateOf<List<ColmadoNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Navegacion segura
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }

    // Funcion para marcar todo como leido al salir
    fun markAllAsRead() {
        if (colmadoId == null) return
        val batch = db.batch()
        notifications.filter { !it.read }.forEach { notif ->
            val ref = db.collection("colmados_requests").document(colmadoId).collection("notifications").document(notif.id)
            batch.update(ref, "read", true)
        }
        batch.commit()
    }

    DisposableEffect(Unit) {
        onDispose { markAllAsRead() }
    }

    // Carga en tiempo real
    LaunchedEffect(colmadoId) {
        if (colmadoId != null) {
            db.collection("colmados_requests").document(colmadoId).collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        notifications = snapshot.documents.map { doc ->
                            ColmadoNotification(
                                id = doc.id,
                                title = doc.getString("title") ?: "Notificación",
                                message = doc.getString("message") ?: "",
                                type = doc.getString("type") ?: "INFO",
                                targetId = doc.getString("targetId") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                read = doc.getBoolean("read") ?: false
                            )
                        }
                        isLoading = false
                    }
                }
        }
    }

    // Agrupación por Fechas (Igual que en Cliente)
    val groupedNotifications = remember(notifications) {
        notifications.groupBy {
            val now = Calendar.getInstance()
            val notifTime = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            when {
                now.get(Calendar.DAY_OF_YEAR) == notifTime.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == notifTime.get(Calendar.YEAR) -> "Hoy"
                now.get(Calendar.DAY_OF_YEAR) - 1 == notifTime.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == notifTime.get(Calendar.YEAR) -> "Ayer"
                else -> "Anterior"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertas Colmado") },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { markAllAsRead() }) {
                        Icon(Icons.Default.DoneAll, "Leído", tint = Color(0xFF4CAF50))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4CAF50)) }
            } else if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsOff, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Sin notificaciones", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupedNotifications.forEach { (header, items) ->
                        item {
                            Text(header, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(4.dp))
                        }
                        items(items) { notif ->
                            ColmadoNotificationCard(notif) {
                                // Navegación Inteligente para el Colmado
                                if (notif.targetId.isNotEmpty()) {
                                    when (notif.type) {
                                        "NEW_ORDER", "ORDER_UPDATE", "SUCCESS" -> navController.navigate(AppScreens.ColmadoOrderDetail.createRoute(notif.targetId))
                                        "MESSAGE", "DEBT_CHAT" -> navController.navigate(AppScreens.ColmadoChatList.route)
                                        "CREDIT_REQUEST" -> navController.navigate(AppScreens.ColmadoCreditRequests.route)
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColmadoNotificationCard(notif: ColmadoNotification, onClick: () -> Unit) {
    // Configuración visual según tipo
    val (icon, color, bg) = when (notif.type) {
        "NEW_ORDER" -> Triple(Icons.Default.AddShoppingCart, Color(0xFF4CAF50), Color(0xFFE8F5E9)) // Verde
        "CREDIT_REQUEST" -> Triple(Icons.Default.PersonAdd, Color(0xFFFF9800), Color(0xFFFFF3E0)) // Naranja
        "MESSAGE", "DEBT_CHAT" -> Triple(Icons.AutoMirrored.Filled.Chat, Color(0xFF2196F3), Color(0xFFE3F2FD)) // Azul
        "ORDER_UPDATE" -> Triple(Icons.Default.Refresh, Color(0xFF9C27B0), Color(0xFFF3E5F5)) // Morado
        else -> Triple(Icons.Default.Info, Color.Gray, Color(0xFFEEEEEE))
    }

    val timeAgo = DateUtils.getRelativeTimeSpanString(notif.timestamp).toString()

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if(notif.read) {
                MaterialTheme.colorScheme.surface
            } else {
                if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFF0F9F4)
            }
        ),
        elevation = CardDefaults.cardElevation(if(notif.read) 1.dp else 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if(androidx.compose.foundation.isSystemInDarkTheme()) bg.copy(alpha = 0.2f) else bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(
                        notif.title, 
                        fontWeight = if(!notif.read) FontWeight.Bold else FontWeight.SemiBold, 
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if(!notif.read) Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    notif.message, 
                    fontSize = 13.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(timeAgo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
    }