package com.domifiao.domifiao.ui.screens.client

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.Card

import androidx.compose.material3.CardDefaults

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.HorizontalDivider

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.LinearProgressIndicator

import androidx.compose.material3.OutlinedButton

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.material3.TopAppBar

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.domifiao.domifiao.data.SessionManager
import com.domifiao.domifiao.model.CartManager
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.StorageImage
import com.domifiao.domifiao.ui.components.IdVerificationComponent
import com.domifiao.domifiao.utils.IdentityVerifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ClientDebtInfo(
    val colmadoId: String,
    val colmadoName: String,
    val amount: Int,
    val limit: Int
)

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val sessionManager = SessionManager(context)
    val userId = auth.currentUser?.uid
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Navegacion segura
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (!navController.popBackStack()) {
                navController.navigate(AppScreens.ClientHome.route)
            }
        }
    }

        var isLoading by remember { mutableStateOf(true) }


        var showVerificationDialog by remember { mutableStateOf(false) }


        var showLogoutDialog by remember { mutableStateOf(false) }


    


        var userName by remember { mutableStateOf("Cargando...") }
    var userCedula by remember { mutableStateOf("") }
    var userPhone by remember { mutableStateOf("") }
    var userAddress by remember { mutableStateOf("") }
    var userProvince by remember { mutableStateOf("") }
    var userPhoto by remember { mutableStateOf("") }
    var isVerified by remember { mutableStateOf(false) }

    var userRating by remember { mutableDoubleStateOf(0.0) }
    var userReviews by remember { mutableIntStateOf(0) }

    var activeDebts by remember { mutableStateOf<List<ClientDebtInfo>>(emptyList()) }

    var idPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var aiVerificationStatus by remember { mutableStateOf<String?>(null) }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var isIdentityValid by remember { mutableStateOf(false) }

    if (userId == null) {
        LaunchedEffect(Unit) {
            sessionManager.clearSession()
            navController.navigate(AppScreens.Login.route) { popUpTo(0) }
        }
        return
    }

    LaunchedEffect(Unit) {
        db.collection("users").document(userId).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                userName = doc.getString("name") ?: "Usuario"
                userCedula = doc.getString("cedula") ?: ""
                userPhone = doc.getString("phone") ?: ""
                userAddress = doc.getString("address") ?: ""
                userProvince = doc.getString("province") ?: ""
                userPhoto = doc.getString("photoUrl") ?: doc.getString("photoBase64") ?: ""
                isVerified = doc.getBoolean("isIdentityVerified") ?: false
                userRating = doc.getDouble("rating") ?: 0.0
                userReviews = (doc.getLong("ratingCount") ?: 0).toInt()
            }
        }

        db.collection("debts")
            .whereEqualTo("clientId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { isLoading = false; return@addSnapshotListener }
                if (snapshot != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val debtsList = mutableListOf<ClientDebtInfo>()
                        for (doc in snapshot.documents) {
                            val cId = doc.getString("colmadoId") ?: ""
                            var cName = doc.getString("colmadoName")
                            if (cName == null || cName == "Colmado" || cName.isEmpty()) {
                                try {
                                    val cDoc = db.collection("colmados_requests").document(cId).get().await()
                                    cName = cDoc.getString("name") ?: "Colmado"
                                } catch (ex: Exception) {
                                    cName = "Colmado"
                                }
                            }
                            debtsList.add(
                                ClientDebtInfo(
                                    colmadoId = cId,
                                    colmadoName = cName ?: "Colmado",
                                    amount = doc.getLong("amount")?.toInt() ?: 0,
                                    limit = doc.getLong("limit")?.toInt() ?: 0
                                )
                            )
                        }
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            activeDebts = debtsList
                            isLoading = false
                        }
                    }
                }
            }
    }

    val totalDebt = activeDebts.sumOf { it.amount }

    fun saveVerification() {
        if (idPhotoUri == null) return
        isAiAnalyzing = true

        scope.launch {
            try {
                // Subir foto de cedula a Storage
                val idPhotoUrl = com.domifiao.domifiao.utils.ImageStorageManager.uploadImage(
                    context = context,
                    imageUri = idPhotoUri!!,
                    ownerId = userId,
                    imageType = com.domifiao.domifiao.utils.ImageStorageManager.ImageType.USER_CEDULA
                )

                if (idPhotoUrl != null) {
                    db.collection("users").document(userId).update(
                        mapOf(
                            "idPhotoUrl" to idPhotoUrl,
                            "isIdentityVerified" to true
                        )
                    ).await()

                    withContext(Dispatchers.Main) {
                        isAiAnalyzing = false
                        showVerificationDialog = false
                        Toast.makeText(context, "Identidad verificada!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isAiAnalyzing = false
                        Toast.makeText(context, "Error al subir imagen", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isAiAnalyzing = false
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        navigateBackSecurely()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(AppScreens.ClientEditProfile.route) }) {
                        Icon(Icons.Default.Edit, "Editar", tint = Color(0xFF2196F3))
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
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4CAF50)) }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 30.dp)
            ) {
                // HEADER
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val borderColor = if(isVerified) Color(0xFF4CAF50) else Color.Gray

                            Box(
                                modifier = Modifier.size(110.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(4.dp, borderColor.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (userPhoto.isNotEmpty()) {
                                    StorageImage(imageData = userPhoto, contentDescription = "Foto de perfil", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                                }
                                if (isVerified) {
                                    Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF4CAF50), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(userName, fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(userProvince.ifEmpty { "Ubicación no definida" }, fontSize = 14.sp, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (!isVerified) {
                                Card(
                                    onClick = {
                                        idPhotoUri = null
                                        aiVerificationStatus = null
                                        isIdentityValid = false
                                        showVerificationDialog = true
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF4E342E) else Color(0xFFFFF3E0)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Perfil no verificado. Pulsa para verificar.", fontSize = 12.sp, color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFFFCC80) else Color(0xFFE65100))
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(50), 
                                    color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9), 
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = String.format("%.1f", userRating), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(text = " ($userReviews opiniones)", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
                // TARJETAS DE PUNTOS Y RECOMPENSAS (POR COLMADO)
                item {
                    var loyaltyAccounts by remember { mutableStateOf<List<com.domifiao.domifiao.model.LoyaltyAccount>>(emptyList()) }

                    LaunchedEffect(userId) {
                        com.domifiao.domifiao.utils.LoyaltyManager.getAllLoyaltyAccounts(
                            userId = userId,
                            onSuccess = { loyaltyAccounts = it }
                        )
                    }

                    if (loyaltyAccounts.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Programas de Puntos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            // Resumen Total
                            val totalPoints = loyaltyAccounts.sumOf { it.currentPoints }
                            val averageDiscount = loyaltyAccounts.map { it.tier.discountPercentage }.average()
                            val accountsCount = loyaltyAccounts.size

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF8E1)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Total de Puntos",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFC107),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "$totalPoints",
                                                    fontSize = 28.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFF57C00)
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "$accountsCount Colmado${if (accountsCount > 1) "s" else ""}",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                "Descuento promedio: %.1f%%".format(averageDiscount),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            loyaltyAccounts.forEach { account ->
                                Card(
                                    onClick = { navController.navigate(AppScreens.ClientLoyalty.route) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(account.tier.color)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                account.colmadoName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(alpha = 0.9f)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    account.tier.emoji,
                                                    fontSize = 18.sp
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    account.tier.displayName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "${account.currentPoints} pts",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                if (account.tier.discountPercentage > 0) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "• ${account.tier.discountPercentage}% desc",
                                                        fontSize = 12.sp,
                                                        color = Color.White.copy(alpha = 0.9f)
                                                    )
                                                }
                                            }
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color.White.copy(0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                // RESUMEN FINANCIERO
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Deuda Total Global", color = Color.White.copy(0.7f), fontSize = 12.sp)
                                Text(text = "RD$ $totalDebt", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if(totalDebt > 0) Color(0xFFFF5252) else Color(0xFF69F0AE))
                            }
                            Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White.copy(0.2f), modifier = Modifier.size(48.dp))
                        }
                    }
                }

                item { Text("Estado de Cuentas", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

                if (activeDebts.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SentimentVerySatisfied, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("¡Estás libre de deudas!", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text("No tienes cuentas pendientes.", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                } else {
                    items(activeDebts) { debt ->
                        CreditStatusCard(debt = debt, onClick = { navController.navigate(AppScreens.ClientDebtDetail.createRoute(debt.colmadoId, debt.colmadoName, debt.amount, debt.limit)) })
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Información de Contacto", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ProfileInfoRow(Icons.Default.Phone, "Celular", userPhone)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(0.2f))
                            ProfileInfoRow(Icons.Default.Home, "Dirección", userAddress)
                        }
                    }
                }

                                item {


                                    Spacer(modifier = Modifier.height(32.dp))


                                    OutlinedButton(


                                        onClick = { showLogoutDialog = true },


                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp),


                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),


                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(0.5f))


                                    ) {


                                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null)


                                        Spacer(modifier = Modifier.width(8.dp))


                                        Text("Cerrar Sesión")


                                    }


                                }


                            }


                        }


                    }


                


                        if (showLogoutDialog) {


                


                            AlertDialog(


                


                                onDismissRequest = { showLogoutDialog = false },


                


                                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.Red) },


                


                                title = { Text("Cerrar Sesión") },


                


                                text = { Text("¿Estás seguro de que quieres cerrar la sesión?") },


                


                                confirmButton = {


                


                                    Button(


                


                                        onClick = {


                


                                            showLogoutDialog = false


                


                                            auth.signOut()


                


                                            sessionManager.clearSession()


                


                                            CartManager.clearCart()


                


                                            navController.navigate(AppScreens.Login.route) { popUpTo(0) }


                


                                        },


                


                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)


                


                                    ) {


                


                                        Text("Cerrar Sesión")


                


                                    }


                


                                },


                


                                dismissButton = {


                


                                    TextButton(onClick = { showLogoutDialog = false }) {


                


                                        Text("Cancelar")


                


                                    }


                


                                }


                


                            )


                


                        }


                


                    if (showVerificationDialog) {
        Dialog(onDismissRequest = { if (!isAiAnalyzing) showVerificationDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Verificar Identidad", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (!isAiAnalyzing) { IconButton(onClick = { showVerificationDialog = false }) { Icon(Icons.Default.Close, null) } }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    IdVerificationComponent(
                        photoUri = idPhotoUri,
                        onPhotoSelected = { uri ->
                            idPhotoUri = uri
                            if (uri != null) {
                                isAiAnalyzing = true
                                scope.launch {
                                    val result = IdentityVerifier.verifyImageContent(context, uri, userCedula, userName)
                                    isAiAnalyzing = false
                                    when(result) {
                                        is IdentityVerifier.VerificationResult.Verified -> { aiVerificationStatus = "¡Verificación Exitosa!"; isIdentityValid = true }
                                        is IdentityVerifier.VerificationResult.Partial -> { aiVerificationStatus = "PARTIAL"; isIdentityValid = true }
                                        else -> { aiVerificationStatus = "Verificación Fallida"; isIdentityValid = false }
                                    }
                                }
                            }
                        },
                        isAnalyzing = isAiAnalyzing,
                        verificationStatus = aiVerificationStatus,
                        onFinish = { saveVerification() },
                        onSkip = { showVerificationDialog = false },
                        isProfileContext = true
                    )

                    if (aiVerificationStatus != null) {
                        val textColor = if (aiVerificationStatus == "¡Verificación Exitosa!") Color(0xFF4CAF50) else Color.Red
                        Text(
                            text = aiVerificationStatus!!,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreditStatusCard(debt: ClientDebtInfo, onClick: () -> Unit) {
    val progress = if (debt.limit > 0) debt.amount.toFloat() / debt.limit.toFloat() else 0f
    val available = (debt.limit - debt.amount).coerceAtLeast(0)
    val statusColor = if (progress > 0.9f) Color(0xFFD32F2F) else if (progress > 0.5f) Color(0xFFFF9800) else Color(0xFF4CAF50)

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(Color(0xFFE3F2FD), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Store, null, tint = Color(0xFF2196F3)) }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(debt.colmadoName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Límite: RD$ ${debt.limit}", fontSize = 12.sp, color = Color.Gray)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Deuda", fontSize = 11.sp, color = Color.Gray); Text("RD$ ${debt.amount}", fontWeight = FontWeight.Bold, color = statusColor) }
                Column(horizontalAlignment = Alignment.End) { Text("Disponible", fontSize = 11.sp, color = Color.Gray); Text("RD$ $available", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = statusColor, trackColor = Color(0xFFF5F5F5))
        }
    }
}

@Composable
fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column { Text(text = label, fontSize = 12.sp, color = Color.Gray); Text(text = value.ifEmpty { "No definido" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray) }
    }
}