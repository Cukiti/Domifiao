package com.domifiao.domifiao.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.domifiao.domifiao.model.ClientProduct
import com.domifiao.domifiao.model.LibraOption
import com.domifiao.domifiao.model.DetalleOption
import com.domifiao.domifiao.model.UnitSystemUtils

/**
 * Diálogo mejorado para seleccionar cantidad según el tipo de unidad del producto.
 *
 * Para productos UNIDAD: Selector simple +/-
 * Para productos LIBRA: El cliente elige entre "Por Libra" o "Al Detalle"
 */
@Composable
fun QuantitySelectionDialog(
    product: ClientProduct,
    onConfirm: (quantity: Double, quantityDisplay: String, selectionMode: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Determinar si es producto por libra o por unidad
    val isLibraProduct = product.unitType.uppercase() in listOf("LIBRA", "PRECIO", "GRAMO", "LITRO")

    // Estados
    var selectedMode by remember { mutableStateOf(if (isLibraProduct) "POR_LIBRA" else "UNIDAD") }
    var selectedQuantity by remember { mutableDoubleStateOf(if (isLibraProduct) 0.5 else 1.0) }
    var selectedDisplay by remember { mutableStateOf(if (isLibraProduct) "½ Libra" else "1") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Título del producto
                Text(
                    text = product.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Precio por unidad/libra
                Text(
                    text = if (isLibraProduct) "RD$ ${product.price.toInt()} / Libra" else "RD$ ${product.price.toInt()} c/u",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Contenido según tipo de producto
                if (isLibraProduct) {
                    // === SELECTOR PARA PRODUCTOS POR LIBRA ===
                    LibraProductSelector(
                        pricePerLibra = product.price,
                        selectedMode = selectedMode,
                        selectedQuantity = selectedQuantity,
                        selectedDisplay = selectedDisplay,
                        onModeChange = { mode ->
                            selectedMode = mode
                            // Resetear valores al cambiar modo
                            if (mode == "POR_LIBRA") {
                                selectedQuantity = 0.5
                                selectedDisplay = "½ Libra"
                            } else {
                                selectedQuantity = 50.0
                                selectedDisplay = "RD$ 50"
                            }
                        },
                        onQuantityChange = { qty, display ->
                            selectedQuantity = qty
                            selectedDisplay = display
                        }
                    )
                } else {
                    // === SELECTOR PARA PRODUCTOS POR UNIDAD ===
                    UnidadProductSelector(
                        quantity = selectedQuantity.toInt(),
                        onQuantityChange = { qty ->
                            selectedQuantity = qty.toDouble()
                            selectedDisplay = qty.toString()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Divider y Total
                HorizontalDivider(color = Color.LightGray)

                Spacer(modifier = Modifier.height(12.dp))

                // Calcular total
                val total = when (selectedMode) {
                    "AL_DETALLE" -> selectedQuantity
                    else -> product.price * selectedQuantity
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total estimado:",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        if (selectedMode == "AL_DETALLE") {
                            val estimatedLibras = UnitSystemUtils.estimateLibrasFromPrice(
                                selectedQuantity.toInt(),
                                product.price
                            )
                            Text(
                                text = UnitSystemUtils.formatEstimatedLibras(estimatedLibras),
                                fontSize = 12.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }

                    Text(
                        text = "RD$ ${total.toInt()}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            onConfirm(selectedQuantity, selectedDisplay, selectedMode)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Agregar")
                    }
                }
            }
        }
    }
}

/**
 * Selector para productos vendidos por LIBRA
 * El cliente puede elegir entre "Por Libra" o "Al Detalle"
 */
@Composable
private fun LibraProductSelector(
    pricePerLibra: Double,
    selectedMode: String,
    selectedQuantity: Double,
    selectedDisplay: String,
    onModeChange: (String) -> Unit,
    onQuantityChange: (Double, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Toggle entre modos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF5F5F5))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Botón "Por Libra"
            ModeToggleButton(
                text = "Por Libra",
                icon = Icons.Default.Scale,
                isSelected = selectedMode == "POR_LIBRA",
                onClick = { onModeChange("POR_LIBRA") },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Botón "Al Detalle"
            ModeToggleButton(
                text = "Al Detalle",
                icon = Icons.Default.AttachMoney,
                isSelected = selectedMode == "AL_DETALLE",
                onClick = { onModeChange("AL_DETALLE") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contenido según modo seleccionado
        AnimatedVisibility(
            visible = selectedMode == "POR_LIBRA",
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LibraOptionsGrid(
                selectedValue = selectedQuantity,
                onSelect = { value, display ->
                    onQuantityChange(value, display)
                }
            )
        }

        AnimatedVisibility(
            visible = selectedMode == "AL_DETALLE",
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DetalleOptionsGrid(
                selectedValue = selectedQuantity.toInt(),
                pricePerLibra = pricePerLibra,
                onSelect = { value, display ->
                    onQuantityChange(value.toDouble(), display)
                }
            )
        }
    }
}

/**
 * Botón toggle para cambiar entre modos
 */
@Composable
private fun ModeToggleButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color(0xFF4CAF50) else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.Gray

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Grid de opciones de libras
 */
@Composable
private fun LibraOptionsGrid(
    selectedValue: Double,
    onSelect: (Double, String) -> Unit
) {
    val options = LibraOption.entries.toList()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            OptionChip(
                text = option.displayName,
                isSelected = selectedValue == option.value,
                onClick = { onSelect(option.value, option.displayName) }
            )
        }
    }
}

/**
 * Grid de opciones al detalle (por precio)
 */
@Composable
private fun DetalleOptionsGrid(
    selectedValue: Int,
    pricePerLibra: Double,
    onSelect: (Int, String) -> Unit
) {
    val options = DetalleOption.entries.toList()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            val estimatedLibras = UnitSystemUtils.estimateLibrasFromPrice(option.value, pricePerLibra)

            OptionChipWithSubtext(
                text = option.displayName,
                subtext = UnitSystemUtils.formatEstimatedLibras(estimatedLibras),
                isSelected = selectedValue == option.value,
                onClick = { onSelect(option.value, option.displayName) }
            )
        }
    }
}

/**
 * Chip de opción simple
 */
@Composable
private fun OptionChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF4CAF50) else Color.White
    val borderColor = if (isSelected) Color(0xFF4CAF50) else Color.LightGray
    val textColor = if (isSelected) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Chip de opción con subtexto (para mostrar estimación de libras)
 */
@Composable
private fun OptionChipWithSubtext(
    text: String,
    subtext: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF4CAF50) else Color.White
    val borderColor = if (isSelected) Color(0xFF4CAF50) else Color.LightGray
    val textColor = if (isSelected) Color.White else Color.Black
    val subtextColor = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Gray

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = subtext,
                color = subtextColor,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Selector simple para productos por UNIDAD
 */
@Composable
private fun UnidadProductSelector(
    quantity: Int,
    onQuantityChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Cantidad",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón menos
            IconButton(
                onClick = { if (quantity > 1) onQuantityChange(quantity - 1) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5))
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Disminuir",
                    tint = if (quantity > 1) Color(0xFF4CAF50) else Color.LightGray
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Cantidad
            Text(
                text = "$quantity",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Botón más
            IconButton(
                onClick = { if (quantity < 99) onQuantityChange(quantity + 1) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5))
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Aumentar",
                    tint = Color(0xFF4CAF50)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (quantity == 1) "unidad" else "unidades",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Chips de acceso rápido
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf(1, 2, 3, 5, 10, 12)) { value ->
                QuickSelectChip(
                    value = value,
                    isSelected = quantity == value,
                    onClick = { onQuantityChange(value) }
                )
            }
        }
    }
}

/**
 * Chip de selección rápida para unidades
 */
@Composable
private fun QuickSelectChip(
    value: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF4CAF50) else Color(0xFFF5F5F5)
    val textColor = if (isSelected) Color.White else Color.DarkGray

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$value",
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}