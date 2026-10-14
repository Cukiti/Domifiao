import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.domifiao.domifiao"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.domifiao.domifiao"
        minSdk = 24
        targetSdk = 35
        versionCode = 34
        versionName = "1.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Cargar API keys desde local.properties de forma segura
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }

        // Exponer Google Maps API Key via BuildConfig
        val googleMapsApiKey = properties.getProperty("GOOGLE_MAPS_API_KEY")
            ?: System.getenv("GOOGLE_MAPS_API_KEY")
            ?: ""

        // Exponer Mapbox Access Token via BuildConfig (seguro)
        val mapboxAccessToken = properties.getProperty("MAPBOX_ACCESS_TOKEN")
            ?: System.getenv("MAPBOX_ACCESS_TOKEN")
            ?: ""

        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"$mapboxAccessToken\"")
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsApiKey
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
                
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storePassword = keystoreProperties.getProperty("storePassword")
                val storePath = keystoreProperties.getProperty("storeFile")
                storeFile = rootProject.file(storePath)
            } else {
                println("AVISO: No se encontró keystore.properties en ${keystorePropertiesFile.absolutePath}")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // Habilitar minification y obfuscación para proteger el código
            isMinifyEnabled = true
            isShrinkResources = true  // También optimizar recursos
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk.debugSymbolLevel = "FULL"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // CORE
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // COMPOSE
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // NAVIGATION
    implementation(libs.androidx.navigation.compose)

    // FIREBASE (usando BOM para manejar versiones)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-config-ktx")

    // Firebase App Check
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // Credentials
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Security - EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // reCAPTCHA Enterprise
    implementation("com.google.android.recaptcha:recaptcha:18.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Browser (Required for Firebase Auth reCAPTCHA fallback)
    implementation("androidx.browser:browser:1.7.0")

    // IMAGES
    implementation(libs.coil.compose)

    // LOTTIE
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // GOOGLE MAPS & LOCATION
    implementation(libs.play.services.location)
    // implementation(libs.maps.compose)
    // implementation(libs.play.services.maps)

    // MAPBOX (con variante NDK27 para soporte 16KB)
    implementation("com.mapbox.maps:android-ndk27:11.9.0")
    implementation("com.mapbox.extension:maps-compose:11.9.0")

    // CAMERA (CameraX)
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion") // Force newer native libs for 16KB support
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // HILT (Dependency Injection)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // TESTING
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.junit.junit)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // DEBUG
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}