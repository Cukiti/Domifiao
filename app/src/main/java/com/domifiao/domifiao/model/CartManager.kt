package com.domifiao.domifiao.model

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Objeto Global (Singleton) para manejar el carrito desde cualquier pantalla.
 * Sincroniza automáticamente con Firestore para persistencia entre sesiones.
 *
 * FIX: Añadidos flags para prevenir race conditions en cargas y sincronizaciones simultáneas.
 */
object CartManager {

    // Datos del Colmado actual (Para saber a quién le compramos)
    var currentColmadoId: String? = null
    var currentColmadoName: String = ""

    // Lista de productos seleccionados (Reactiva para Compose)
    val items = mutableStateListOf<ClientProduct>()

    // Dependencias de Firebase (Lazy para que se inicien solo cuando se necesiten)
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // FIX: Flags para prevenir race conditions
    @Volatile
    private var isLoadingFromFirestore = false

    @Volatile
    private var isSyncingToFirestore = false

    private var lastSyncTimestamp = 0L
    private const val MIN_SYNC_INTERVAL_MS = 500L // Mínimo 500ms entre sincronizaciones

    /**
     * Agrega un producto al carrito o aumenta su cantidad y sincroniza con la nube.
     * Ahora soporta el nuevo sistema de unidades con selectionMode.
     *
     * FIX: Añadida validación para prevenir cambio de colmado sin confirmación.
     */
    fun addProduct(
        product: ClientProduct,
        colmadoId: String,
        colmadoName: String,
        quantity: Double = 1.0,
        quantityDisplay: String = "",
        selectionMode: String = "UNIDAD",
        onColmadoConflict: (() -> Unit)? = null
    ) {
        // 1. Validación de Colmado (Regla: No mezclar colmados)
        // FIX: Si hay conflicto, invocar callback en lugar de limpiar silenciosamente
        if (currentColmadoId != null && currentColmadoId != colmadoId) {
            if (onColmadoConflict != null) {
                onColmadoConflict()
                return  // No continuar hasta que usuario confirme
            } else {
                // Fallback: limpiar carrito (comportamiento legacy)
                clearCart()
            }
        }

        // 2. Establecer sesión actual
        currentColmadoId = colmadoId
        currentColmadoName = colmadoName

        // 3. Buscar si el producto ya existe en el carrito
        val index = items.indexOfFirst { it.id == product.id }

        if (index != -1) {
            // A. YA EXISTE: Reemplazamos (no sumamos para unidades especiales)
            items[index] = product.copy(
                quantityInCart = quantity,
                quantityDisplay = quantityDisplay,
                selectionMode = selectionMode
            )
        } else {
            // B. ES NUEVO: Lo agregamos a la lista
            val newProduct = product.copy(
                quantityInCart = quantity,
                quantityDisplay = quantityDisplay,
                selectionMode = selectionMode
            )
            items.add(newProduct)
        }

        // 4. Guardar cambios en Firestore
        syncToFirestore()
    }

    /**
     * Disminuye la cantidad o elimina el producto si llega a 0.
     */
    fun removeProduct(product: ClientProduct) {
        val index = items.indexOfFirst { it.id == product.id }

        if (index != -1) {
            val currentItem = items[index]

            if (currentItem.quantityInCart > 1) {
                // Restar 1
                val newQuantity = currentItem.quantityInCart - 1
                items[index] = currentItem.copy(quantityInCart = newQuantity)
            } else {
                // Eliminar del todo
                items.removeAt(index)
            }
        }

        // Si el carrito queda vacío, reseteamos la sesión del colmado
        if (items.isEmpty()) {
            currentColmadoId = null
            currentColmadoName = ""
        }

        // Guardar cambios
        syncToFirestore()
    }

    /**
     * Calcula el total a pagar.
     */
    fun getTotal(): Double {
        return items.sumOf { it.calculateTotalPrice() }
    }

    /**
     * Método de compatibilidad: removeItem
     * Wrapper para removeProduct por ID
     */
    fun removeItem(productId: String) {
        val product = items.find { it.id == productId }
        if (product != null) {
            removeProduct(product)
        }
    }

    /**
     * Limpia todo el carrito.
     */
    fun clearCart() {
        items.clear()
        currentColmadoId = null
        currentColmadoName = ""
        syncToFirestore()
    }

    // --- PERSISTENCIA EN FIREBASE ---

    /**
     * Guarda el estado actual del carrito en Firestore.
     * RUTA: users/{uid}/cart/active_cart (Coincide con reglas de seguridad)
     *
     * FIX: Añadido throttling para evitar demasiadas escrituras simultáneas
     */
    private fun syncToFirestore(userIdParam: String? = null, force: Boolean = false) {
        // FIX: Prevenir sincronizaciones simultáneas
        if (isSyncingToFirestore && !force) {
            Log.d("CartManager", "Sincronización ya en progreso, saltando...")
            return
        }

        // FIX: Throttling - Evitar sincronizar más de una vez cada 500ms
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncTimestamp) < MIN_SYNC_INTERVAL_MS) {
            Log.d("CartManager", "Throttling: esperando antes de sincronizar")
            return
        }

        isSyncingToFirestore = true
        lastSyncTimestamp = now

        val userId = userIdParam ?: auth.currentUser?.uid
        if (userId == null) {
            isSyncingToFirestore = false
            return
        }

        // Preparamos los datos para guardar
        val cartData = hashMapOf(
            "colmadoId" to currentColmadoId,
            "colmadoName" to currentColmadoName,
            "updatedAt" to System.currentTimeMillis(),
            "items" to items.map { prod ->
                mapOf(
                    "id" to prod.id,
                    "name" to prod.name,
                    "price" to prod.price,
                    "quantity" to prod.quantityInCart,
                    "quantityDisplay" to prod.quantityDisplay,
                    "selectionMode" to prod.selectionMode,
                    "category" to prod.category,
                    "unitType" to prod.unitType,
                    "photoUrl" to prod.photoUrl
                )
            }
        )

        // Usamos la subcolección del usuario para cumplir con las reglas de seguridad
        db.collection("users").document(userId).collection("cart").document("active_cart")
            .set(cartData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("CartManager", "Carrito sincronizado exitosamente")
                isSyncingToFirestore = false
            }
            .addOnFailureListener { e ->
                Log.e("CartManager", "Error guardando carrito", e)
                isSyncingToFirestore = false
            }
    }

    /**
     * Carga el carrito desde Firestore al iniciar la app.
     *
     * FIX: Mejorado para prevenir race conditions con flag isLoadingFromFirestore
     */
    fun loadFromFirestore(userIdParam: String? = null, onLoaded: () -> Unit = {}) {
        // FIX: Prevenir múltiples cargas simultáneas
        if (isLoadingFromFirestore) {
            Log.d("CartManager", "Ya hay una carga en progreso")
            onLoaded()
            return
        }

        val userId = userIdParam ?: auth.currentUser?.uid
        if (userId == null) {
            onLoaded()
            return
        }

        // Si ya hay items, no sobrescribimos (evita race condition)
        if (items.isNotEmpty()) {
            Log.d("CartManager", "Carrito ya tiene items, saltando carga")
            onLoaded()
            return
        }

        isLoadingFromFirestore = true

        db.collection("users").document(userId).collection("cart").document("active_cart")
            .get()
            .addOnSuccessListener { document ->
                try {
                    if (document.exists()) {
                        val cId = document.getString("colmadoId")
                        val cName = document.getString("colmadoName")

                        @Suppress("UNCHECKED_CAST")
                        val rawItems = document.get("items") as? List<Map<String, Any>> ?: emptyList()

                        // FIX: Double-check que la lista sigue vacía antes de restaurar
                        if (cId != null && rawItems.isNotEmpty() && items.isEmpty()) {
                            currentColmadoId = cId
                            currentColmadoName = cName ?: ""

                            rawItems.forEach { map ->
                                try {
                                    // Recuperación robusta de foto
                                    val photo = (map["photoUrl"] ?: map["photoBase64"] as? String)
                                        ?: (map["photoUrl"] as? String)
                                        ?: ""

                                    // Obtener unitType y selectionMode
                                    val unitType = map["unitType"] as? String ?: "UNIDAD"
                                    val selectionMode = map["selectionMode"] as? String
                                        ?: if (unitType == "LIBRA") "POR_LIBRA" else "UNIDAD"

                                    val prod = ClientProduct(
                                        id = map["id"] as? String ?: "",
                                        name = map["name"] as? String ?: "Producto",
                                        price = (map["price"] as? Number)?.toDouble() ?: 0.0,
                                        category = map["category"] as? String ?: "",
                                        unitType = unitType,
                                        photoUrl = photo.toString(),
                                        quantityInCart = (map["quantity"] as? Number)?.toDouble() ?: 0.0,
                                        quantityDisplay = map["quantityDisplay"] as? String ?: "",
                                        selectionMode = selectionMode
                                    )
                                    items.add(prod)
                                } catch (e: Exception) {
                                    Log.e("CartManager", "Error parseando producto", e)
                                }
                            }
                            Log.d("CartManager", "Carrito restaurado: ${items.size} items")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CartManager", "Error procesando documento del carrito", e)
                } finally {
                    isLoadingFromFirestore = false
                    onLoaded()
                }
            }
            .addOnFailureListener {
                Log.e("CartManager", "Error cargando carrito", it)
                isLoadingFromFirestore = false
                onLoaded()
            }
    }

    /**
     * FIX: Método para forzar sincronización inmediata (útil antes de checkout)
     */
    fun forceSyncToFirestore() {
        syncToFirestore(force = true)
    }

    /**
     * FIX: Método para verificar si hay un conflicto de colmado
     */
    fun hasColmadoConflict(newColmadoId: String): Boolean {
        return currentColmadoId != null && currentColmadoId != newColmadoId && items.isNotEmpty()
    }
}
