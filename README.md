# Domifiao 🛵

**App Android que conecta a los clientes con su colmado de barrio: pedidos a domicilio, fiao (crédito) digital, fidelización y chat.**

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Hilt](https://img.shields.io/badge/DI-Hilt-34A853)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?logo=firebase&logoColor=black)
![Google Maps](https://img.shields.io/badge/Google%20Maps-4285F4?logo=googlemaps&logoColor=white)

## Qué es

En República Dominicana el colmado es el corazón del barrio, y el *fiao* (comprar a crédito) se lleva casi siempre en una libreta de papel. Domifiao digitaliza esa relación con dos experiencias en una misma app:

- **Cliente**: encuentra colmados cercanos en el mapa, arma su carrito, repite pedidos anteriores, pide crédito y acumula puntos de fidelidad.
- **Colmado**: gestiona su catálogo e inventario, recibe pedidos en tiempo real, aprueba o rechaza solicitudes de crédito y lleva el historial de deudas de cada cliente.

## Aspectos técnicos destacados

- **Arquitectura MVVM** con repositorios e inyección de dependencias con **Hilt**.
- **UI en Jetpack Compose** con Navigation y animaciones Lottie.
- **Firebase completo**: Auth, Firestore (con reglas e índices propios), Cloud Messaging para notificaciones push, Remote Config y Hosting.
- **Cloud Functions (Node.js)** para enviar códigos OTP por correo.
- **Seguridad**: reCAPTCHA Enterprise, autenticación biométrica, detección de root y verificación de identidad.
- **Mapas y ubicación** con Google Maps y Play Services Location.
- **Cámara y ML Kit** (CameraX) para escanear y fotografiar productos.

## Estructura

```
app/src/main/java/com/domifiao/domifiao/
├── data/         Repositorios y sesión
├── di/           Módulos de Hilt
├── model/        Modelos (pedido, crédito, fidelidad, producto…)
├── services/     FCM y reCAPTCHA
├── ui/           Pantallas de cliente y de colmado (Compose)
└── utils/        Biometría, cámara, notificaciones, seguridad
functions/        Cloud Functions
public/           Políticas de privacidad y baja de cuenta (Hosting)
```

## Compilar

1. Crea un proyecto de Firebase y coloca `google-services.json` en `app/`.
2. Añade a `local.properties`:
   ```properties
   GOOGLE_MAPS_API_KEY=tu_clave
   ```
3. Para las Cloud Functions, define las variables de entorno `MAIL_USER` y `MAIL_PASS`.
4. Ejecuta `./gradlew assembleDebug`.

Las claves y archivos de firma no están en el repositorio por seguridad.

---
Desarrollado por **[Cukiti](https://github.com/Cukiti)**.
