package com.domifiao.domifiao.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.domifiao.domifiao.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Utilidades para el manejo y compresión de imágenes.
 * Ayuda a mantener los documentos de Firestore dentro del límite de 1MB.
 */
object ImageUtils {

    // Constantes de configuración
    private const val MAX_IMAGE_DIMENSION = 800  // Píxeles máximos de ancho/alto
    private const val JPEG_QUALITY = 70          // Calidad de compresión (0-100)
    private const val MAX_BASE64_SIZE = 500_000  // ~500KB máximo para Base64

    /**
     * Comprime una imagen desde URI y la convierte a Base64.
     *
     * @param context Contexto de Android
     * @param uri URI de la imagen (desde galería o cámara)
     * @param maxDimension Dimensión máxima (ancho o alto)
     * @param quality Calidad JPEG (0-100)
     * @return String Base64 de la imagen comprimida, o null si falla
     */
    fun compressImageToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_IMAGE_DIMENSION,
        quality: Int = JPEG_QUALITY
    ): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Comprimir el bitmap
            val compressedBitmap = compressBitmap(originalBitmap, maxDimension)

            // Convertir a Base64
            bitmapToBase64(compressedBitmap, quality)
        } catch (e: Exception) {
            // Solo loggear detalles en modo debug
            if (BuildConfig.DEBUG) {
                Log.e("ImageUtils", "Error comprimiendo imagen", e)
            }
            null
        }
    }

    /**
     * Comprime un Bitmap existente.
     *
     * @param bitmap Bitmap original
     * @param maxDimension Dimensión máxima
     * @return Bitmap redimensionado
     */
    fun compressBitmap(bitmap: Bitmap, maxDimension: Int = MAX_IMAGE_DIMENSION): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Si ya es pequeño, no hacer nada
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        // Calcular factor de escala manteniendo proporción
        val scaleFactor = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }

        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Convierte un Bitmap a String Base64.
     *
     * @param bitmap Bitmap a convertir
     * @param quality Calidad JPEG (0-100)
     * @return String Base64
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = JPEG_QUALITY): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    /**
     * Decodifica un String Base64 a Bitmap.
     *
     * @param base64String String Base64 de la imagen
     * @return Bitmap o null si falla
     */
    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            // Solo loggear detalles en modo debug
            if (BuildConfig.DEBUG) {
                Log.e("ImageUtils", "Error decodificando Base64", e)
            }
            null
        }
    }

    /**
     * Verifica si un String Base64 es válido y no excede el tamaño máximo.
     *
     * @param base64String String Base64 a verificar
     * @param maxSize Tamaño máximo en bytes
     * @return true si es válido y está dentro del límite
     */
    fun isValidBase64Image(base64String: String, maxSize: Int = MAX_BASE64_SIZE): Boolean {
        if (base64String.isEmpty()) return false
        if (base64String.length > maxSize) return false

        return try {
            val decoded = Base64.decode(base64String, Base64.DEFAULT)
            decoded.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtiene el tamaño aproximado en KB de un String Base64.
     *
     * @param base64String String Base64
     * @return Tamaño en KB
     */
    fun getBase64SizeKB(base64String: String): Int {
        // Base64 es aproximadamente 4/3 del tamaño original
        val originalSize = (base64String.length * 3) / 4
        return originalSize / 1024
    }

    /**
     * Comprime una imagen Base64 existente si excede el tamaño máximo.
     *
     * @param base64String String Base64 original
     * @param maxSizeKB Tamaño máximo en KB
     * @param targetQuality Calidad objetivo
     * @return String Base64 comprimido o el original si ya está dentro del límite
     */
    fun recompressBase64IfNeeded(
        base64String: String,
        maxSizeKB: Int = 400,
        targetQuality: Int = 60
    ): String {
        val currentSizeKB = getBase64SizeKB(base64String)

        if (currentSizeKB <= maxSizeKB) {
            return base64String
        }

        // Decodificar, comprimir y re-codificar
        val bitmap = base64ToBitmap(base64String) ?: return base64String
        val compressed = compressBitmap(bitmap, MAX_IMAGE_DIMENSION)
        return bitmapToBase64(compressed, targetQuality)
    }
}