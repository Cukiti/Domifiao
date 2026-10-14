package com.domifiao.domifiao.ui.screens.colmado

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.model.ChatConversation
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoChatListScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val colmadoId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var rawConversations by remember { mutableStateOf<List<ChatConversation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Mapa para contar mensajes no leídos desde Notificaciones
    var unreadCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // Caché para nombres reales de clientes
    var clientNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // LISTA FINAL REACTIVA: Se recalcula si cambia rawConversations, unreadCounts o clientNames
    val displayConversations by remember {
        derivedStateOf {
            rawConversations.map { chat ->
                val parts = chat.id.split("_")
                val clientId = parts.find { it != colmadoId } ?: ""
                val realName = clientNames[clientId]
                val unread = unreadCounts[chat.id] ?: 0
                
                chat.copy(
                    name = if (!realName.isNullOrEmpty()) realName else chat.name,
                    unreadCount = unread
                )
            }.sortedByDescending { it.timestamp }
        }
    }

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    // Función para marcar como leídos al entrar
    fun markAsRead(targetId: String) {
        if (colmadoId == null) return
        db.collection("colmados_requests").document(colmadoId).collection("notifications")
            .whereEqualTo("targetId", targetId)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { doc -> batch.update(doc.reference, "read", true) }
                batch.commit()
            }
    }

    // 1. Escuchar Notificaciones para contar No Leídos (BADGES)
    DisposableEffect(colmadoId) {
        var notifListener: ListenerRegistration? = null
        if (colmadoId != null) {
            notifListener = db.collection("colmados_requests").document(colmadoId).collection("notifications")
                .whereEqualTo("read", false)
                .whereIn("type", listOf("MESSAGE", "DEBT_CHAT"))
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val counts = mutableMapOf<String, Int>()
                        snapshot.documents.forEach { doc ->
                            val targetId = doc.getString("targetId") ?: return@forEach
                            counts[targetId] = (counts[targetId] ?: 0) + 1
                        }
                        unreadCounts = counts
                    }
                }
        }
        onDispose { notifListener?.remove() }
    }

    // 2. Escuchar Colección de CHATS (Persistencia)
    DisposableEffect(colmadoId) {
        var chatsListener: ListenerRegistration? = null

        if (colmadoId != null) {
            chatsListener = db.collection("chats")
                .whereEqualTo("colmadoId", colmadoId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        error.printStackTrace()
                        isLoading = false
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        rawConversations = snapshot.documents.mapNotNull { doc ->
                            try {
                                val storedName = doc.getString("clientName") ?: doc.getString("colmadoName") ?: "Cliente"
                                val lastMsg = doc.getString("lastMessage") ?: ""
                                
                                val timeObj = doc.get("timestamp")
                                val time = when (timeObj) {
                                    is Long -> timeObj
                                    is Double -> timeObj.toLong()
                                    is String -> timeObj.toLongOrNull() ?: 0L
                                    else -> 0L
                                }
    
                                ChatConversation(
                                    id = doc.id,
                                    name = storedName,
                                    lastMessage = lastMsg,
                                    timestamp = time,
                                    unreadCount = 0,
                                    photoUrl = ""
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        isLoading = false
                    }
                }
        } else {
            isLoading = false
        }

        onDispose { chatsListener?.remove() }
    }

    // 3. Buscar Nombres Reales de Clientes
    LaunchedEffect(rawConversations) {
        rawConversations.forEach { chat ->
            val parts = chat.id.split("_")
            val clientId = parts.find { it != colmadoId } ?: ""

            if (clientId.isNotEmpty() && !clientNames.containsKey(clientId)) {
                db.collection("users").document(clientId).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val realName = doc.getString("name") ?: "Cliente"
                            clientNames = clientNames + (clientId to realName)
                        }
                    }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mensajes de Clientes", fontWeight = FontWeight.Bold) },
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (displayConversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bandeja de entrada vacía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    items(displayConversations, key = { it.id }) { chat ->
                        ColmadoChatItem(chat) {
                            markAsRead(chat.id)
                            val parts = chat.id.split("_")
                            val clientId = parts.find { it != colmadoId } ?: ""
                            navController.navigate(AppScreens.Chat.createRoute(clientId, "CLIENT"))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ColmadoChatItem(chat: ChatConversation, onClick: () -> Unit) {
    val dateString = if (chat.timestamp > 0) {
        SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault()).format(Date(chat.timestamp))
    } else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Cliente
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Nombre y Fecha
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = chat.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateString,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Mensaje y Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.lastMessage,
                    color = if(chat.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontWeight = if(chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                )

                if (chat.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.Red, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}