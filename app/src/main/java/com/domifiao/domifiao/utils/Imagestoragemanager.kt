package com.domifiao.domifiao.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * ImageStorageManager - Gestión de imágenes con Firebase Storage
 *
 * Reemplaza el almacenamiento en Base64 (Firestore) por URLs de Firebase Storage.
 *
 * Ventajas:
 * - Reduce tamaño de documentos Firestore (límite 1MB)
 * - Mejor rendimiento de carga
 * - CDN de Google para entrega rápida
 * - Caché automático con Coil
 *
 * Estructura de carpetas en Storage:
 * /users/{userId}/profile.jpg
 * /users/{userId}/house.jpg
 * /users/{userId}/cedula.jpg
 * /colmados/{colmadoId}/logo.jpg
 * /colmados/{colmadoId}/cedula.jpg
 * /colmados/{colmadoId}/products/{productId}.jpg
 */
object ImageStorageManager {

    private const val TAG = "ImageStorageManager"
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference

    // Calidad de compresión JPEG (0-100)
    private const val COMPRESSION_QUALITY = 75

    // Tamaño máximo de imagen (ancho/alto)
    private const val MAX_IMAGE_SIZE = 1024

    /**
     * Tipos de imagen soportados
     */
    enum class ImageType(val folder: String, val filename: String) {
        USER_PROFILE("users", "profile.jpg"),
        USER_HOUSE("users", "house.jpg"),
        USER_CEDULA("users", "cedula.jpg"),
        COLMADO_LOGO("colmados", "logo.jpg"),
        COLMADO_CEDULA("colmados", "cedula.jpg"),
        COLMADO_PRODUCT("colmados", "products"),
        DEFAULT_PRODUCT("admin", "default_products")
    }

    // ==========================================
    // SUBIR IMÁGENES
    // ==========================================

    /**
     * Sube una imagen desde URI y retorna la URL de descarga.
     *
     * @param context Contexto de Android
     * @param imageUri URI de la imagen local
     * @param ownerId ID del usuario o colmado
     * @param imageType Tipo de imagen
     * @param productId ID del producto (solo para COLMADO_PRODUCT)
     * @return URL de descarga o null si falla
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        ownerId: String,
        imageType: ImageType,
        productId: String? = null
    ): String? {
        return try {
            // 1. Leer y comprimir imagen
            val compressedBytes = compressImage(context, imageUri)
            if (compressedBytes == null) {
                Log.e(TAG, "Error al comprimir imagen")
                return null
            }

            // 2. Determinar ruta en Storage
            val path = buildStoragePath(ownerId, imageType, productId)
            val imageRef = storageRef.child(path)

            // 3. Subir a Firebase Storage
            val uploadTask = imageRef.putBytes(compressedBytes).await()

            // 4. Obtener URL de descarga
            val downloadUrl = imageRef.downloadUrl.await()

            Log.d(TAG, "Imagen subida: $path -> $downloadUrl")
            downloadUrl.toString()

        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo imagen", e)
            null
        }
    }

    /**
     * Sube una imagen desde Bitmap y retorna la URL de descarga.
     */
    suspend fun uploadBitmap(
        bitmap: Bitmap,
        ownerId: String,
        imageType: ImageType,
        productId: String? = null
    ): String? {
        return try {
            // 1. Comprimir bitmap
            val compressedBytes = compressBitmap(bitmap)

            // 2. Determinar ruta en Storage
            val path = buildStoragePath(ownerId, imageType, productId)
            val imageRef = storageRef.child(path)

            // 3. Subir a Firebase Storage
            imageRef.putBytes(compressedBytes).await()

            // 4. Obtener URL de descarga
            val downloadUrl = imageRef.downloadUrl.await()

            Log.d(TAG, "Bitmap subido: $path -> $downloadUrl")
            downloadUrl.toString()

        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo bitmap", e)
            null
        }
    }

    /**
     * Sube múltiples imágenes de productos.
     *
     * @return Map de productId -> URL
     */
    suspend fun uploadProductImages(
        context: Context,
        colmadoId: String,
        images: Map<String, Uri>
    ): Map<String, String> {
        val results = mutableMapOf<String, String>()

        for ((productId, uri) in images) {
            val url = uploadImage(context, uri, colmadoId, ImageType.COLMADO_PRODUCT, productId)
            if (url != null) {
                results[productId] = url
            }
        }

        return results
    }

    // ==========================================
    // ELIMINAR IMÁGENES
    // ==========================================

    /**
     * Elimina una imagen de Storage.
     */
    suspend fun deleteImage(
        ownerId: String,
        imageType: ImageType,
        productId: String? = null
    ): Boolean {
        return try {
            val path = buildStoragePath(ownerId, imageType, productId)
            storageRef.child(path).delete().await()
            Log.d(TAG, "Imagen eliminada: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando imagen", e)
            false
        }
    }

    /**
     * Elimina todas las imágenes de un usuario o colmado.
     */
    suspend fun deleteAllImages(ownerId: String, isColmado: Boolean): Boolean {
        return try {
            val folder = if (isColmado) "colmados/$ownerId" else "users/$ownerId"
            val folderRef = storageRef.child(folder)

            val items = folderRef.listAll().await()
            for (item in items.items) {
                item.delete().await()
            }

            // Eliminar subcarpetas (productos)
            for (prefix in items.prefixes) {
                val subItems = prefix.listAll().await()
                for (item in subItems.items) {
                    item.delete().await()
                }
            }

            Log.d(TAG, "Todas las imágenes eliminadas: $folder")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando todas las imágenes", e)
            false
        }
    }

    // ==========================================
    // UTILIDADES
    // ==========================================

    /**
     * Construye la ruta en Storage para una imagen.
     */
    private fun buildStoragePath(
        ownerId: String,
        imageType: ImageType,
        productId: String?
    ): String {
        return when (imageType) {
            ImageType.USER_PROFILE -> "users/$ownerId/profile.jpg"
            ImageType.USER_HOUSE -> "users/$ownerId/house.jpg"
            ImageType.USER_CEDULA -> "users/$ownerId/cedula.jpg"
            ImageType.COLMADO_LOGO -> "colmados/$ownerId/logo.jpg"
            ImageType.COLMADO_CEDULA -> "colmados/$ownerId/cedula.jpg"
            ImageType.COLMADO_PRODUCT -> {
                val id = productId ?: UUID.randomUUID().toString()
                "colmados/$ownerId/products/$id.jpg"
            }
            ImageType.DEFAULT_PRODUCT -> {
                val id = productId ?: UUID.randomUUID().toString()
                "admin/default_products/$id.jpg"
            }
        }
    }

    /**
     * Comprime una imagen desde URI.
     */
    private fun compressImage(context: Context, imageUri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            compressBitmap(originalBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error comprimiendo imagen", e)
            null
        }
    }

    /**
     * Comprime un Bitmap a JPEG.
     */
    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        // Redimensionar si es muy grande
        val scaledBitmap = if (bitmap.width > MAX_IMAGE_SIZE || bitmap.height > MAX_IMAGE_SIZE) {
            val ratio = minOf(
                MAX_IMAGE_SIZE.toFloat() / bitmap.width,
                MAX_IMAGE_SIZE.toFloat() / bitmap.height
            )
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else {
            bitmap
        }

        // Comprimir a JPEG
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)

        return outputStream.toByteArray()
    }

    /**
     * Verifica si una URL es de Firebase Storage.
     */
    fun isStorageUrl(url: String): Boolean {
        return url.contains("firebasestorage.googleapis.com") ||
                url.contains("storage.googleapis.com")
    }

    /**
     * Verifica si un string es Base64 (para compatibilidad con datos antiguos).
     */
    fun isBase64(data: String): Boolean {
        if (data.isEmpty()) return false
        if (data.startsWith("http://") || data.startsWith("https://")) return false

        // Base64 típicamente es largo y no contiene espacios
        return data.length > 100 && !data.contains(" ") && !data.contains("/")
    }

    /**
     * Genera un ID único para producto.
     */
    fun generateProductId(): String = UUID.randomUUID().toString()
}