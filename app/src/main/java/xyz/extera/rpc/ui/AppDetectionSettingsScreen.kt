package xyz.extera.rpc.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.extera.rpc.data.SettingsStore

private fun drawableToBitmapAD(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetectionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var allowedApps by remember { mutableStateOf(SettingsStore.getAppDetectionAllowedApps(context)) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { getAllInstalledApps(context) }
    }

    val filteredApps = remember(apps, searchQuery) {
        val list = apps ?: return@remember emptyList()
        if (searchQuery.isBlank()) list
        else list.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App detection settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Section header + search
            item {
                Text(
                    text = "Detected apps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // App list
            if (apps == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filteredApps.isEmpty()) {
                item {
                    Text(
                        text = "No apps found.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val checked = allowedApps.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val drawable = remember(app.packageName) {
                            try {
                                context.packageManager.getApplicationIcon(app.packageName)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        if (drawable != null) {
                            Image(
                                bitmap = remember(drawable) { drawableToBitmapAD(drawable).asImageBitmap() },
                                contentDescription = app.label,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = checked,
                            onCheckedChange = { enabled ->
                                allowedApps = if (enabled) {
                                    allowedApps + app.packageName
                                } else {
                                    allowedApps - app.packageName
                                }
                                SettingsStore.setAppDetectionAllowedApps(context, allowedApps)
                            }
                        )
                    }
                }
            }
        }
    }
}
