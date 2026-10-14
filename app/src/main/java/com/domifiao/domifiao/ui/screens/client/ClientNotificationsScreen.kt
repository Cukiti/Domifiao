package com.domifiao.domifiao.ui.screens.client

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class ClientNotification(
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
fun ClientNotificationsScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val userId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifications by remember { mutableStateOf<List<ClientNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    LaunchedEffect(userId) {
        if (userId != null) {
            db.collection("users").document(userId).collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        notifications = snapshot.documents.map { doc ->
                            ClientNotification(
                                id = doc.id,
                                title = doc.getString("title") ?: "Notificación",
                                message = doc.getString("message") ?: "",
                                type = doc.getString("type") ?: "INFO",
                                targetId = doc.getString("targetId") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                read = doc.getBoolean("read") ?: false
                            )
                        }
                    }
                    isLoading = false
                }
        }
    }

    fun markAsRead(notifId: String) {
        if (userId == null) return
        db.collection("users").document(userId).collection("notifications").document(notifId).update("read", true)
    }

    fun handleNotificationClick(notif: ClientNotification) {
        when(notif.type) {
            "NEW_ORDER", "ORDER_UPDATE" -> navController.navigate(AppScreens.ClientOrderDetail.createRoute(notif.targetId))
            "DEBT_UPDATE" -> navController.navigate(AppScreens.ClientMyCredits.route)
            // Agregaremos más tipos según sea necesario
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsNone, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No tienes notificaciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notifications) { notif ->
                        NotificationCard(notif) { markAsRead(notif.id); handleNotificationClick(notif) }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(notif: ClientNotification, onClick: () -> Unit) {
    val (icon, color, bg) = when(notif.type) {
        "ORDER_UPDATE" -> Triple(Icons.Default.ShoppingBag, Color(0xFF2196F3), Color(0xFFE3F2FD))
        "DEBT_UPDATE" -> Triple(Icons.Default.CreditCard, Color(0xFFFF9800), Color(0xFFFFF3E0))
        "PROMO" -> Triple(Icons.Default.Star, Color(0xFF4CAF50), Color(0xFFE8F5E9))
        else -> Triple(Icons.Default.Notifications, Color.Gray, Color(0xFFF5F5F5))
    }

    val timeAgo = DateUtils.getRelativeTimeSpanString(notif.timestamp).toString()

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if(notif.read) {
                MaterialTheme.colorScheme.surface
            } else {
                if (isSystemInDarkTheme()) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFF0F9F4)
            }
        ),
        elevation = CardDefaults.cardElevation(if(notif.read) 1.dp else 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if(isSystemInDarkTheme()) bg.copy(alpha = 0.2f) else bg), contentAlignment = Alignment.Center) {
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
