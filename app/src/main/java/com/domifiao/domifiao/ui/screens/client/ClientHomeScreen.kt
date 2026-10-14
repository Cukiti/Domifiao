@file:Suppress("DEPRECATION")

package com.domifiao.domifiao.ui.screens.client

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.domifiao.domifiao.R
import com.domifiao.domifiao.model.Colmado
import com.domifiao.domifiao.navigation.AppScreens
import com.domifiao.domifiao.ui.components.StorageImage
import com.domifiao.domifiao.ui.components.ColmadoImage
import com.domifiao.domifiao.ui.components.dominicanProvinces
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientHomeScreen(
    navController: NavController,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    viewModel: ClientHomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        activity?.finish()
    }

    fun updateUserLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.setGpsLoading(true)
            viewModel.setLocationStatus("Actualizando ubicación...")
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).addOnSuccessListener { location ->
                if (location != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val detectedMunicipality = addresses[0].locality ?: addresses[0].subAdminArea ?: ""
                                val detectedProvince = addresses[0].adminArea ?: ""
                                val normalizedProvince = dominicanProvinces.keys.find { detectedProvince.contains(it, ignoreCase = true) } ?: detectedProvince
                                withContext(Dispatchers.Main) {
                                    if (detectedMunicipality.isNotEmpty()) {
                                        viewModel.updateLocation(location.latitude, location.longitude, detectedMunicipality, normalizedProvince)
                                    } else viewModel.setLocationStatus("No se detectó municipio")
                                    viewModel.setGpsLoading(false)
                                }
                            }
                        } catch (e: Exception) { withContext(Dispatchers.Main) { viewModel.setGpsLoading(false) } }
                    }
                } else viewModel.setGpsLoading(false)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) updateUserLocation() }

    val filteredColmados = remember(uiState.colmadosList, uiState.searchText, uiState.showOnlyWithPoints) {
        uiState.colmadosList.filter { (uiState.searchText.isEmpty() || it.name.contains(uiState.searchText, ignoreCase = true) || it.address.contains(uiState.searchText, ignoreCase = true)) && (!uiState.showOnlyWithPoints || it.hasLoyaltyProgram) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.userPhoto.isNotEmpty()) StorageImage(imageData = uiState.userPhoto, contentDescription = "perfil", modifier = Modifier.size(80.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape))
                        else Box(Modifier.size(80.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) }
                        Spacer(Modifier.height(12.dp))
                        Text(uiState.userName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(auth.currentUser?.email ?: "", color = Color.White.copy(0.9f), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Person, null) }, label = { Text("Mi Perfil") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreens.ClientProfile.route) })
                NavigationDrawerItem(icon = { Icon(Icons.Default.ShoppingBag, null) }, label = { Text("Mis Pedidos") }, badge = { if (uiState.activeOrdersCount > 0) BadgeCount(uiState.activeOrdersCount) }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreens.ClientOrders.route) })
                NavigationDrawerItem(icon = { Icon(Icons.Default.History, null) }, label = { Text("Historial") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreens.ClientHistory.route) })
                NavigationDrawerItem(icon = { Icon(Icons.Default.CreditCard, null) }, label = { Text("Mis Créditos") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreens.ClientMyCredits.route) })
                NavigationDrawerItem(icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Favoritos") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreens.ClientFavorites.route) })
                NavigationDrawerItem(icon = { Icon(Icons.Default.Chat, null) }, label = { Text("Mensajes") }, badge = { if (uiState.unreadChatsCount > 0) BadgeCount(uiState.unreadChatsCount) }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreens.ClientChatList.route) })
                if (uiState.isApprovedDelivery) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(icon = { Icon(Icons.Default.DeliveryDining, null, tint = MaterialTheme.colorScheme.secondary) }, label = { Text("Modo Delivery", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }, badge = { if (uiState.activeDeliveriesCount > 0) BadgeCount(uiState.activeDeliveriesCount) }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate(AppScreens.DeliveryDashboard.route) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color(0xFFE3F2FD)))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.Red) }, label = { Text("Cerrar Sesión", color = Color.Red) }, selected = false, onClick = { scope.launch { drawerState.close() }; viewModel.toggleLogoutDialog(true) })
            }
        }
    ) {
        if (uiState.showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleLogoutDialog(false) }, 
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.Red) }, 
                title = { Text("Cerrar Sesión") }, 
                text = { Text("¿Estás seguro?") }, 
                confirmButton = { Button(onClick = { viewModel.toggleLogoutDialog(false); auth.signOut(); navController.navigate(AppScreens.Login.route) { popUpTo(0) { inclusive = true } } }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Cerrar Sesión") } }, 
                dismissButton = { TextButton(onClick = { viewModel.toggleLogoutDialog(false) }) { Text("Cancelar") } }
            )
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Column { Text("Domifiao", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(uiState.locationStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menú", tint = MaterialTheme.colorScheme.onSurface) } },
                    actions = {
                        // BOTÓN TEMA PREMIUM
                        Surface(
                            onClick = { onToggleDarkMode() }, 
                            shape = RoundedCornerShape(20.dp), 
                            color = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF5F5F5), 
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkMode) Color(0xFFFFD700).copy(0.5f) else Color.LightGray), 
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), 
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = if (isDarkMode) Color(0xFFFFD700) else Color(0xFFFF8C00), modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isDarkMode) "Oscuro" else "Claro", 
                                    fontSize = 10.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = if (isDarkMode) Color.White else Color.Black
                                )
                            }
                        }
                        IconButton(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) updateUserLocation() else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { 
                            if (uiState.isGpsLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary) 
                            else Icon(Icons.Default.MyLocation, null, tint = Color.Gray) 
                        }
                        Box { IconButton(onClick = { navController.navigate(AppScreens.ClientNotifications.route) }) { Icon(Icons.Default.Notifications, null, tint = Color.Gray) }; if (uiState.unreadNotificationsCount > 0) Box(Modifier.align(Alignment.TopEnd).offset((-4).dp, 4.dp).size(18.dp).background(Color.Red, CircleShape).border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Text(if (uiState.unreadNotificationsCount > 9) "9+" else uiState.unreadNotificationsCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = uiState.searchText, 
                    onValueChange = { viewModel.onSearchTextChanged(it) }, 
                    placeholder = { Text("Buscar colmado...") }, 
                    leadingIcon = { Icon(Icons.Default.Search, null) }, 
                    trailingIcon = { if (uiState.searchText.isNotEmpty()) IconButton(onClick = { viewModel.onSearchTextChanged("") }) { Icon(Icons.Default.Close, null) } }, 
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), 
                    shape = RoundedCornerShape(12.dp), 
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = uiState.showOnlyWithPoints, onClick = { viewModel.toggleShowOnlyWithPoints() }, label = { Text("Solo con Puntos") }, leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFC107), selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    QuickAccessButton("Pedidos", Icons.Default.ShoppingBag, uiState.activeOrdersCount) { navController.navigate(AppScreens.ClientOrders.route) }
                    QuickAccessButton("Chats", Icons.Default.Chat, uiState.unreadChatsCount) { navController.navigate(AppScreens.ClientChatList.route) }
                    QuickAccessButton("Favoritos", Icons.Default.Favorite) { navController.navigate(AppScreens.ClientFavorites.route) }
                    QuickAccessButton("Buscador", Icons.Default.Search) { navController.navigate(AppScreens.GlobalSearch.route) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (uiState.userMunicipality.isNotEmpty()) "Colmados en: ${uiState.userMunicipality}" else "Todos los colmados", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray) }
                Text(if (uiState.userMunicipality.isNotEmpty()) "Cerca de ti" else "Colmados Disponibles", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                
                if (uiState.isLoading) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                else if (filteredColmados.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Storefront, null, tint = Color.Gray, modifier = Modifier.size(64.dp)); Spacer(Modifier.height(16.dp)); Text(if (uiState.searchText.isNotEmpty()) "No hay resultados" else "No hay colmados aquí", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp)) } }
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(filteredColmados) { colmado ->
                        var dist = "--"
                        if (uiState.userLat != 0.0 && colmado.latitude != 0.0) {
                            val res = FloatArray(1); Location.distanceBetween(uiState.userLat, uiState.userLng, colmado.latitude, colmado.longitude, res)
                            dist = if (res[0] < 1000) "${res[0].toInt()} m" else String.format("%.1f km", res[0] / 1000)
                        }
                        ColmadoCardWithDistance(colmado = colmado, distance = dist, loyaltyAccount = uiState.loyaltyAccountsMap[colmado.id]) { navController.navigate(AppScreens.ClientColmadoProducts.createRoute(colmado.id)) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun BadgeCount(count: Int) {
    Box(Modifier.background(Color.Red, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(if (count > 9) "9+" else count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun QuickAccessButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, badgeCount: Int = 0, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(50.dp).background(if(androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            if (badgeCount > 0) Box(Modifier.offset(4.dp, (-4).dp).size(20.dp).background(Color.Red, CircleShape).border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Text(if (badgeCount > 9) "9+" else badgeCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(4.dp)); Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ColmadoCardWithDistance(colmado: Colmado, distance: String, loyaltyAccount: com.domifiao.domifiao.model.LoyaltyAccount? = null, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().height(220.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Box(Modifier.fillMaxSize()) {
            if (colmado.photoUrl.isNotEmpty()) ColmadoImage(colmado.photoUrl, Modifier.fillMaxSize(), ContentScale.Crop)
            else Image(painterResource(R.drawable.fondo_colmado), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f)))))
            Surface(color = Color.White, shape = RoundedCornerShape(bottomEnd = 12.dp), modifier = Modifier.align(Alignment.TopStart)) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { if (colmado.rating > 0.0) { Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(String.format("%.1f", colmado.rating), fontWeight = FontWeight.Bold, fontSize = 12.sp) } else Text("NUEVO", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary) } }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) { Text(colmado.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(colmado.address, color = Color.White.copy(0.9f), fontSize = 12.sp, maxLines = 1) } }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(bottomStart = 12.dp), modifier = Modifier.align(Alignment.TopEnd)) { Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Default.DirectionsRun, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(distance, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black) } }
            if (colmado.hasLoyaltyProgram) {
                val tierInfo = loyaltyAccount?.let { "${it.tier.emoji} ${it.tier.displayName} • ${it.tier.discountPercentage}%" } ?: "⭐ Puntos"
                Card(colors = CardDefaults.cardColors(containerColor = loyaltyAccount?.let { Color(it.tier.color) } ?: Color(0xFFFFC107)), shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp), modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp)) { Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { Text(tierInfo, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold); loyaltyAccount?.let { acc -> val next = when(acc.tier){ com.domifiao.domifiao.model.LoyaltyTier.BRONCE -> com.domifiao.domifiao.model.LoyaltyTier.PLATA; com.domifiao.domifiao.model.LoyaltyTier.PLATA -> com.domifiao.domifiao.model.LoyaltyTier.ORO; com.domifiao.domifiao.model.LoyaltyTier.ORO -> com.domifiao.domifiao.model.LoyaltyTier.PLATINO; else -> null }; next?.let { n -> val prog = (acc.totalPointsEarned.toFloat() / n.requiredPoints.toFloat()).coerceIn(0f, 1f); Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(0.3f), RoundedCornerShape(2.dp))) { Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(Color.White, RoundedCornerShape(2.dp))) }; Spacer(Modifier.height(2.dp)); Text("${n.requiredPoints - acc.totalPointsEarned} pts → ${n.emoji}", color = Color.White.copy(0.9f), fontSize = 8.sp) } } } }
            }
            Card(colors = CardDefaults.cardColors(containerColor = if (colmado.isOpen) MaterialTheme.colorScheme.primary else Color.DarkGray), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Text(if (colmado.isOpen) "ABIERTO" else "CERRADO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
        }
    }
}