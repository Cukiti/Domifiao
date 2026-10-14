package com.domifiao.domifiao.ui.screens.colmado

import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.domifiao.domifiao.R
import com.domifiao.domifiao.navigation.AppScreens

@Composable
fun OrderStatus.displayName(): String = when (this) {
    OrderStatus.PENDING -> stringResource(R.string.status_pending)
    OrderStatus.WAITING_APPROVAL -> stringResource(R.string.status_waiting_approval)
    OrderStatus.PREPARING -> stringResource(R.string.status_preparing_short)
    OrderStatus.DELIVERING -> stringResource(R.string.status_delivering)
    OrderStatus.WAITING_CLIENT_CONFIRMATION -> stringResource(R.string.status_waiting_confirmation)
    OrderStatus.COMPLETED -> stringResource(R.string.status_completed)
    OrderStatus.CANCELLED -> stringResource(R.string.status_cancelled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColmadoOrdersScreen(
    navController: NavController,
    viewModel: ColmadoOrdersViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estado para Diálogo de Rechazo
    var showRejectDialog by remember { mutableStateOf(false) }
    var orderToReject by remember { mutableStateOf<OrderSummary?>(null) }

    // Manejo de Mensajes
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { code ->
            val msg = when(code) {
                "TOAST_CREDIT_ORDER_CLOSED" -> context.getString(R.string.toast_credit_order_closed)
                "STATUS_PREPARING" -> context.getString(R.string.status_preparing_short)
                "STATUS_DELIVERING" -> context.getString(R.string.status_delivering)
                "STATUS_CANCELLED" -> context.getString(R.string.status_cancelled)
                else -> context.getString(R.string.toast_status_updated)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            if(code == "STATUS_CANCELLED") showRejectDialog = false
            viewModel.clearMessages()
        }
    }

    // --- NAVEGACIÓN SEGURA ---
    fun navigateBackSecurely() {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navController.popBackStack()
        }
    }
    BackHandler { navigateBackSecurely() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.order_management_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navigateBackSecurely() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TabRow(
                selectedTabIndex = uiState.selectedTab, 
                containerColor = MaterialTheme.colorScheme.surface, 
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                val titles = listOf(stringResource(R.string.tab_pending), stringResource(R.string.tab_history))
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.onTabSelected(index) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontWeight = FontWeight.Bold)
                                if(index == 0 && uiState.activeOrders.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BadgeCount(uiState.activeOrders.size)
                                }
                            }
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else {
                val listToShow = if (uiState.selectedTab == 0) uiState.activeOrders else uiState.historyOrders

                if (listToShow.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if(uiState.selectedTab==0) stringResource(R.string.no_pending_orders) else stringResource(R.string.no_recent_history), 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(16.dp), 
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(listToShow) { order ->
                            ColmadoOrderCard(
                                order = order,
                                onViewDetails = {
                                    navController.navigate(AppScreens.ColmadoOrderDetail.createRoute(order.id))
                                },
                                onQuickReject = {
                                    orderToReject = order
                                    showRejectDialog = true
                                },
                                onStatusChange = { newStatus ->
                                    viewModel.updateOrderStatus(order, newStatus)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // DIÁLOGO DE RECHAZO
    if (showRejectDialog && orderToReject != null) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text(stringResource(R.string.reject_order_title)) },
            text = { Text(stringResource(R.string.confirm_reject_order_format, orderToReject?.customerName ?: "")) },
            confirmButton = {
                Button(
                    onClick = { viewModel.updateOrderStatus(orderToReject!!, OrderStatus.CANCELLED) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text(stringResource(R.string.btn_yes_reject)) }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun ColmadoOrderCard(
    order: OrderSummary,
    onViewDetails: () -> Unit,
    onQuickReject: () -> Unit,
    onStatusChange: (OrderStatus) -> Unit
) {
    val timeAgo = DateUtils.getRelativeTimeSpanString(order.timestamp).toString()
    val isFiado = order.paymentMethod == "Fiado"

    val statusColor = when(order.status) {
        OrderStatus.PENDING -> Color(0xFFFF9800)
        OrderStatus.WAITING_APPROVAL -> Color(0xFFD32F2F)
        OrderStatus.PREPARING -> Color(0xFF2196F3)
        OrderStatus.DELIVERING -> Color(0xFF9C27B0)
        OrderStatus.WAITING_CLIENT_CONFIRMATION -> Color(0xFF009688)
        OrderStatus.COMPLETED -> Color(0xFF4CAF50)
        OrderStatus.CANCELLED -> Color.Red
    }

    val allPricesPresent = order.products.isNotEmpty() && order.products.all { it.price > 0 }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onViewDetails() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(order.customerName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(timeAgo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de productos con precios
            order.products.forEach { product ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.product_quantity_name_format, product.quantityDisplay, product.name),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if(product.price > 0) stringResource(R.string.price_format, product.calculateTotal().toInt().toString()) else stringResource(R.string.pending_quote),
                        fontSize = 13.sp,
                        fontWeight = if(product.price > 0) FontWeight.Medium else FontWeight.Normal,
                        color = if(product.price > 0) MaterialTheme.colorScheme.onSurface else Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status y Total
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val currentStage = when(order.status) {
                    OrderStatus.PENDING -> 1
                    OrderStatus.WAITING_APPROVAL -> 2
                    OrderStatus.PREPARING -> 3
                    OrderStatus.DELIVERING, OrderStatus.WAITING_CLIENT_CONFIRMATION -> 4
                    OrderStatus.COMPLETED -> 5
                    else -> 0
                }

                Column(horizontalAlignment = Alignment.Start) {
                    Surface(color = statusColor.copy(0.1f), shape = RoundedCornerShape(4.dp)) {
                        Text(order.status.displayName(), color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
                    Icon(if(isFiado) Icons.Default.CreditCard else Icons.Default.AttachMoney, null, tint = if(isFiado) Color(0xFFFF9800) else Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.price_format, order.total.toString()), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // --- ACCIONES RÁPIDAS (SOLO PARA PEDIDOS ACTIVOS) ---
            if (order.status != OrderStatus.COMPLETED && order.status != OrderStatus.CANCELLED) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // BOTÓN 1: ACCIÓN NEGATIVA OR SECUNDARIA
                    if (order.status == OrderStatus.PENDING || order.status == OrderStatus.WAITING_CLIENT_CONFIRMATION) {
                        OutlinedButton(
                            onClick = {
                                if (order.status == OrderStatus.PENDING) {
                                    if (allPricesPresent) onViewDetails() // Si todo tiene precio, el secundario es ver
                                    else onQuickReject() // Si falta precio, el secundario es rechazar
                                } else {
                                    onViewDetails() // En WAITING_CLIENT_CONFIRMATION, el secundario es ver detalles
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if(order.status == OrderStatus.PENDING && !allPricesPresent) Color.Red else MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            val btnText = when {
                                order.status == OrderStatus.PENDING && allPricesPresent -> stringResource(R.string.btn_view_order)
                                order.status == OrderStatus.PENDING -> stringResource(R.string.btn_reject)
                                else -> stringResource(R.string.btn_view_details)
                            }
                            Text(btnText)
                        }
                    }

                    // BOTÓN 2: ACCIÓN POSITIVA O NAVEGACIÓN
                    Button(
                        onClick = {
                            if (order.status == OrderStatus.PENDING) {
                                if (allPricesPresent) onStatusChange(OrderStatus.PREPARING)
                                else onViewDetails()
                            } else if (order.status == OrderStatus.WAITING_APPROVAL) {
                                onViewDetails()
                            } else {
                                when(order.status) {
                                    OrderStatus.PREPARING -> onStatusChange(OrderStatus.DELIVERING)
                                    OrderStatus.DELIVERING -> onStatusChange(OrderStatus.WAITING_CLIENT_CONFIRMATION)
                                    OrderStatus.WAITING_CLIENT_CONFIRMATION -> onStatusChange(OrderStatus.COMPLETED)
                                    else -> onViewDetails()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if(order.status == OrderStatus.PENDING && !allPricesPresent) Color(0xFF2196F3) else Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        when(order.status) {
                            OrderStatus.PENDING -> Text(if (allPricesPresent) stringResource(R.string.btn_accept_order) else stringResource(R.string.btn_view_order))
                            OrderStatus.WAITING_APPROVAL -> Text(stringResource(R.string.btn_view_quote))
                            OrderStatus.PREPARING -> Text(stringResource(R.string.btn_send_delivery))
                            OrderStatus.DELIVERING -> Text(stringResource(R.string.btn_arrived_destination))
                            OrderStatus.WAITING_CLIENT_CONFIRMATION -> Text(stringResource(R.string.btn_finish_delivery))
                            else -> Text(stringResource(R.string.btn_manage))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeCount(count: Int) {
    Box(
        modifier = Modifier
            .background(Color.Red, CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = if(count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}