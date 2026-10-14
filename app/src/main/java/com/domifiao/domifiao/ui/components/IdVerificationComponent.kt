package com.domifiao.domifiao.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

@Composable
fun IdVerificationComponent(
    photoUri: Uri?,
    onPhotoSelected: (Uri?) -> Unit,
    isAnalyzing: Boolean,
    verificationStatus: String?,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    isProfileContext: Boolean = false // Para cambiar texto si es Registro o Perfil
) {
    val context = LocalContext.current
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher de Cámara
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val uri = tempPhotoUri
            if (success && uri != null) {
                onPhotoSelected(uri)
            }
        }
    )

    // Función para abrir cámara
    val launchCamera = {
        try {
            // Usar directorio 'documents' en cache interno (seguro y configurado en file_paths.xml)
            val storageDir = File(context.cacheDir, "documents")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            
            val photoFile = File.createTempFile("ID_", ".jpg", storageDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al crear archivo de foto", Toast.LENGTH_SHORT).show()
        }
    }

    // Permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) launchCamera() else Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier) {
        if (!isProfileContext) {
            Text("Verificación de Identidad (Opcional)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Sube tu cédula para validar tu identidad y acceder a mejores créditos.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Área de la Foto
        Box(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (verificationStatus == "SUCCESS" || verificationStatus == "¡Verificación Exitosa!") Color(0xFFE8F5E9) else Color.LightGray)
                .clickable {
                    val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    if (perm == PackageManager.PERMISSION_GRANTED) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isAnalyzing) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.DarkGray)
                    Text("Tocar para tomar foto", color = Color.DarkGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Estado de la Verificación
        if (verificationStatus != null) {
            val (icon, color, text) = when (verificationStatus) {
                "SUCCESS", "¡Verificación Exitosa!" -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "¡Identidad Verificada!")
                "PARTIAL" -> Triple(Icons.Default.Warning, Color(0xFFFF9800), "Lectura parcial (Aceptable)")
                else -> Triple(Icons.Default.Error, Color.Red, "Datos no coinciden o foto borrosa")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text, color = color, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón Principal
        Button(
            onClick = onFinish,
            enabled = !isAnalyzing && verificationStatus != "FAILED" && verificationStatus != "Verificación Fallida" && photoUri != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text(if (isProfileContext) "Confirmar Verificación" else "Finalizar Registro (Verificado)")
        }

        // Botón Secundario (Solo en registro)
        if (!isProfileContext) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700), // Amarillo/Oro
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Omitir verificación por ahora", fontWeight = FontWeight.Bold)
            }
        }
    }
}