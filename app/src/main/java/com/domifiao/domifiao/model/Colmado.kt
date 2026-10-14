package com.domifiao.domifiao.model

/**
 * Modelo de datos para un Colmado.
 * Representa la información completa de un colmado registrado en la plataforma.
 */
data class Colmado(
    val id: String,
    val name: String,
    val phone: String = "",
    val province: String,
    val municipality: String = "",
    val address: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val photoUrl: String,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val isOpen: Boolean = true,

    // Campos de identidad
    val ownerName: String = "",
    val ownerCedula: String = "",
    val isIdentityVerified: Boolean = false,

    // Configuracion del negocio
    val deliveryFee: Int = 0,
    val hasDeliveryService: Boolean = true,
    val isAcceptingCredit: Boolean = true,
    val isHiringDelivery: Boolean = false,
    val hasLoyaltyProgram: Boolean = false
) {

    companion object
}