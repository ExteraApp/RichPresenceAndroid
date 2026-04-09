package xyz.extera.rpc.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.core.content.edit

/**
 * Simple data class representing stored Matrix credentials.
 */
data class Credentials(
    val homeserverUrl: String,
    val accessToken: String,
    val userId: String
)

/**
 * Helper for persisting and retrieving Matrix credentials using EncryptedSharedPreferences.
 */
object CredentialsStore {
    private const val PREFS_NAME = "matrix_credentials"
    private const val KEY_HOMESERVER = "homeserver_url"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Returns stored credentials or null if not set */
    fun getCredentials(context: Context): Credentials? {
        val prefs = getPrefs(context)
        val homeserver = prefs.getString(KEY_HOMESERVER, null)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        return if (homeserver != null && token != null && userId != null) {
            Credentials(homeserver, token, userId)
        } else {
            null
        }
    }

    /** Save credentials */
    fun saveCredentials(context: Context, credentials: Credentials) {
        getPrefs(context).edit {
            putString(KEY_HOMESERVER, credentials.homeserverUrl)
            putString(KEY_ACCESS_TOKEN, credentials.accessToken)
            putString(KEY_USER_ID, credentials.userId)
        }
    }

    /** Clear stored credentials */
    fun clear(context: Context) {
        getPrefs(context).edit { clear() }
    }
}
