package xyz.extera.rpc

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import xyz.extera.rpc.data.CredentialsStore
import xyz.extera.rpc.data.MatrixApi
import xyz.extera.rpc.data.SettingsStore
import xyz.extera.rpc.service.MusicNotificationListenerService
import xyz.extera.rpc.ui.AppDetectionSettingsScreen
import xyz.extera.rpc.ui.DashboardScreen
import xyz.extera.rpc.ui.LoginScreen
import xyz.extera.rpc.ui.MusicSettingsScreen
import xyz.extera.rpc.ui.theme.ExteraRPCTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExteraRPCTheme {
                var isLoggedIn by remember {
                    mutableStateOf(CredentialsStore.getCredentials(this@MainActivity) != null)
                }
                var currentScreen by remember { mutableStateOf("dashboard") }

                if (isLoggedIn) {
                    val credentials = remember {
                        CredentialsStore.getCredentials(this@MainActivity)
                    }
                    when (currentScreen) {
                        "dashboard" -> DashboardScreen(
                            userId = credentials?.userId ?: "unknown",
                            onLogout = {
                                if (credentials != null) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val delResult = MatrixApi.deleteRichPresence(
                                            homeserverUrl = credentials.homeserverUrl,
                                            accessToken = credentials.accessToken,
                                            userId = credentials.userId
                                        )
                                        if (delResult.isSuccess) {
                                            Log.d(TAG, "Rich Presence deleted successfully")
                                        } else {
                                            Log.w(TAG, "Failed to delete Rich Presence", delResult.exceptionOrNull())
                                        }
                                        CredentialsStore.clear(this@MainActivity)
                                    }
                                }
                                isLoggedIn = false
                            },
                            onMusicSettingsClick = {
                                currentScreen = "music_settings"
                            },
                            onAppDetectionSettingsClick = {
                                currentScreen = "app_detection_settings"
                            },
                            onMusicEnabledChanged = {
                                if (!it && credentials != null) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val delResult = MatrixApi.deleteRichPresence(
                                            homeserverUrl = credentials.homeserverUrl,
                                            accessToken = credentials.accessToken,
                                            userId = credentials.userId
                                        )
                                        if (delResult.isSuccess) {
                                            Log.d(TAG, "Rich Presence deleted successfully")
                                        } else {
                                            Log.w(TAG, "Failed to delete Rich Presence", delResult.exceptionOrNull())
                                        }
                                    }
                                }
                            },
                            onForegroundAppChanged = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (credentials == null) {
                                        Log.w(TAG, "Not sending Rich Presence bc credentials==null")
                                        return@launch
                                    }
                                    if (it == null) {
                                        val delResult = MatrixApi.deleteRichPresence(
                                            homeserverUrl = credentials.homeserverUrl,
                                            accessToken = credentials.accessToken,
                                            userId = credentials.userId
                                        )
                                        if (delResult.isSuccess) {
                                            Log.d(TAG, "Rich Presence deleted successfully")
                                        } else {
                                            Log.w(TAG, "Failed to delete Rich Presence", delResult.exceptionOrNull())
                                        }
                                    } else {
                                        val appName = if (SettingsStore.isBroadcastAppName(applicationContext)) {
                                            try {
                                                val pm = applicationContext.packageManager
                                                pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Failed to get app label for ${it}", e)
                                                null
                                            }
                                        } else {
                                            null
                                        }
                                        if (appName == null) return@launch
                                        val rpcActivity = MatrixApi.RpcActivity(
                                            name = appName,
                                            details = ""
                                        )
                                        val rpcResult = MatrixApi.setRpcActivity(
                                            homeserverUrl = credentials.homeserverUrl,
                                            accessToken = credentials.accessToken,
                                            userId = credentials.userId,
                                            activity = rpcActivity
                                        )
                                        if (rpcResult.isSuccess) {
                                            Log.d(TAG, "Rich Presence sent successfully")
                                        } else {
                                            Log.w(TAG, "Failed to send rpc", rpcResult.exceptionOrNull())
                                        }
                                    }
                                }
                            }
                        )
                        "music_settings" -> MusicSettingsScreen(
                            onBack = { currentScreen = "dashboard" }
                        )
                        "app_detection_settings" -> AppDetectionSettingsScreen(
                            onBack = { currentScreen = "dashboard" }
                        )
                    }
                } else {
                    LoginScreen(onLoginSuccess = {
                        isLoggedIn = true
                    })
                }
            }
        }
    }
}
