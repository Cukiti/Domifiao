package com.domifiao.domifiao.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.domifiao.domifiao.navigation.AppScreens

@Composable
fun RegisterSuccessScreen(
    navController: NavController,
    isVerified: Boolean = true // Por defecto true para compatibilidad
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icono diferente segun verificacion
        Icon(
            imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isVerified) Color(0xFF4CAF50) else Color(0xFFFF9800),
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Titulo diferente segun verificacion
        Text(
            text = if (isVerified) "¡Identidad Verificada!" else "¡Cuenta Creada!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mensaje principal
        Text(
            text = if (isVerified)
                "Tu cuenta ha sido creada y tu cedula validada exitosamente."
            else
                "Tu cuenta ha sido creada correctamente.",
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mensaje secundario
        Text(
            text = if (isVerified)
                "Ya puedes empezar a pedir."
            else
                "Sin embargo, tu cedula no fue verificada.",
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isVerified) Color(0xFF4CAF50) else Color(0xFFFF9800)
        )

        // Mostrar advertencia si no esta verificado
        if (!isVerified) {
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "¿Que significa esto?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFE65100)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Sin verificacion de cedula:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color(0xFF5D4037)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column {
                        WarningItem("Los colmados veran que no estas verificado")
                        WarningItem("Sera mas dificil que te aprueben credito/fiado")
                        WarningItem("Algunos colmados podrian rechazar tus pedidos")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Puedes verificar tu cedula mas tarde desde tu perfil.",
                        fontSize = 13.sp,
                        color = Color(0xFF795548),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isVerified) 48.dp else 32.dp))

        // Boton: Ir al Home
        Button(
            onClick = {
                navController.navigate(AppScreens.ClientHome.route) {
                    popUpTo(0)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isVerified) Color(0xFF4CAF50) else Color(0xFFFF9800)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isVerified) "Ir al Inicio" else "Continuar sin verificar",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WarningItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontSize = 14.sp,
            color = Color(0xFFE65100),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF5D4037)
        )
    }
}