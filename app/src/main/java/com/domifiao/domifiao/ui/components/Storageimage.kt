package com.domifiao.domifiao.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * StorageImage - Componente universal para cargar imágenes
 *
 * Soporta:
 * - URLs de Firebase Storage (nuevas imágenes)
 * - Strings Base64 (imágenes antiguas/compatibilidad)
 * - URLs http/https normales
 *
 * Uso:
 * StorageImage(
 *     imageData = user.photoUrl,  // URL o Base64
 *     contentDescription = "Foto de perfil",
 *     modifier = Modifier.size(100.dp)
 * )
 */
@Composable
fun StorageImage(
    imageData: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: ImageVector = Icons.Default.Image,
    errorIcon: ImageVector = Icons.Default.BrokenImage,
    placeholderColor: Color = Color(0xFFE0E0E0),
    iconTint: Color = Color.Gray
) {
    when {
        imageData.isEmpty() -> {
            // Sin imagen - mostrar placeholder
            PlaceholderBox(
                icon = placeholderIcon,
                modifier = modifier,
                backgroundColor = placeholderColor,
                iconTint = iconTint
            )
        }

        isBase64String(imageData) -> {
            // Imagen en Base64 (compatibilidad con datos antiguos)
            Base64ImageView(
                base64String = imageData,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                errorIcon = errorIcon,
                placeholderColor = placeholderColor,
                iconTint = iconTint
            )
        }

        else -> {
            // URL (Firebase Storage u otra)
            UrlImageView(
                url = imageData,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                placeholderIcon = placeholderIcon,
                errorIcon = errorIcon,
                placeholderColor = placeholderColor,
                iconTint = iconTint
            )
        }
    }
}

/**
 * ProfileImage - Versión especializada para fotos de perfil
 */
@Composable
fun ProfileImage(
    imageData: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    StorageImage(
        imageData = imageData,
        contentDescription = "Foto de perfil",
        modifier = modifier,
        contentScale = contentScale,
        placeholderIcon = Icons.Default.Person,
        placeholderColor = Color(0xFFE3F2FD),
        iconTint = Color(0xFF1976D2)
    )
}

/**
 * ColmadoImage - Versión especializada para logos de colmados
 */
@Composable
fun ColmadoImage(
    imageData: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    StorageImage(
        imageData = imageData,
        contentDescription = "Foto del colmado",
        modifier = modifier,
        contentScale = contentScale,
        placeholderIcon = Icons.Default.Store,
        placeholderColor = Color(0xFFE8F5E9),
        iconTint = Color(0xFF4CAF50)
    )
}

/**
 * ProductImage - Versión especializada para productos
 */
@Composable
fun ProductImage(
    imageData: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    StorageImage(
        imageData = imageData,
        contentDescription = "Foto del producto",
        modifier = modifier,
        contentScale = contentScale,
        placeholderIcon = Icons.Default.Image,
        placeholderColor = Color(0xFFF5F5F5),
        iconTint = Color.Gray
    )
}

// ==========================================
// COMPONENTES INTERNOS
// ==========================================

@Composable
private fun PlaceholderBox(
    icon: ImageVector,
    modifier: Modifier,
    backgroundColor: Color,
    iconTint: Color
) {
    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun UrlImageView(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    placeholderIcon: ImageVector,
    errorIcon: ImageVector,
    placeholderColor: Color,
    iconTint: Color
) {
    val context = LocalContext.current

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(placeholderColor),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = iconTint
                )
            }
        },
        error = {
            PlaceholderBox(
                icon = errorIcon,
                modifier = Modifier.matchParentSize(),
                backgroundColor = placeholderColor,
                iconTint = Color(0xFFE57373)
            )
        }
    )
}

@Composable
private fun Base64ImageView(
    base64String: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    errorIcon: ImageVector,
    placeholderColor: Color,
    iconTint: Color
) {
    val bitmap = remember(base64String) {
        try {
            val cleanBase64 = base64String
                .replace("data:image/jpeg;base64,", "")
                .replace("data:image/png;base64,", "")
                .replace("\n", "")
                .replace(" ", "")

            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        PlaceholderBox(
            icon = errorIcon,
            modifier = modifier,
            backgroundColor = placeholderColor,
            iconTint = Color(0xFFE57373)
        )
    }
}

// ==========================================
// UTILIDADES
// ==========================================

/**
 * Determina si un string es Base64.
 */
private fun isBase64String(data: String): Boolean {
    if (data.isEmpty()) return false

    // Si empieza con http, es URL
    if (data.startsWith("http://") || data.startsWith("https://")) return false

    // Si tiene prefijo data:image, es Base64
    if (data.startsWith("data:image")) return true

    // Si es muy largo y no contiene caracteres de URL, probablemente es Base64
    return data.length > 100 && !data.contains("/") && !data.contains(".")
}