package com.domifiao.domifiao.model

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Modelo del Producto Global.
 * Representa un producto del catálogo maestro que puede ser usado por cualquier colmado.
 */
data class GlobalProduct(
    val barcode: String = "",       // El código será el ID del documento
    val name: String = "",
    val category: String = "",
    val photoUrl: String = "",
    val searchName: String = "",    // Para búsqueda por nombre (lowercase)
    val unitType: String = "UNIDAD", // UNIDAD, PRECIO, LIBRA, GRAMO, LITRO, PAQUETE
    val suggestedUnit: String = "UNIDAD" // Unidad sugerida para este producto
)

/**
 * Utilidad para sembrar el catálogo de productos dominicanos en Firestore.
 * Solo se usa una vez para inicializar la base de datos.
 */
object ProductSeeder {

    // LISTA DE PRODUCTOS COMUNES DOMINICANOS
    private val dominicanProducts = listOf(
        // --- BEBIDAS ALCOHÓLICAS ---
        GlobalProduct("7460000000001", "Cerveza Presidente 650ml (Grande)", "Alcohol"),
        GlobalProduct("7460000000002", "Cerveza Presidente Pequeña", "Alcohol"),
        GlobalProduct("7460000000003", "Cerveza Presidente Light 650ml", "Alcohol"),
        GlobalProduct("7460000000004", "Ron Brugal Extra Viejo 700ml", "Alcohol"),
        GlobalProduct("7460000000005", "Ron Brugal XV", "Alcohol"),
        GlobalProduct("7460000000006", "Ron Barceló Gran Añejo", "Alcohol"),
        GlobalProduct("7460000000007", "Whisky Black Label 12 Años", "Alcohol"),

        // --- BEBIDAS NO ALCOHÓLICAS ---
        GlobalProduct("7460000000010", "Refresco Coca-Cola 2L", "Bebidas"),
        GlobalProduct("7460000000011", "Refresco Coca-Cola 500ml", "Bebidas"),
        GlobalProduct("7460000000012", "Refresco Country Club Merengue 2L", "Bebidas"),
        GlobalProduct("7460000000013", "Refresco Country Club Frambuesa 2L", "Bebidas"),
        GlobalProduct("7460000000014", "Agua Planeta Azul 500ml", "Bebidas"),
        GlobalProduct("7460000000015", "Agua Dasani 500ml", "Bebidas"),
        GlobalProduct("7460000000016", "Jugo Santal Pera 200ml", "Bebidas"),
        GlobalProduct("7460000000017", "Malta Morena Botella", "Bebidas"),

        // --- VÍVERES Y GRANOS ---
        GlobalProduct("7460000000030", "Arroz Campos 1LB", "Víveres"),
        GlobalProduct("7460000000031", "Arroz Pimco Selecto 1LB", "Víveres"),
        GlobalProduct("7460000000032", "Habichuelas Don Rodrigo Roja (Lata)", "Enlatados"),
        GlobalProduct("7460000000033", "Guandules Verdes La Famosa (Lata)", "Enlatados"),
        GlobalProduct("7460000000034", "Aceite Crisol 16oz", "Víveres"),
        GlobalProduct("7460000000035", "Aceite La Joya 64oz", "Víveres"),
        GlobalProduct("7460000000036", "Espaguetis Milano 400g", "Pastas"),
        GlobalProduct("7460000000037", "Coditos Milano 400g", "Pastas"),
        GlobalProduct("7460000000038", "Salsa de Tomate La Famosa (Lata Peq)", "Víveres"),
        GlobalProduct("7460000000039", "Sazón Completo Maggi (Sobre)", "Condimentos"),
        GlobalProduct("7460000000040", "Sopita Maggi (Cajita)", "Condimentos"),
        GlobalProduct("7460000000041", "Azúcar Crema 1LB", "Víveres"),
        GlobalProduct("7460000000042", "Sal Molida 1LB", "Víveres"),
        GlobalProduct("7460000000043", "Café Santo Domingo (Sobre)", "Víveres", "", "", "GRAMO", "GRAMO"),

        // --- EMBUTIDOS Y LÁCTEOS ---
        GlobalProduct("7460000000060", "Salami Induveca Super Especial 1LB", "Embutidos", "", "", "PRECIO", "PRECIO"),
        GlobalProduct("7460000000061", "Salami Mallita", "Embutidos", "", "", "PRECIO", "PRECIO"),
        GlobalProduct("7460000000062", "Queso Cheddar Sosúa (Lonjas)", "Lácteos", "", "", "LIBRA", "LIBRA"),
        GlobalProduct("7460000000063", "Leche Rica Listamilk 1L (Entera)", "Lácteos"),
        GlobalProduct("7460000000064", "Leche Evaporada Carnation (Lata)", "Lácteos"),
        GlobalProduct("7460000000065", "Huevos (Cartón 30 uds)", "Víveres"),

        // --- SNACKS Y PANADERÍA ---
        GlobalProduct("7460000000080", "Papitas Lays Clásicas (Grande)", "Snacks"),
        GlobalProduct("7460000000081", "Platanitos Caribes", "Snacks"),
        GlobalProduct("7460000000082", "Galletas Hatuey Soda (Paquete)", "Panadería"),
        GlobalProduct("7460000000083", "Galletas Dino Chocolate", "Snacks"),
        GlobalProduct("7460000000084", "Pan Sobao (Funda)", "Panadería", "", "", "UNIDAD", "UNIDAD"),

        // --- LIMPIEZA Y HOGAR ---
        GlobalProduct("7460000000100", "Cloro Macier 1 Galón", "Limpieza"),
        GlobalProduct("7460000000101", "Jabón de Cuaba Hispano", "Limpieza"),
        GlobalProduct("7460000000102", "Detergente Brillante (Sobre)", "Limpieza"),
        GlobalProduct("7460000000103", "Papel Higiénico Familia (Rollo)", "Limpieza"),
        GlobalProduct("7460000000104", "Mistolin Olor a Bebé", "Limpieza"),

        // --- CIGARRILLOS ---
        GlobalProduct("7460000000120", "Cigarrillos Marlboro Rojo (Cajetilla 20)", "Otros"),
        GlobalProduct("7460000000121", "Cigarrillos Nacional (Cajetilla 10)", "Otros")
    )

    /**
     * Sube la lista maestra a Firestore con campo de búsqueda.
     * @return Mensaje de éxito o error.
     */
    suspend fun injectCatalog(): String {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        var count = 0

        dominicanProducts.forEach { product ->
            val productToSave = product.copy(
                searchName = product.name.trim().lowercase()
            )

            val ref = db.collection("global_products").document(product.barcode)
            batch.set(ref, productToSave)
            count++
        }

        return try {
            batch.commit().await()
            "¡Éxito! $count productos inyectados al catálogo global."
        } catch (e: Exception) {
            "Error inyectando: ${e.message}"
        }
    }
}