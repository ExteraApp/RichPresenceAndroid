package xyz.extera.rpc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import xyz.extera.rpc.MainActivity
import xyz.extera.rpc.R

/**
 * Notification manager for Rich Presence service status notifications.
 * Shows notifications when services are enabled or disabled.
 */
object ServiceStatusNotificationManager {
    private const val CHANNEL_ID = "service_status_channel"
    private const val NOTIFICATION_TAG = "service_status"
    
    /**
     * Shows a notification when any Rich Presence service is enabled
     */
    fun showServiceEnabledNotification(context: Context, serviceName: String) {
        val notification = buildNotification(
            context = context,
            title = "Rich Presence Service Enabled",
            text = "$serviceName service is active",
            actionText = "View details",
            actionIntent = createMainActivityIntent(context)
        )
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_TAG, 1, notification)
    }

    /**
     * Shows a temporary notification when a service is disabled
     */
    fun showServiceDisabledNotification(context: Context, serviceName: String) {
        val notification = buildNotification(
            context = context,
            title = "Rich Presence Service Disabled",
            text = "$serviceName service is no longer active",
            actionText = null,
            actionIntent = null
        )
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_TAG, 2, notification)
        
        // Auto-dismiss after 3 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(NOTIFICATION_TAG, 2)
        }, 3000)
    }
    
    private fun buildNotification(
        context: Context,
        title: String,
        text: String,
        actionText: String?,
        actionIntent: Intent?
    ): Notification {
        createNotificationChannel(context)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to create this drawable
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        // Add action if provided
        actionText?.let { action ->
            actionIntent?.let { intent ->
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    android.R.drawable.ic_menu_info_details,
                    action,
                    pendingIntent
                )
            }
        }
        
        return builder.build()
    }
    
    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Service Status",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for Rich Presence service status"
            setShowBadge(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createMainActivityIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}