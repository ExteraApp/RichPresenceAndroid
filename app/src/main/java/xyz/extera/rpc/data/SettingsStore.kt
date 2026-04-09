package xyz.extera.rpc.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple SharedPreferences store for app settings (feature toggles, etc.).
 */
object SettingsStore {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_MUSIC_ENABLED = "music_enabled"
    private const val KEY_MUSIC_ALLOWED_APPS = "music_allowed_apps"
    private const val KEY_BROADCAST_ALBUM_COVER = "broadcast_album_cover"
    private const val KEY_BROADCAST_APP_NAME = "broadcast_app_name"
    private const val KEY_APP_DETECTION_ENABLED = "app_detection_enabled"
    private const val KEY_APP_DETECTION_ALLOWED_APPS = "app_detection_allowed_apps"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns whether the music feature is enabled */
    fun isMusicEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_MUSIC_ENABLED, false)

    /** Save whether the music feature is enabled */
    fun setMusicEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MUSIC_ENABLED, enabled).apply()
    }

    /** Returns whether to broadcast album cover in Rich Presence */
    fun isBroadcastAlbumCover(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_BROADCAST_ALBUM_COVER, false)

    /** Set broadcast album cover flag */
    fun setBroadcastAlbumCover(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BROADCAST_ALBUM_COVER, enabled).apply()
    }

    /** Returns whether to broadcast the player app name in Rich Presence */
    fun isBroadcastAppName(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_BROADCAST_APP_NAME, false)

    /** Set broadcast app name flag */
    fun setBroadcastAppName(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BROADCAST_APP_NAME, enabled).apply()
    }

    /** Returns the set of allowed package names for music listening. Empty set means all are allowed. */
    fun getAllowedApps(context: Context): Set<String> =
        getPrefs(context).getStringSet(KEY_MUSIC_ALLOWED_APPS, emptySet()) ?: emptySet()

    /** Persist the set of allowed package names. */
    fun setAllowedApps(context: Context, apps: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_MUSIC_ALLOWED_APPS, apps).apply()
    }

    /** Returns whether app detection is enabled */
    fun isAppDetectionEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_APP_DETECTION_ENABLED, false)

    /** Set app detection enabled flag */
    fun setAppDetectionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_APP_DETECTION_ENABLED, enabled).apply()
    }

    /** Returns the set of package names eligible for app detection */
    fun getAppDetectionAllowedApps(context: Context): Set<String> =
        getPrefs(context).getStringSet(KEY_APP_DETECTION_ALLOWED_APPS, emptySet()) ?: emptySet()

    /** Persist the set of package names eligible for app detection */
    fun setAppDetectionAllowedApps(context: Context, apps: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_APP_DETECTION_ALLOWED_APPS, apps).apply()
    }
}
