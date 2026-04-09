package xyz.extera.rpc.ui

import android.content.Context
import android.content.pm.PackageManager

/** Simple data class representing an installed launchable app */
data class AppEntry(
    val packageName: String,
    val label: String
)

/** Returns all installed apps that have a launch intent, sorted alphabetically */
fun getAllInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return apps
        .filter { appInfo -> pm.getLaunchIntentForPackage(appInfo.packageName) != null }
        .map { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString()
            AppEntry(packageName = appInfo.packageName, label = label)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
