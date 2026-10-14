package com.domifiao.domifiao.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Extensiones útiles para la aplicación Domifiao
 */

// ============================================
// EXTENSIONES DE STRING
// ============================================

/**
 * Capitaliza la primera letra de cada palabra
 */
fun String.capitalizeWords(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}

/**
 * Verifica si el string es un email válido
 */
fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

/**
 * Verifica si el string es un teléfono dominicano válido (10 dígitos)
 */
fun String.isValidDominicanPhone(): Boolean {
    val digits = this.filter { it.isDigit() }
    return digits.length == 10 && (digits.startsWith("809") || digits.startsWith("829") || digits.startsWith("849"))
}

/**
 * Formatea un número dominicano para Firebase Phone Auth
 * Asegura que el formato sea correcto para todos los códigos de área (809, 829, 849)
 */
fun String.toFirebasePhoneNumber(): String {
    val digits = this.filter { it.isDigit() }
    // República Dominicana usa el código de país +1 (North American Numbering Plan)
    // Formato: +1XXXXXXXXXX (donde XXXXXXXXXX son los 10 dígitos)
    return if (digits.length == 10) {
        "+1$digits"
    } else if (digits.startsWith("1") && digits.length == 11) {
        "+$digits"
    } else if (digits.startsWith("+1")) {
        digits
    } else {
        "+1$digits"
    }
}

/**
 * Verifica si el string es una cédula dominicana válida (11 dígitos)
 */
fun String.isValidDominicanCedula(): Boolean {
    val digits = this.filter { it.isDigit() }
    return digits.length == 11
}

/**
 * Formatea como teléfono dominicano (809-555-1234)
 */
fun String.formatAsPhone(): String {
    val digits = this.filter { it.isDigit() }
    return when {
        digits.length >= 10 -> "${digits.substring(0,3)}-${digits.substring(3,6)}-${digits.substring(6,10)}"
        digits.length >= 7 -> "${digits.substring(0,3)}-${digits.substring(3,7)}"
        else -> digits
    }
}

/**
 * Formatea como cédula dominicana (001-1234567-8)
 */
fun String.formatAsCedula(): String {
    val digits = this.filter { it.isDigit() }
    return when {
        digits.length >= 11 -> "${digits.substring(0,3)}-${digits.substring(3,10)}-${digits.substring(10,11)}"
        digits.length >= 10 -> "${digits.substring(0,3)}-${digits.substring(3,10)}"
        else -> digits
    }
}

/**
 * Trunca con elipsis
 */
fun String.truncate(maxLength: Int): String {
    return if (this.length > maxLength) {
        this.take(maxLength - 3) + "..."
    } else {
        this
    }
}

/**
 * Limpia espacios extras
 */
fun String.cleanSpaces(): String {
    return this.trim().replace(Regex("\\s+"), " ")
}

// ============================================
// EXTENSIONES DE INT/LONG (MONEDA Y TIEMPO)
// ============================================

/**
 * Formatea como moneda dominicana
 */
fun Int.toCurrency(): String {
    val format = NumberFormat.getNumberInstance(Locale("es", "DO"))
    return "RD$ ${format.format(this)}"
}

fun Double.toCurrency(): String {
    val format = NumberFormat.getNumberInstance(Locale("es", "DO"))
    return "RD$ ${format.format(this.toInt())}"
}

/**
 * Formatea timestamp como fecha
 */
fun Long.toDateString(): String {
    if (this == 0L) return ""
    val format = SimpleDateFormat("dd/MM/yyyy", Locale("es", "DO"))
    return format.format(Date(this))
}

/**
 * Formatea timestamp como hora
 */
fun Long.toTimeString(): String {
    if (this == 0L) return ""
    val format = SimpleDateFormat("hh:mm a", Locale("es", "DO"))
    return format.format(Date(this))
}

/**
 * Formatea timestamp como fecha y hora
 */
fun Long.toDateTimeString(): String {
    if (this == 0L) return ""
    val format = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale("es", "DO"))
    return format.format(Date(this))
}

/**
 * Formatea como tiempo relativo (hace 5 min, hace 2 horas)
 */
fun Long.toRelativeTime(): String {
    if (this == 0L) return ""

    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Ahora"
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
            "Hace $mins min"
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            "Hace $hours h"
        }
        diff < TimeUnit.DAYS.toMillis(2) -> "Ayer"
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "Hace $days días"
        }
        else -> this.toDateString()
    }
}

/**
 * Verifica si el timestamp es de hoy
 */
fun Long.isToday(): Boolean {
    if (this == 0L) return false
    val messageDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(this))
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    return messageDate == today
}

// ============================================
// EXTENSIONES DE CONTEXT
// ============================================

/**
 * Muestra un Toast corto
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * Muestra un Toast largo
 */
fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

// ============================================
// EXTENSIONES DE COLOR
// ============================================

/**
 * Aclara un color
 */
fun Color.lighten(factor: Float = 0.2f): Color {
    return Color(
        red = (red + (1 - red) * factor).coerceIn(0f, 1f),
        green = (green + (1 - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Oscurece un color
 */
fun Color.darken(factor: Float = 0.2f): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

// ============================================
// EXTENSIONES DE COLLECTIONS
// ============================================

/**
 * Agrupa una lista por fecha (para listas de mensajes, transacciones, etc.)
 */
fun <T> List<T>.groupByDate(getTimestamp: (T) -> Long): Map<String, List<T>> {
    val format = SimpleDateFormat("dd/MM/yyyy", Locale("es", "DO"))
    return this.groupBy { format.format(Date(getTimestamp(it))) }
}

/**
 * Filtra elementos nulos de una lista de manera segura
 */
fun <T> List<T?>.filterNotNullSafe(): List<T> {
    return this.filterNotNull()
}