package com.domifiao.domifiao

import android.graphics.Bitmap
import com.domifiao.domifiao.utils.ImageUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para ImageUtils
 */
class ImageUtilsTest {

    @Test
    fun testGetBase64SizeKB_calculatesCorrectly() {
        // Base64 string de prueba (tamaño conocido)
        val base64String = "SGVsbG8gV29ybGQ=" // "Hello World" en Base64 (11 bytes original)

        val sizeKB = ImageUtils.getBase64SizeKB(base64String)

        // El resultado debería ser 0 KB (tamaño muy pequeño)
        assertEquals(0, sizeKB)
    }

    @Test
    fun testGetBase64SizeKB_largeString() {
        // String Base64 grande (simulado)
        val largeBase64 = "A".repeat(10000)  // 10000 caracteres

        val sizeKB = ImageUtils.getBase64SizeKB(largeBase64)

        // Debería ser aproximadamente 7KB (10000 * 3/4 / 1024)
        assertTrue(sizeKB > 0)
        assertTrue(sizeKB < 10)
    }

    @Test
    fun testIsValidBase64Image_emptyStringReturnsFalse() {
        assertFalse(ImageUtils.isValidBase64Image(""))
    }

    @Test
    fun testIsValidBase64Image_validStringReturnsTrue() {
        // String Base64 válido
        val validBase64 = "SGVsbG8gV29ybGQ="

        assertTrue(ImageUtils.isValidBase64Image(validBase64))
    }

    @Test
    fun testIsValidBase64Image_exceedsMaxSizeReturnsFalse() {
        // String muy grande (mayor a maxSize)
        val hugeBase64 = "A".repeat(600000)  // 600KB

        assertFalse(ImageUtils.isValidBase64Image(hugeBase64, 500000))
    }

    @Test
    fun testCompressBitmap_smallImageStaysSame() {
        // Bitmap pequeño de prueba (100x100)
        val smallBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val compressed = ImageUtils.compressBitmap(smallBitmap, 800)

        // Debería mantener el tamaño original
        assertEquals(100, compressed.width)
        assertEquals(100, compressed.height)
    }

    @Test
    fun testBitmapToBase64_returnsNonEmptyString() {
        // Bitmap de prueba
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)

        val base64 = ImageUtils.bitmapToBase64(bitmap)

        // Debería retornar un string no vacío
        assertTrue(base64.isNotEmpty())
    }

    @Test
    fun testBase64ToBitmap_validStringReturnsBitmap() {
        // Crear bitmap original
        val originalBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        // Convertir a Base64
        val base64 = ImageUtils.bitmapToBase64(originalBitmap)

        // Convertir de vuelta a Bitmap
        val decodedBitmap = ImageUtils.base64ToBitmap(base64)

        // Debería retornar un bitmap válido
        assertNotNull(decodedBitmap)
        assertEquals(100, decodedBitmap!!.width)
        assertEquals(100, decodedBitmap.height)
    }

    @Test
    fun testBase64ToBitmap_invalidStringReturnsNull() {
        val invalidBase64 = "esto_no_es_base64_valido!!!"

        val bitmap = ImageUtils.base64ToBitmap(invalidBase64)

        assertNull(bitmap)
    }
}
