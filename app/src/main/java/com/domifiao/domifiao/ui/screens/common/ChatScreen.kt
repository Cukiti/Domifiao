package com.domifiao.domifiao.ui.screens.common

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.airbnb.lottie.compose.*
import com.domifiao.domifiao.R
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.model.ChatMessage
import com.domifiao.domifiao.ui.components.ColmadoImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.itemsIndexed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, targetId: String?, targetType: String?) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sessionManager = SessionManager(context)
    val currentUserId = sessionManager.getUserId() ?: ""
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var newMessageText by remember { mutableStateOf("") }
    var chatTitle by remember { mutableStateOf("Chat") }
    var targetPhoto by remember { mutableStateOf("") }
    var isTargetOnline by remember { mutableStateOf(false) }
    var targetLastSeen by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val conversationId = remember(targetId) {
        if (targetId == null) ""
        else {
            val ids = listOf(currentUserId, targetId).sorted()
            "${ids[0]}_${ids[1]}"
        }
    }

    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    // Marcar como leído al entrar
    LaunchedEffect(conversationId) {
        if (conversationId.isNotEmpty()) {
            val role = sessionManager.getRole()
            val collection = if (role == "COLMADO") "colmados_requests" else "users"
            val userId = sessionManager.getUserId()
            if (userId != null) {
                db.collection(collection).document(userId).collection("notifications")
                    .whereEqualTo("targetId", conversationId)
                    .whereEqualTo("read", false)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val batch = db.batch()
                        snapshot.documents.forEach { doc -> batch.update(doc.reference, "read", true) }
                        batch.commit()
                    }
            }
        }
    }

    // Auto-scroll al final cuando cargan mensajes externos
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    DisposableEffect(conversationId) {
        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        var userListener: com.google.firebase.firestore.ListenerRegistration? = null
        if (conversationId.isNotEmpty() && targetId != null) {
            val collection = if (targetType == "COLMADO") "colmados_requests" else "users"
            userListener = db.collection(collection).document(targetId)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        chatTitle = doc.getString("name") ?: "Chat"
                        targetPhoto = doc.getString("photoUrl") ?: doc.getString("photoBase64") ?: ""
                        isTargetOnline = doc.getBoolean("isOnline") ?: false
                        targetLastSeen = doc.getLong("lastSeen") ?: 0L
                    }
                }

            listener = db.collection("chats").document(conversationId).collection("messages")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        messages = snapshot.documents.mapNotNull { doc ->
                            try {
                                ChatMessage(
                                    id = doc.id,
                                    senderId = doc.getString("senderId") ?: "",
                                    senderName = doc.getString("senderName") ?: "",
                                    text = doc.getString("text") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: 0L,
                                    messageType = doc.getString("messageType") ?: "TEXT",
                                    replyToId = doc.getString("replyToId"),
                                    replyToText = doc.getString("replyToText")
                                )
                            } catch (e: Exception) { null }
                        }
                        isLoading = false
                    } else {
                        isLoading = false
                    }
                }
        } else {
            isLoading = false
        }
        onDispose {
            listener?.remove()
            userListener?.remove()
        }
    }

    fun sendMessage(customText: String? = null, messageType: String = "TEXT") {
        val textToSend = (customText ?: newMessageText).trim()
        if (textToSend.isBlank() && messageType == "TEXT" || conversationId.isEmpty()) return
        
        if (customText == null) newMessageText = ""

        val messageData = hashMapOf(
            "senderId" to currentUserId,
            "senderName" to (sessionManager.getUserName() ?: ""),
            "text" to textToSend,
            "timestamp" to System.currentTimeMillis(),
            "messageType" to messageType
        )
        
        if (replyingToMessage != null) {
            messageData["replyToId"] = replyingToMessage!!.id
            messageData["replyToText"] = replyingToMessage!!.text
            replyingToMessage = null // Limpiar estado despues de enviar
        }

        db.collection("chats").document(conversationId).collection("messages").add(messageData)
            .addOnSuccessListener {
                // Scroll instantáneo al enviar para sensación Triple A
                scope.launch {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            }
        
        val participants = listOf(currentUserId, targetId ?: "").filter { it.isNotEmpty() }.sorted()
        val convData = mutableMapOf<String, Any>(
            "lastMessage" to if(messageType == "LOTTIE") "Emote" else textToSend,
            "timestamp" to System.currentTimeMillis(),
            "lastSenderId" to currentUserId,
            "participants" to participants
        )

        if (targetType == "COLMADO") {
            convData["colmadoId"] = targetId ?: ""
            convData["colmadoName"] = chatTitle
            convData["clientId"] = currentUserId
            convData["clientName"] = sessionManager.getUserName() ?: "Cliente"
        } else if (targetType == "CLIENT") {
            convData["colmadoId"] = currentUserId
            convData["colmadoName"] = sessionManager.getUserName() ?: "Colmado"
            convData["clientId"] = targetId ?: ""
            convData["clientName"] = chatTitle
        }

        db.collection("chats").document(conversationId).set(convData, com.google.firebase.firestore.SetOptions.merge())

        val senderName = sessionManager.getUserName() ?: "Usuario"
        val recipientRole = if (targetType == "COLMADO") "colmado" else "client"
        if (targetId != null) {
            com.domifiao.domifiao.utils.FCMHelper.notifyNewMessage(targetId, recipientRole, senderName, conversationId)
        }
    }

    fun scrollToMessage(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            scope.launch {
                listState.animateScrollToItem(index)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                    
                    // Avatar
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f))) {
                        if (targetPhoto.isNotEmpty()) {
                            ColmadoImage(targetPhoto, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(
                                if(targetType == "COLMADO") Icons.Default.Store else Icons.Default.Person, 
                                null, 
                                modifier = Modifier.align(Alignment.Center).size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(chatTitle, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        if (isTargetOnline) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text("En línea", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (targetLastSeen > 0L) {
                            val timeAgo = android.text.format.DateUtils.getRelativeTimeSpanString(targetLastSeen).toString()
                            Text("Visto por última vez: $timeAgo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(if(androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0F172A) else Color(0xFFF1F5F9))
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp) // Reducido para grupos de burbujas
                    ) {
                        itemsIndexed(messages, key = { _, it -> it.id }) { index, msg ->
                            val isMe = msg.senderId == currentUserId
                            val prevMsg = messages.getOrNull(index - 1)
                            val nextMsg = messages.getOrNull(index + 1)
                            
                            val isFirstInGroup = prevMsg?.senderId != msg.senderId
                            val isLastInGroup = nextMsg?.senderId != msg.senderId

                            // Swipe to reply logic
                            var offsetX by remember { mutableFloatStateOf(0f) }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = if (isFirstInGroup) 6.dp else 0.dp,
                                        bottom = if (isLastInGroup) 6.dp else 0.dp
                                    )
                                    .offset(x = offsetX.dp)
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (offsetX > 60f) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    replyingToMessage = msg
                                                }
                                                offsetX = 0f
                                            },
                                            onDragCancel = { offsetX = 0f }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            if (dragAmount > 0 || offsetX > 0) { // Solo deslizar a la derecha
                                                offsetX = (offsetX + dragAmount * 0.5f).coerceIn(0f, 100f)
                                            }
                                        }
                                    }
                            ) {
                                PremiumMessageBubble(
                                    message = msg, 
                                    isMe = isMe,
                                    isFirstInGroup = isFirstInGroup,
                                    isLastInGroup = isLastInGroup,
                                    onReplyClicked = { replyId -> scrollToMessage(replyId) }
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 8.dp,
                        shadowElevation = 16.dp,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp).navigationBarsPadding()) {
                            
                            // Caja de Respuesta (Reply Box)
                            AnimatedVisibility(
                                visible = replyingToMessage != null,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                replyingToMessage?.let { replyMsg ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f), RoundedCornerShape(12.dp)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.width(4.dp).height(40.dp).background(MaterialTheme.colorScheme.primary))
                                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(
                                                text = if(replyMsg.senderId == currentUserId) "Tú" else replyMsg.senderName.ifEmpty { chatTitle },
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if(replyMsg.messageType == "LOTTIE") "Sticker" else replyMsg.text,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { replyingToMessage = null }) {
                                            Icon(Icons.Default.Close, "Cancelar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Lottie Emotes Quick Replies
                                val lottieEmotes = listOf("hand" to R.raw.hand, "like" to R.raw.like)
                                lottieEmotes.forEach { (name, resId) ->
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(0.08f))
                                            .clickable { sendMessage(name, "LOTTIE") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LottieEmote(resId, modifier = Modifier.size(40.dp))
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newMessageText, 
                                    onValueChange = { newMessageText = it },
                                    placeholder = { Text("Escribe un mensaje...", fontSize = 15.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(28.dp),
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.5f)
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                FloatingActionButton(
                                    onClick = { sendMessage() },
                                    containerColor = if (newMessageText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray.copy(0.3f),
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp),
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
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
fun LottieEmote(resId: Int, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier
    )
}

@Composable
fun PremiumMessageBubble(
    message: ChatMessage, 
    isMe: Boolean, 
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true,
    onReplyClicked: (String) -> Unit = {}
) {
    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
    val timeColor = if (isMe) Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
    val alignment = if (isMe) Alignment.End else Alignment.Start
    
    // Forma dinámica de la burbuja (Tail logic)
    val shape = if (isMe) {
        RoundedCornerShape(
            topStart = 18.dp, 
            topEnd = if(isFirstInGroup) 18.dp else 4.dp, 
            bottomStart = 18.dp, 
            bottomEnd = if(isLastInGroup) 2.dp else 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if(isFirstInGroup) 18.dp else 4.dp, 
            topEnd = 18.dp, 
            bottomStart = if(isLastInGroup) 2.dp else 4.dp, 
            bottomEnd = 18.dp
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = if (message.messageType == "LOTTIE") Color.Transparent else bubbleColor,
            shape = shape,
            shadowElevation = if (message.messageType == "LOTTIE") 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 320.dp, min = 80.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                
                // Bloque de Respuesta Incrustado
                if (message.replyToText != null) {
                    Surface(
                        color = Color.Black.copy(0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable { message.replyToId?.let { onReplyClicked(it) } }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(if(isMe) Color.White else MaterialTheme.colorScheme.primary))
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(
                                    text = "Respuesta", 
                                    color = if(isMe) Color.White.copy(0.9f) else MaterialTheme.colorScheme.primary, 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = message.replyToText,
                                    color = if(isMe) Color.White.copy(0.8f) else MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                    fontSize = 13.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Contenido del mensaje principal
                if (message.messageType == "LOTTIE") {
                    val lottieRes = when(message.text.lowercase()) {
                        "hand" -> R.raw.hand
                        "like" -> R.raw.like
                        else -> R.raw.hand
                    }
                    LottieEmote(lottieRes, modifier = Modifier.size(100.dp))
                } else {
                    Text(text = message.text, color = textColor, fontSize = 15.sp, lineHeight = 20.sp)
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Tiempo y estado
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = time, 
                        fontSize = 10.sp, 
                        color = timeColor, 
                        fontWeight = FontWeight.Medium
                    )
                    if (isMe && message.messageType != "LOTTIE") {
                        Spacer(modifier = Modifier.width(4.dp))
                        // Icono de checks (Doble check azul/gris)
                        Icon(
                            imageVector = Icons.Default.Check, // Reemplazable con icono de doble check personalizado
                            contentDescription = "Estado",
                            tint = if (message.isRead) MaterialTheme.colorScheme.secondary else timeColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}