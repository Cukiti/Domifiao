# ============================================
# PROGUARD RULES - DOMIFIAO
# ============================================

# Mantener información de línea para debugging de crashes
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Mantener números de línea para stack traces legibles
-keepattributes LineNumberTable

# ============================================
# KOTLIN
# ============================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# ============================================
# JETPACK COMPOSE
# ============================================
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose Runtime
-keep class androidx.compose.runtime.** { *; }
-keep interface androidx.compose.runtime.** { *; }

# ============================================
# FIREBASE
# ============================================
# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-keepclassmembers class com.google.firebase.auth.** { *; }

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class com.google.firebase.firestore.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Firebase Messaging
-keep class com.google.firebase.messaging.** { *; }
-keepclassmembers class com.google.firebase.messaging.** { *; }

# Firebase Storage
-keep class com.google.firebase.storage.** { *; }
-keepclassmembers class com.google.firebase.storage.** { *; }

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-keepclassmembers class com.google.firebase.analytics.** { *; }

# Firebase App Check
-keep class com.google.firebase.appcheck.** { *; }
-keepclassmembers class com.google.firebase.appcheck.** { *; }

# ============================================
# GOOGLE PLAY SERVICES
# ============================================
# Google Play Services Core
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Google Maps
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }

# Google Location Services
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.location.**
-dontwarn com.google.android.gms.internal.location.**
-keep class com.google.android.gms.internal.location.** { *; }

# Google Credentials
-keep class com.google.android.libraries.identity.** { *; }
-keep class androidx.credentials.** { *; }

# ============================================
# ML KIT
# ============================================
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit** { *; }
-dontwarn com.google.mlkit.**

# ============================================
# CAMERAX
# ============================================
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ============================================
# COIL (Image Loading)
# ============================================
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**

# ============================================
# MAPBOX
# ============================================
-keep class com.mapbox.** { *; }
-keep interface com.mapbox.** { *; }
-dontwarn com.mapbox.**
-keepclassmembers class com.mapbox.** { *; }

# Mapbox Annotations
-keep class com.mapbox.maps.plugin.annotation.** { *; }
-keep class com.mapbox.geojson.** { *; }

# ============================================
# NAVIGATION COMPONENT
# ============================================
-keep class androidx.navigation.** { *; }
-keep interface androidx.navigation.** { *; }

# ============================================
# MODELOS DE DATOS (Data Classes)
# ============================================
# IMPORTANTE: Mantener todas las data classes para Firestore serialization
-keep class com.domifiao.domifiao.model.** { *; }
-keepclassmembers class com.domifiao.domifiao.model.** { *; }

# Mantener constructores sin argumentos para Firestore
-keepclassmembers class * {
    public <init>();
}

# ============================================
# UTILIDADES DE SEGURIDAD
# ============================================
# Mantener BiometricAuthHelper y sus métodos públicos
-keep class com.domifiao.domifiao.utils.BiometricAuthHelper { *; }

# Mantener RootDetector
-keep class com.domifiao.domifiao.utils.RootDetector { *; }

# Mantener RemoteConfigHelper
-keep class com.domifiao.domifiao.utils.RemoteConfigHelper { *; }

# SessionManager ya está protegido por data classes

# ============================================
# SERIALIZATION
# ============================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================
# ENUMS
# ============================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# PARCELABLE
# ============================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================
# DEBUGGING (Remover en producción final)
# ============================================
# Descomentar para ver qué clases están siendo removidas
# -printusage usage.txt
# -printseeds seeds.txt
# -printconfiguration configuration.txt

# ============================================
# SEGURIDAD ADICIONAL
# ============================================
# Remover logs de producción
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Ofuscar nombres de paquetes agresivamente
-repackageclasses 'o'
-allowaccessmodification
-overloadaggressively

# ============================================
# WARNINGS A IGNORAR
# ============================================
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.android.gms.internal.location.**
