package xyz.extera.rpc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import xyz.extera.rpc.data.SettingsStore

/**
 * Foreground service that periodically polls UsageStatsManager to detect
 * which app is currently in the foreground.
 *
 * When the foreground app is in the user's allowed list, it reports the
 * package name via [currentForegroundApp]. Handling (RPC sending etc.)
 * will be added later.
 */
class AppDetectionService : Service() {
    companion object {
        private const val TAG = "AppDetectionSvc"
        private const val CHANNEL_ID = "app_detection_channel"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 3000L

        /** The package name of the currently detected foreground app, or null. */
        @Volatile
        var currentForegroundApp: String? = null
            private set

        /** Optional callback for foreground app changes. */
        @Volatile
        var onForegroundAppChanged: ((String?) -> Unit)? = null

        fun setOnForegroundAppChangedListener(callback: (String?) -> Unit) {
            onForegroundAppChanged = callback
        }

        fun start(context: Context) {
            val intent = Intent(context, AppDetectionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppDetectionService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        startPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        currentForegroundApp = null
        onForegroundAppChanged?.invoke(null)
        Log.d(TAG, "App detection service stopped")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App Detection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors the currently active app for Rich Presence"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("App Detection")
            .setContentText("Monitoring foreground app")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun startPolling() {
        serviceScope.launch {
            Log.d(TAG, "App detection polling started")
            while (isActive) {
                if (SettingsStore.isAppDetectionEnabled(applicationContext)) {
                    val foreground = getForegroundPackage()
                    val allowedApps = SettingsStore.getAppDetectionAllowedApps(applicationContext)

                    val detected = if (foreground != null && allowedApps.contains(foreground)) {
                        foreground
                    } else {
                        null
                    }

                    if (detected != currentForegroundApp) {
                        currentForegroundApp = detected
                        onForegroundAppChanged?.invoke(detected)
                        Log.d(TAG, "Foreground app changed: $detected")
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Query UsageStatsManager for the most recently used app in the last few seconds.
     */
    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 10_000,
            now
        )
        if (stats.isNullOrEmpty()) return null

        // The app with the most recent lastTimeUsed is the foreground app
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}
