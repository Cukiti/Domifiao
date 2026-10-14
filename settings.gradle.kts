pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // --- CONFIGURACIÓN DE MAPBOX (OBLIGATORIO) ---
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                // Lee el token desde local.properties
                val localProperties = java.util.Properties()
                val localPropertiesFile = File(rootDir, "local.properties")
                if (localPropertiesFile.exists()) {
                    localPropertiesFile.inputStream().use { localProperties.load(it) }
                }
                
                // SOLUCIÓN: Validar token antes de usarlo
                val mapboxToken = localProperties.getProperty("MAPBOX_SECRET_TOKEN")
                    ?: System.getenv("MAPBOX_SECRET_TOKEN")
                    ?: ""
                
                // Si no hay token, no configurar credenciales (evita bloqueo)
                if (mapboxToken.isNotEmpty()) {
                    password = mapboxToken
                } else {
                    password = ""
                    println("⚠️ ADVERTENCIA: MAPBOX_SECRET_TOKEN vacío - Gradle sync puede fallar al descargar Mapbox")
                }
            }
        }
    }
}

rootProject.name = "Domifiao"
include(":app")