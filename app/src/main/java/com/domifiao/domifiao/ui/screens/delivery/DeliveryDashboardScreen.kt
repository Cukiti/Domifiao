package com.domifiao.domifiao.ui.screens.delivery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.domifiao.domifiao.R
import com.domifiao.domifiao.navigation.AppScreens

data class DeliveryOrder(
    val id: String,
    val colmadoName: String,
    val customerName: String,
    val customerId: String,
    val address: String,
    val status: String,
    val total: Int,
    val lat: Double,
    val lng: Double,
    val assignedToMe: Boolean,
    val timestamp: Long,
    val housePhotoUrl: String? = null,
    val houseDescription: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDashboardScreen(
    navController: NavController,
    viewModel: DeliveryDashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Navegacion segura
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val orders = uiState.orders
    val isLoading = uiState.isLoading
    val currentTab = uiState.currentTab

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            val toastText = when (msg) {
                "ORDER_ACCEPTED" -> context.getString(R.string.order_accepted_toast)
                "ORDER_TAKEN" -> context.getString(R.string.order_taken_by_other)
                "ERROR_LOADING" -> "Error al cargar los pedidos"
                else -> msg
            }
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    // Estado del diálogo de foto
    var selectedOrderForPhoto by remember { mutableStateOf<DeliveryOrder?>(null) }
    var showHousePhotoDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun openGps(address: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.delivery_dashboard_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                TabButton(text = stringResource(R.string.tab_available), selected = currentTab == 0, onClick = { viewModel.setTab(0) }, modifier = Modifier.weight(1f))
                TabButton(text = stringResource(R.string.tab_my_deliveries), selected = currentTab == 1, onClick = { viewModel.setTab(1) }, modifier = Modifier.weight(1f))
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) 
                }
            } else if (orders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Moped, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if(currentTab==0) stringResource(R.string.no_new_orders) else stringResource(R.string.no_active_deliveries), 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp), 
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(orders) { order ->
                        DeliveryOrderCard(
                            order = order,
                            onAccept = { viewModel.acceptOrder(order.id) },
                            onOpenGps = { openGps(order.address) },
                            onGoToMap = {
                                navController.navigate(AppScreens.LiveMap.createRoute(order.id, true))
                            },
                            onViewHouse = {
                                selectedOrderForPhoto = order
                                showHousePhotoDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showHousePhotoDialog && selectedOrderForPhoto != null) {
        val order = selectedOrderForPhoto!!
        Dialog(onDismissRequest = { showHousePhotoDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.house_of, order.customerName), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { showHousePhotoDialog = false }) {
                            Icon(Icons.Default.Close, stringResource(R.string.close), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!order.housePhotoUrl.isNullOrEmpty()) {
                        com.domifiao.domifiao.ui.components.StorageImage(
                            imageData = order.housePhotoUrl,
                            contentDescription = stringResource(R.string.house_photo_desc),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (!order.houseDescription.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.description), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = order.houseDescription, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = order.address, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showHousePhotoDialog = false
                                openGps(order.address)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.google_maps))
                        }
                        Button(
                            onClick = { showHousePhotoDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal,
            color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if(selected) Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
        else Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
fun DeliveryOrderCard(
    order: DeliveryOrder,
    onAccept: () -> Unit,
    onOpenGps: () -> Unit,
    onGoToMap: () -> Unit,
    onViewHouse: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(order.colmadoName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.price_format, order.total), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (order.housePhotoUrl != null) {
                        Icon(Icons.Default.Home, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if(order.assignedToMe) {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                            Text(stringResource(R.string.yours), modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Status y Total
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val currentStage = when(order.status) {
                    "PENDING" -> 1
                    "WAITING_APPROVAL" -> 2
                    "PREPARING" -> 3
                    "DELIVERING", "WAITING_CLIENT_CONFIRMATION" -> 4
                    "COMPLETED" -> 5
                    else -> 0
                }
                
                val statusColor = when(order.status) {
                    "PENDING" -> Color(0xFFFF9800)
                    "WAITING_APPROVAL" -> Color(0xFFD32F2F)
                    "PREPARING" -> Color(0xFF2196F3)
                    "DELIVERING" -> Color(0xFF9C27B0)
                    "WAITING_CLIENT_CONFIRMATION" -> Color(0xFF009688)
                    "COMPLETED" -> Color(0xFF4CAF50)
                    "CANCELLED" -> Color.Red
                    else -> Color.Gray
                }

                Column(horizontalAlignment = Alignment.Start) {
                    Surface(color = statusColor.copy(0.1f), shape = RoundedCornerShape(4.dp)) {
                        val statusText = when(order.status) {
                            "PENDING" -> stringResource(R.string.status_pending)
                            "WAITING_APPROVAL" -> stringResource(R.string.status_waiting_approval)
                            "PREPARING" -> stringResource(R.string.status_preparing_short)
                            "DELIVERING" -> stringResource(R.string.status_delivering)
                            "WAITING_CLIENT_CONFIRMATION" -> stringResource(R.string.status_waiting_confirmation)
                            "COMPLETED" -> stringResource(R.string.status_completed)
                            "CANCELLED" -> stringResource(R.string.status_cancelled)
                            else -> order.status
                        }
                        Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    if (currentStage > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.width(75.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (i in 1..5) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(if (i <= currentStage) statusColor else Color.LightGray.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Moped, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.price_format, order.total), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.deliver_to, order.customerName), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(order.address, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 2)
                    if (!order.houseDescription.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.location_desc_format, order.houseDescription), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (order.housePhotoUrl != null && order.assignedToMe) {
                OutlinedButton(
                    onClick = onViewHouse,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Home, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.view_house))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (order.assignedToMe) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenGps,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Map, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.gps))
                    }
                    Button(
                        onClick = onGoToMap,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Default.Navigation, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.start))
                    }
                }
            } else {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.btn_accept_order_caps), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
