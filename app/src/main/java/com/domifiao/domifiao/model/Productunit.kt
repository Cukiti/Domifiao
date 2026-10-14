package com.domifiao.domifiao.model

import android.annotation.SuppressLint

/**
 * Sistema de Unidades de Medida para Domifiao
 * Adaptado a la forma real de comprar en colmados dominicanos.
 *
 * Para el COLMADO hay solo 2 opciones:
 * - UNIDAD: Productos que se venden por unidad (pan, huevos, latas, etc.)
 * - LIBRA: Productos que se pesan (queso, salami, pollo, etc.)
 *          El cliente puede elegir pedir "por libra" o "al detalle" (por precio)
 */

/**
 * Opciones de libras disponibles para el cliente
 */
enum class LibraOption(val value: Double, val displayName: String) {
    CUARTA(0.25, "¼ Libra"),
    MEDIA(0.5, "½ Libra"),
    TRES_CUARTOS(0.75, "¾ Libra"),
    UNA(1.0, "1 Libra"),
    UNA_Y_CUARTA(1.25, "1¼ Libras"),
    UNA_Y_MEDIA(1.5, "1½ Libras"),
    DOS(2.0, "2 Libras"),
    DOS_Y_MEDIA(2.5, "2½ Libras"),
    TRES(3.0, "3 Libras"),
    CUATRO(4.0, "4 Libras"),
    CINCO(5.0, "5 Libras")
}

/**
 * Opciones de precio "al detalle" para el cliente
 */
enum class DetalleOption(val value: Int, val displayName: String) {
    VEINTICINCO(25, "RD$ 25"),
    CINCUENTA(50, "RD$ 50"),
    SETENTA_Y_CINCO(75, "RD$ 75"),
    CIEN(100, "RD$ 100"),
    CIENTO_CINCUENTA(150, "RD$ 150"),
    DOSCIENTOS(200, "RD$ 200"),
    DOSCIENTOS_CINCUENTA(250, "RD$ 250"),
    TRESCIENTOS(300, "RD$ 300"),
    CUATROCIENTOS(400, "RD$ 400"),
    QUINIENTOS(500, "RD$ 500")
}

/**
 * Utilidades para el sistema de unidades
 */
object UnitSystemUtils {

    /**
     * Estima la cantidad en libras dado un precio y el precio por libra
     */
    fun estimateLibrasFromPrice(price: Int, pricePerLibra: Double): Double {
        if (pricePerLibra <= 0) return 0.0
        return price / pricePerLibra
    }

    /**
     * Formatea la cantidad estimada de libras
     */
    @SuppressLint("DefaultLocale")
    fun formatEstimatedLibras(libras: Double): String {
        return when {
            libras < 0.1 -> "menos de ⅛ lb"
            libras < 0.2 -> "aprox. ⅛ lb"
            libras < 0.3 -> "aprox. ¼ lb"
            libras < 0.4 -> "aprox. ⅓ lb"
            libras < 0.6 -> "aprox. ½ lb"
            libras < 0.8 -> "aprox. ¾ lb"
            libras < 1.1 -> "aprox. 1 lb"
            libras < 1.3 -> "aprox. 1¼ lb"
            libras < 1.6 -> "aprox. 1½ lb"
            libras < 2.1 -> "aprox. 2 lb"
            else -> "aprox. ${String.format("%.1f", libras)} lb"
        }
    }

}

// ============================================
// EXTENSIONES PARA COMPATIBILIDAD
// ============================================

