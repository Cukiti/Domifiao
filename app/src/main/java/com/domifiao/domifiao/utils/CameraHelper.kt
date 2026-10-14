package com.domifiao.domifiao.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CameraHelper {
    /**
     * Crea un archivo temporal para la imagen y retorna su URI.
     */
    fun createImageFile(context: Context): Pair<File, Uri> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        
        // Usar el directorio de caché configurado en file_paths.xml
        val storageDir = File(context.cacheDir, "images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        
        val imageFile = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        return Pair(imageFile, uri)
    }
}
