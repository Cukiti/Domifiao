package com.domifiao.domifiao.ui.components

import java.text.Normalizer
import java.util.Locale

// Mapa completo de Provincias y sus Municipios
val dominicanProvinces = mapOf(
    "Azua" to listOf("Azua de Compostela", "Estebanía", "Guayabal", "Las Charcas", "Las Yayas de Viajama", "Padre Las Casas", "Peralta", "Pueblo Viejo", "Sabana Yegua", "Tábara Arriba"),
    "Baoruco" to listOf("Neiba", "Galván", "Los Ríos", "Tamayo", "Villa Jaragua"),
    "Barahona" to listOf("Barahona", "Cabral", "El Peñón", "Enriquillo", "Fundación", "Jaquimeyes", "La Ciénaga", "Las Salinas", "Paraíso", "Polo", "Vicente Noble"),
    "Dajabón" to listOf("Dajabón", "El Pino", "Loma de Cabrera", "Partidó", "Restauración"),
    "Distrito Nacional" to listOf("Santo Domingo de Guzmán"),
    "Duarte" to listOf("San Francisco de Macorís", "Arenoso", "Castillo", "Eugenio María de Hostos", "Las Guáranas", "Pimentel", "Villa Riva"),
    "El Seibo" to listOf("El Seibo", "Miches"),
    "Elías Piña" to listOf("Comendador", "Bánica", "El Llano", "Hondo Valle", "Juan Santiago", "Pedro Santana"),
    "Espaillat" to listOf("Moca", "Cayetano Germosén", "Gaspar Hernández", "Jamao al Norte"),
    "Hato Mayor" to listOf("Hato Mayor del Rey", "El Valle", "Sabana de la Mar"),
    "Hermanas Mirabal" to listOf("Salcedo", "Tenares", "Villa Tapia"),
    "Independencia" to listOf("Jimaní", "Cristóbal", "Duvergé", "La Descubierta", "Mella", "Postrer Río"),
    "La Altagracia" to listOf("Higüey", "San Rafael del Yuma", "Punta Cana", "Bávaro", "Bayahíbe"),
    "La Romana" to listOf("La Romana", "Guaymate", "Villa Hermosa"),
    "La Vega" to listOf("La Vega", "Constanza", "Jarabacoa", "Jima Abajo"),
    "María Trinidad Sánchez" to listOf("Nagua", "Cabrera", "El Factor", "Río San Juan"),
    "Monseñor Nouel" to listOf("Bonao", "Maimón", "Piedra Blanca"),
    "Monte Cristi" to listOf("Monte Cristi", "Castañuelas", "Guayubín", "Las Matas de Santa Cruz", "Pepillo Salcedo", "Villa Vásquez"),
    "Monte Plata" to listOf("Monte Plata", "Bayaguana", "Peralvillo", "Sabana Grande de Boyá", "Yamasá"),
    "Pedernales" to listOf("Pedernales", "Oviedo"),
    "Peravia" to listOf("Baní", "Nizao"),
    "Puerto Plata" to listOf("Puerto Plata", "Altamira", "Guananico", "Imbert", "Los Hidalgos", "Luperón", "Sosúa", "Villa Isabela", "Villa Montellano"),
    "Samaná" to listOf("Samaná", "Las Terrenas", "Sánchez"),
    "San Cristóbal" to listOf("San Cristóbal", "Bajos de Haina", "Cambita Garabitos", "Los Cacaos", "Sabana Grande de Palenque", "San Gregorio de Nigua", "Villa Altagracia", "Yaguate"),
    "San José de Ocoa" to listOf("San José de Ocoa", "Rancho Arriba", "Sabana Larga"),
    "San Juan" to listOf("San Juan de la Maguana", "Bohechío", "El Cercado", "Juan de Herrera", "Las Matas de Farfán", "Vallejuelo"),
    "San Pedro de Macorís" to listOf("San Pedro de Macorís", "Consuelo", "Guayacanes", "Quisqueya", "Ramón Santana", "San José de los Llanos"),
    "Sánchez Ramírez" to listOf("Cotuí", "Cevicos", "Fantino", "La Mata"),
    "Santiago" to listOf("Santiago de los Caballeros", "Bisonó", "Jánico", "Licey al Medio", "Puñal", "Sabana Iglesia", "San José de las Matas", "Tamboril", "Villa González"),
    "Santiago Rodríguez" to listOf("Sabaneta", "Los Almácigos", "Monción"),
    "Santo Domingo" to listOf("Santo Domingo Este", "Santo Domingo Norte", "Santo Domingo Oeste", "Boca Chica", "Los Alcarrizos", "Pedro Brand", "San Antonio de Guerra"),
    "Valverde" to listOf("Mao", "Esperanza", "Laguna Salada")
)

fun normalizeLocationName(input: String): String {
    val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
    return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase(Locale.ROOT).trim()
}

fun findMatchingProvince(input: String?): String? {
    if (input == null) return null
    val normalizedInput = normalizeLocationName(input)

    return dominicanProvinces.keys.find {
        val normalizedKey = normalizeLocationName(it)
        // Check both ways: if input contains key (e.g. "Provincia de Azua" contains "Azua")
        // or key contains input (e.g. "Santo Domingo" contains "Santo Domingo")
        normalizedInput.contains(normalizedKey) || normalizedKey.contains(normalizedInput)
    }
}

fun findMatchingMunicipality(province: String, input: String?): String? {
    if (input == null) return null
    val municipalities = dominicanProvinces[province] ?: return null
    val normalizedInput = normalizeLocationName(input)

    return municipalities.find {
        val normalizedKey = normalizeLocationName(it)
        normalizedInput.contains(normalizedKey) || normalizedKey.contains(normalizedInput)
    }
}
