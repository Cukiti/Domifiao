package com.domifiao.domifiao.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OrderProgressBar(status: String, modifier: Modifier = Modifier) {
    val stages = listOf(
        "Pendiente",
        "Cotizado",
        "Preparando",
        "En camino",
        "Entregado"
    )

    val currentStageIndex = when (status) {
        "PENDING" -> 0
        "WAITING_APPROVAL" -> 1
        "PREPARING" -> 2
        "DELIVERING", "WAITING_CLIENT_CONFIRMATION" -> 3
        "COMPLETED" -> 4
        "CANCELLED" -> -1
        else -> 0
    }

    if (currentStageIndex == -1) {
        Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("PEDIDO CANCELADO", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stages.forEachIndexed { index, _ ->
                val isCompleted = index < currentStageIndex
                val isCurrent = index == currentStageIndex
                val isActive = index <= currentStageIndex

                val color by animateColorAsState(
                    targetValue = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant,
                    label = "color"
                )

                // Circle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isActive) color else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else if (isCurrent) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                    }
                }

                // Connector line
                if (index < stages.size - 1) {
                    val connectorColor by animateColorAsState(
                        targetValue = if (index < currentStageIndex) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant,
                        label = "connectorColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(connectorColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stages.forEachIndexed { index, stage ->
                val isCurrent = index == currentStageIndex
                val isActive = index <= currentStageIndex
                
                Text(
                    text = stage,
                    fontSize = 10.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
