package xyz.extera.rpc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userId: String,
    onLogout: () -> Unit,
    onMusicSettingsClick: () -> Unit,
    onMusicEnabledChanged: (Boolean) -> Unit,
    onAppDetectionSettingsClick: () -> Unit,
    onForegroundAppChanged: (String?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Extract localpart from @user:server.com
    val localpart = remember(userId) {
        if (userId.startsWith("@") && userId.contains(":")) {
            userId.substring(1, userId.indexOf(':'))
        } else {
            userId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extera Rich Presence") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = {
                                menuExpanded = false
                                showLogoutDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Hello, $localpart!",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Row for Music card
            Row(modifier = Modifier.fillMaxWidth()) {
                MusicCard(
                    modifier = Modifier.weight(0.475f),
                    onSettingsClick = onMusicSettingsClick,
                    onEnabledChanged = onMusicEnabledChanged
                )
                Spacer(modifier = Modifier.weight(0.05f))
                AppDetectionCard(
                    modifier = Modifier.weight(0.475f),
                    onSettingsClick = onAppDetectionSettingsClick,
                    onForegroundAppChanged = onForegroundAppChanged
                )
            }
//            Spacer(modifier = Modifier.height(16.dp))
//            // Row for App Detection card
//            Row(modifier = Modifier.fillMaxWidth()) {
//                Spacer(modifier = Modifier.weight(0.5f))
//            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
