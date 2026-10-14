package com.domifiao.domifiao.ui.screens.common

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.R
import com.domifiao.domifiao.navigation.AppScreens
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.viewport
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import com.domifiao.domifiao.ui.components.OrderProgressBar

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    navController: NavController,
    orderId: String,
    isDeliveryMode: Boolean = false
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Referencias a Mapbox
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var annotationManager by remember { mutableStateOf<PointAnnotationManager?>(null) }

    // Coordenadas
    var deliveryPos by remember { mutableStateOf<Point?>(null) }
    var clientPos by remember { mutableStateOf<Point?>(null) }

    // Datos del Pedido
    var houseDesc by remember { mutableStateOf("Cargando...") }
    var customerName by remember { mutableStateOf("Cliente") }
    var customerPhone by remember { mutableStateOf("") }
    var orderStatus by remember { mutableStateOf("") }
    var colmadoId by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("") }
    var orderTotal by remember { mutableIntStateOf(0) }

    // Estado Calificación
    var showRateDialog by remember { mutableStateOf(false) }
    var clientRating by remember { mutableIntStateOf(0) }
    
    // Información de la casa del cliente
    var clientHousePhoto by remember { mutableStateOf<String?>(null) }
    var clientHouseDescription by remember { mutableStateOf("") }
    var showHousePhotoDialog by remember { mutableStateOf(false) }

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    // --- LÓGICA DE ACTUALIZACIÓN DE MARCADORES ---
    fun updateMarkers() {
        val manager = annotationManager ?: return
        manager.deleteAll()

        // 1. Casa del Cliente
        clientPos?.let {
            val bitmap = bitmapFromVector(context, R.drawable.ic_launcher_foreground)
            if (bitmap != null) {
                val options = PointAnnotationOptions()
                    .withPoint(it)
                    .withIconImage(bitmap)
                    .withIconSize(1.0)
                manager.create(options)
            }
        }

        // 2. Delivery (Solo para el cliente)
        if (!isDeliveryMode) {
            deliveryPos?.let {
                val bitmap = createColoredMarkerBitmap(context, Color.Green)
                val options = PointAnnotationOptions()
                    .withPoint(it)
                    .withIconImage(bitmap)
                    .withIconSize(1.0)
                manager.create(options)
            }
        }
    }

    // --- 1. ESCUCHAR DATOS FIREBASE ---
    LaunchedEffect(orderId) {
        val docRef = db.collection("orders").document(orderId)
        docRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val dLat = snapshot.getDouble("deliveryLat") ?: 0.0
                val dLng = snapshot.getDouble("deliveryLng") ?: 0.0

                if (dLat != 0.0 && dLng != 0.0) {
                    val newPos = Point.fromLngLat(dLng, dLat)
                    deliveryPos = newPos
                    updateMarkers()

                    if (!isDeliveryMode) {
                        mapViewRef?.mapboxMap?.flyTo(
                            CameraOptions.Builder().center(newPos).zoom(16.0).build()
                        )
                    }
                }

                // Otros datos
                orderStatus = snapshot.getString("status") ?: ""
                colmadoId = snapshot.getString("colmadoId") ?: ""
                clientId = snapshot.getString("clientId") ?: ""
                customerName = snapshot.getString("customerName") ?: "Cliente"
                customerPhone = snapshot.getString("customerPhone") ?: ""
                paymentMethod = snapshot.getString("paymentMethod") ?: "Efectivo"
                orderTotal = snapshot.getLong("total")?.toInt() ?: 0

                if (clientId.isNotEmpty() && clientPos == null) {
                    db.collection("users").document(clientId).get().addOnSuccessListener { userDoc ->
                        houseDesc = userDoc.getString("houseDescription") ?: "Sin descripción"
                        clientHousePhoto = userDoc.getString("housePhotoBase64")
                        clientHouseDescription = userDoc.getString("houseDescription") ?: ""
                        val cLat = userDoc.getDouble("latitude") ?: 0.0
                        val cLng = userDoc.getDouble("longitude") ?: 0.0
                        if (cLat != 0.0 && cLng != 0.0) {
                            clientPos = Point.fromLngLat(cLng, cLat)
                            updateMarkers()
                            if (isDeliveryMode && deliveryPos == null) {
                                mapViewRef?.mapboxMap?.setCamera(CameraOptions.Builder().center(clientPos).zoom(15.0).build())
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 2. TRANSMITIR GPS ---
    if (isDeliveryMode) {
        val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
        DisposableEffect(Unit) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateDistanceMeters(10f)
                .build()
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        db.collection("orders").document(orderId).update(
                            mapOf("deliveryLat" to location.latitude, "deliveryLng" to location.longitude)
                        )
                    }
                }
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            }
            onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
        }
    }

    // --- ACCIONES DE MAPA ---
    fun recenterMap() {
        val mapView = mapViewRef ?: return
        if (isDeliveryMode) {
            val followPuckState = mapView.viewport.makeFollowPuckViewportState(
                FollowPuckViewportStateOptions.Builder()
                    .pitch(45.0)
                    .zoom(17.0)
                    .build()
            )
            mapView.viewport.transitionTo(followPuckState)
        } else if (clientPos != null) {
            mapView.mapboxMap.flyTo(CameraOptions.Builder().center(clientPos).zoom(16.0).build())
        }
    }

    // --- UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isDeliveryMode) "Ruta de Entrega" else "Rastrear Pedido") },
                navigationIcon = {
                    IconButton(onClick = { navigateBackSecurely() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // --- MAPBOX MAP ---
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        mapboxMap.loadStyle(Style.MAPBOX_STREETS)
                        val manager = annotations.createPointAnnotationManager()
                        annotationManager = manager
                        
                        if (isDeliveryMode) {
                            location.enabled = true
                            location.pulsingEnabled = true
                            
                            val followPuckState = viewport.makeFollowPuckViewportState(
                                FollowPuckViewportStateOptions.Builder()
                                    .pitch(45.0)
                                    .zoom(17.0)
                                    .build()
                            )
                            viewport.transitionTo(followPuckState)
                        }
                        mapViewRef = this
                    }
                }
            )

            // BOTONES FLOTANTES
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { recenterMap() }, 
                    containerColor = MaterialTheme.colorScheme.surface, 
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.MyLocation, "Centrar")
                }
                if (isDeliveryMode) {
                    FloatingActionButton(onClick = { 
                        clientPos?.let {
                            val uri = Uri.parse("google.navigation:q=${it.latitude()},${it.longitude()}&mode=d")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
                        }
                    }, containerColor = Color(0xFF4CAF50), contentColor = Color.White) {
                        Icon(Icons.Default.Directions, "GPS")
                    }
                }
            }

            // PANEL INFERIOR
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(16.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (isDeliveryMode) {
                        Text(
                            "Entregar a: $customerName", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Ref: $houseDesc", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant, 
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OrderProgressBar(status = orderStatus)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledIconButton(
                                onClick = { 
                                    if (customerPhone.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone")))
                                }, 
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                                )
                            ) {
                                Icon(Icons.Default.Call, null, tint = Color(0xFF4CAF50))
                            }
                            Button(
                                onClick = { showRateDialog = true }, 
                                modifier = Modifier.weight(1f), 
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("Finalizar Entrega", color = Color.White)
                            }
                        }
                    } else {
                        Text(
                            "Tu pedido está en camino", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OrderProgressBar(status = orderStatus)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { 
                                if (colmadoId.isNotEmpty()) {
                                    navController.navigate(AppScreens.Chat.createColmadoChat(colmadoId)) 
                                }
                            }, 
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chat con Delivery")
                        }
                    }
                }
            }
        }
    }

    // DIÁLOGOS
    if (showRateDialog) {
        Dialog(onDismissRequest = { }) {
            Card(
                shape = RoundedCornerShape(16.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Entrega Completada", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            db.collection("orders").document(orderId).update("status", "COMPLETED").addOnSuccessListener { navigateBackSecurely() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("Confirmar", color = Color.White) }
                }
            }
        }
    }
}

// Utilidades Bitmaps (se mantienen igual)
fun bitmapFromVector(context: Context, @DrawableRes vectorResId: Int): Bitmap? {
    return ContextCompat.getDrawable(context, vectorResId)?.run {
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        bitmap
    }
}

fun createColoredMarkerBitmap(context: Context, color: Color): Bitmap {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.GREEN
        isAntiAlias = true
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return bitmap
}