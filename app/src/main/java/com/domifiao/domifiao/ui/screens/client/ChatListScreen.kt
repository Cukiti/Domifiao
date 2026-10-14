package com.domifiao.domifiao.ui.screens.client

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.navigation.AppScreens
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

data class ClientConversationState(
    val id: String,
    val colmadoId: String,
    val colmadoName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Boolean,
    val hasCredit: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val currentUserId = sessionManager.getUserId()
    val lifecycleOwner = LocalLifecycleOwner.current

    var rawConversations by remember { mutableStateOf<List<ClientConversationState>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var colmadoNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Lista reactiva y ordenada
    val displayConversations by remember {
        derivedStateOf {
            rawConversations.map { chat ->
                val realName = colmadoNames[chat.colmadoId]
                chat.copy(colmadoName = realName ?: chat.colmadoName)
            }.sortedByDescending { it.timestamp }
        }
    }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    // Listener de chats en tiempo real
    DisposableEffect(currentUserId) {
        var listener: ListenerRegistration? = null
        if (currentUserId != null) {
            listener = db.collection("chats")
                .whereArrayContains("participants", currentUserId)
                .addSnapshotListener { snapshot, e ->
                    if (snapshot != null) {
                        rawConversations = snapshot.documents.mapNotNull { doc ->
                            try {
                                val participants = doc.get("participants") as? List<*> ?: emptyList<Any>()
                                val colmadoId = participants.find { it != currentUserId }?.toString() ?: ""
                                
                                val timeObj = doc.get("timestamp")
                                val time = when (timeObj) {
                                    is Long -> timeObj
                                    is Double -> timeObj.toLong()
                                    else -> 0L
                                }

                                ClientConversationState(
                                    id = doc.id,
                                    colmadoId = colmadoId,
                                    colmadoName = doc.getString("colmadoName") ?: "Colmado",
                                    lastMessage = doc.getString("lastMessage") ?: "",
                                    timestamp = time,
                                    unreadCount = doc.getString("lastSenderId") != currentUserId
                                )
                            } catch (ex: Exception) {
                                null
                            }
                        }
                    }
                    isLoading = false
                }
        }
        onDispose { listener?.remove() }
    }

    // Cargar nombres de colmados
    LaunchedEffect(rawConversations) {
        rawConversations.forEach { chat ->
            if (chat.colmadoId.isNotEmpty() && !colmadoNames.containsKey(chat.colmadoId)) {
                db.collection("colmados_requests").document(chat.colmadoId).get().addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val name = doc.getString("name") ?: "Colmado"
                        colmadoNames = colmadoNames + (chat.colmadoId to name)
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mis Mensajes", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else if (displayConversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No tienes chats activos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    items(displayConversations, key = { it.id }) { chat ->
                        ClientChatItem(chat) {
                            navController.navigate(AppScreens.Chat.createRoute(chat.colmadoId, "COLMADO"))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ClientChatItem(chat: ClientConversationState, onClick: () -> Unit) {
    val dateString = if (chat.timestamp > 0) SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault()).format(Date(chat.timestamp)) else ""

    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f)), Alignment.Center) {
            Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(chat.colmadoName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateString, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(chat.lastMessage, fontSize = 14.sp, color = if(chat.unreadCount) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if(chat.unreadCount) FontWeight.Bold else FontWeight.Normal)
        }
        if (chat.unreadCount) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}