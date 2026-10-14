package com.domifiao.domifiao.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.domifiao.domifiao.MainActivity
import com.domifiao.domifiao.R
import kotlin.random.Random

class NotificationHandler(private val context: Context) {

    companion object {
        // Canales de notificacion
        const val CHANNEL_ORDERS = "domifiao_orders"
        const val CHANNEL_CHAT = "domifiao_chat"
        const val CHANNEL_CREDITS = "domifiao_credits"
        const val CHANNEL_GENERAL = "domifiao_general"

        const val EXTRA_ROUTE = "deep_link_route"

        // Patron de vibracion estilo WhatsApp (en milisegundos)
        // [delay, vibrar, pausa, vibrar]
        private val VIBRATION_PATTERN = longArrayOf(0, 250, 100, 250)
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /**
     * Crea los canales de notificacion con sonido y vibracion
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Sonido por defecto del sistema
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Atributos de audio para notificaciones
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // Canal de Pedidos (Alta prioridad) - Sonido fuerte
            val ordersChannel = NotificationChannel(
                CHANNEL_ORDERS,
                "Pedidos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones sobre pedidos nuevos y actualizaciones"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(defaultSoundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Canal de Chat (Alta prioridad) - Como WhatsApp
            val chatChannel = NotificationChannel(
                CHANNEL_CHAT,
                "Mensajes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mensajes de chat con colmados o clientes"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(defaultSoundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            // Canal de Creditos (Alta prioridad)
            val creditsChannel = NotificationChannel(
                CHANNEL_CREDITS,
                "Creditos y Fiado",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Solicitudes de credito y pagos"
                enableLights(true)
                lightColor = Color.YELLOW
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(defaultSoundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Canal General (Media prioridad)
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Otras notificaciones de Domifiao"
                enableLights(true)
                lightColor = Color.WHITE
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
                setShowBadge(true)
            }

            // Eliminar canales antiguos si existen (para actualizar configuracion)
            try {
                notificationManager.deleteNotificationChannel(CHANNEL_ORDERS)
                notificationManager.deleteNotificationChannel(CHANNEL_CHAT)
                notificationManager.deleteNotificationChannel(CHANNEL_CREDITS)
                notificationManager.deleteNotificationChannel(CHANNEL_GENERAL)
            } catch (_: Exception) { }

            // Crear los canales
            notificationManager.createNotificationChannels(
                listOf(ordersChannel, chatChannel, creditsChannel, generalChannel)
            )
        }
    }

    /**
     * Determina el canal apropiado segun el tipo de notificacion
     */
    private fun getChannelForType(type: String?): String {
        return when (type?.uppercase()) {
            "NEW_ORDER", "ORDER_UPDATE", "SUCCESS", "DELIVERING", "PREPARING" -> CHANNEL_ORDERS
            "MESSAGE", "DEBT_CHAT" -> CHANNEL_CHAT
            "CREDIT_REQUEST", "CREDIT_APPROVED", "CREDIT_REJECTED", "DEBT_UPDATE" -> CHANNEL_CREDITS
            else -> CHANNEL_GENERAL
        }
    }

    /**
     * Obtiene el icono pequeno segun el tipo
     */
    private fun getSmallIcon(type: String?): Int {
        // Puedes crear iconos personalizados en res/drawable
        // Por ahora usamos el icono de la app
        return R.drawable.ic_launcher_foreground
    }

    /**
     * Obtiene el color de acento segun el tipo
     */
    private fun getAccentColor(type: String?): Int {
        return when (type?.uppercase()) {
            "NEW_ORDER" -> Color.parseColor("#4CAF50")      // Verde
            "ORDER_UPDATE", "PREPARING" -> Color.parseColor("#2196F3")  // Azul
            "DELIVERING" -> Color.parseColor("#9C27B0")     // Morado
            "MESSAGE", "DEBT_CHAT" -> Color.parseColor("#2196F3")  // Azul
            "CREDIT_REQUEST" -> Color.parseColor("#FF9800") // Naranja
            "CREDIT_APPROVED" -> Color.parseColor("#4CAF50") // Verde
            "CREDIT_REJECTED" -> Color.parseColor("#F44336") // Rojo
            "SUCCESS" -> Color.parseColor("#4CAF50")        // Verde
            else -> Color.parseColor("#4CAF50")             // Verde por defecto
        }
    }

    /**
     * Muestra una notificacion inmersiva estilo WhatsApp
     */
    fun showNotification(title: String, message: String, route: String?, type: String? = null) {
        val channelId = getChannelForType(type)

        // Intent para abrir la app al tocar
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) {
                putExtra(EXTRA_ROUTE, route)
            }
        }

        val uniqueRequestCode = System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getActivity(
            context,
            uniqueRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Icono grande (opcional - logo de la app)
        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (_: Exception) { null }

        // Sonido de notificacion
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Construir la notificacion
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(getSmallIcon(type))
            .setContentTitle(title)
            .setContentText(message)
            .setColor(getAccentColor(type))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(VIBRATION_PATTERN)
            .setLights(getAccentColor(type), 500, 500)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        // Agregar icono grande si existe
        largeIcon?.let {
            notificationBuilder.setLargeIcon(it)
        }

        // Para Android 8+ agregar badge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder.setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
        }

        // Mostrar la notificacion
        val notificationId = Random.nextInt(1000, 9999)
        notificationManager.notify(notificationId, notificationBuilder.build())

        // Vibrar manualmente para dispositivos que lo requieran
        vibrateDevice()
    }

    /**
     * Vibra el dispositivo
     */
    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(VIBRATION_PATTERN, -1)
                }
            }
        } catch (_: Exception) {
            // Ignorar si no se puede vibrar
        }
    }

    /**
     * Metodo de compatibilidad (sin tipo)
     */
    fun showNotification(title: String, message: String, route: String?) {
        showNotification(title, message, route, null)
    }

    /**
     * Cancela todas las notificaciones de la app
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * Cancela una notificacion especifica
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}