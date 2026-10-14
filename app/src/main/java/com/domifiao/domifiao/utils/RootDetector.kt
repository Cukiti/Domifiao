package com.domifiao.domifiao.utils

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Detector de dispositivos rooteados
 *
 * Detecta si el dispositivo Android tiene root access, lo cual puede representar
 * un riesgo de seguridad para aplicaciones que manejan información sensible.
 *
 * Uso:
 * ```
 * val rootDetector = RootDetector(context)
 * if (rootDetector.isDeviceRooted()) {
 *     // Mostrar advertencia o bloquear funcionalidad
 * }
 * ```
 */
class RootDetector(private val context: Context) {

    companion object {
        // Paths comunes de binarios de root
        private val ROOT_BINARY_PATHS = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        // Apps comunes de root
        private val ROOT_APPS_PACKAGES = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk"
        )

        // Directorios sospechosos que no deberían existir
        private val DANGEROUS_PROPERTIES = arrayOf(
            "ro.debuggable",
            "ro.secure"
        )
    }

    /**
     * Verifica si el dispositivo está rooteado usando múltiples métodos
     *
     * @return true si se detecta root, false en caso contrario
     */
    fun isDeviceRooted(): Boolean {
        return checkRootBinaries() ||
                checkRootApps() ||
                checkSuExists() ||
                checkBuildTags() ||
                checkDangerousProperties()
    }

    /**
     * Verifica la presencia de binarios de root en paths conocidos
     */
    private fun checkRootBinaries(): Boolean {
        return ROOT_BINARY_PATHS.any { path ->
            File(path).exists()
        }
    }

    /**
     * Verifica si hay apps de root management instaladas
     */
    private fun checkRootApps(): Boolean {
        val packageManager = context.packageManager
        return ROOT_APPS_PACKAGES.any { packageName ->
            try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Intenta ejecutar el comando 'su' para verificar acceso root
     */
    private fun checkSuExists(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val bufferedReader = process.inputStream.bufferedReader()
            bufferedReader.readLine() != null
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    /**
     * Verifica las build tags del sistema (test-keys indica ROM custom)
     */
    private fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * Verifica propiedades del sistema que indican inseguridad
     */
    private fun checkDangerousProperties(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getprop")
            val bufferedReader = process.inputStream.bufferedReader()
            val lines = bufferedReader.readLines()

            lines.any { line ->
                // ro.debuggable=1 o ro.secure=0 son signos de root
                line.contains("ro.debuggable") && line.contains("1") ||
                line.contains("ro.secure") && line.contains("0")
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtiene un reporte detallado del estado de root del dispositivo
     *
     * @return Map con los resultados de cada verificación
     */
    fun getDetailedReport(): Map<String, Boolean> {
        return mapOf(
            "rootBinariesFound" to checkRootBinaries(),
            "rootAppsInstalled" to checkRootApps(),
            "suCommandAvailable" to checkSuExists(),
            "suspiciousBuildTags" to checkBuildTags(),
            "dangerousProperties" to checkDangerousProperties()
        )
    }

    /**
     * Obtiene un nivel de confianza sobre el estado de root (0-100)
     * 0 = Definitivamente rooteado
     * 100 = Definitivamente NO rooteado
     *
     * @return Nivel de confianza (0-100)
     */
    fun getRootConfidenceLevel(): Int {
        val report = getDetailedReport()
        val detectedCount = report.values.count { it }
        val totalChecks = report.size

        // Si todas las verificaciones detectan root = 0% confianza (100% rooteado)
        // Si ninguna detecta root = 100% confianza (0% rooteado)
        return ((totalChecks - detectedCount) * 100) / totalChecks
    }

    /**
     * Verifica si el dispositivo es un emulador
     * Los emuladores suelen tener características similares a dispositivos rooteados
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }
}

/**
 * Extension function para verificar rápidamente si hay root
 */
fun Context.isDeviceRooted(): Boolean {
    return RootDetector(this).isDeviceRooted()
}
