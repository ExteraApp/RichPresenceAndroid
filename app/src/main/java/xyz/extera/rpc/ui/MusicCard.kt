package xyz.extera.rpc.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import xyz.extera.rpc.data.SettingsStore
import xyz.extera.rpc.service.ServiceStatusNotificationManager

fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
    return enabledPackages.contains(context.packageName)
}

fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun MusicCard(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsStore.isMusicEnabled(context)) }
    var pendingPermission by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && pendingPermission) {
                pendingPermission = false
                if (isNotificationListenerEnabled(context)) {
                    enabled = true
                    SettingsStore.setMusicEnabled(context, true)
                    ServiceStatusNotificationManager.showServiceEnabledNotification(context, "Music")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Music",
                style = MaterialTheme.typography.titleLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Music settings")
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { newValue ->
                        if (newValue) {
                            if (!isNotificationListenerEnabled(context)) {
                                openNotificationListenerSettings(context)
                                pendingPermission = true
                                return@Switch
                            }
                        }
                        enabled = newValue
                        SettingsStore.setMusicEnabled(context, newValue)
                        if (newValue) {
                            ServiceStatusNotificationManager.showServiceEnabledNotification(context, "Music")
                        } else {
                            ServiceStatusNotificationManager.showServiceDisabledNotification(context, "Music")
                        }
                        onEnabledChanged(newValue)
                    }
                )
            }
        }
    }
}
