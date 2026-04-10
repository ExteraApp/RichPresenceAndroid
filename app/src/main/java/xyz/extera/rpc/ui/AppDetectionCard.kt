package xyz.extera.rpc.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import xyz.extera.rpc.data.SettingsStore
import xyz.extera.rpc.service.AppDetectionService
import xyz.extera.rpc.service.ServiceStatusNotificationManager

/** Checks if the Usage Access permission is granted */
fun isUsageAccessEnabled(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun AppDetectionCard(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
    onForegroundAppChanged: (String?) -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsStore.isAppDetectionEnabled(context)) }
    var pendingPermission by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && pendingPermission) {
                pendingPermission = false
                if (isUsageAccessEnabled(context)) {
                    enabled = true
                    SettingsStore.setAppDetectionEnabled(context, true)
                    AppDetectionService.start(context)
                    ServiceStatusNotificationManager.showServiceEnabledNotification(context, "App Detection")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        AppDetectionService.setOnForegroundAppChangedListener(onForegroundAppChanged)
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
                text = "App detection",
                style = MaterialTheme.typography.titleLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "App detection settings")
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { newValue ->
                        if (newValue) {
                            // Prompt for usage access if needed
                            if (!isUsageAccessEnabled(context)) {
                                openUsageAccessSettings(context)
                                pendingPermission = true
                                return@Switch
                            }
                        }
                        enabled = newValue
                        SettingsStore.setAppDetectionEnabled(context, newValue)
                        if (newValue) {
                            AppDetectionService.start(context)
                            ServiceStatusNotificationManager.showServiceEnabledNotification(context, "App Detection")
                        } else {
                            AppDetectionService.stop(context)
                            ServiceStatusNotificationManager.showServiceDisabledNotification(context, "App Detection")
                        }
                    }
                )
            }
        }
    }
}
