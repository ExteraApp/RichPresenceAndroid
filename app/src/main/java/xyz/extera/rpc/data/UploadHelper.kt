package xyz.extera.rpc.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.security.MessageDigest

/**
 * Upload helper that caches file hashes to MXC URIs.
 * When asked to upload a file, it first computes a SHA-256 hash.
 * If that hash has been uploaded before, the cached mxc:// URL is returned immediately.
 * Otherwise the file is uploaded via MatrixApi and the resulting URI is cached.
 */
object UploadHelper {
    private const val PREFS_NAME = "upload_cache"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Upload a file, returning a cached MXC URI if the same content was uploaded before.
     *
     * @param context       Application context
     * @param credentials   The user's Matrix credentials (homeserver + access token)
     * @param file          The file to upload
     * @param mimeType      MIME type of the file (e.g. "image/png")
     * @param fileName      Optional filename sent to the server
     */
    suspend fun upload(
        context: Context,
        credentials: Credentials,
        file: File,
        mimeType: String,
        fileName: String? = null
    ): Result<String> {
        val hash = hashFile(file)

        // Check cache
        val cached = getPrefs(context).getString(hash, null)
        if (cached != null) {
            return Result.success(cached)
        }

        // Upload
        val result = MatrixApi.uploadFile(
            homeserverUrl = credentials.homeserverUrl,
            accessToken = credentials.accessToken,
            file = file,
            mimeType = mimeType,
            fileName = fileName
        )

        // Cache on success
        result.onSuccess { mxcUri ->
            getPrefs(context).edit().putString(hash, mxcUri).apply()
        }

        return result
    }

    /**
     * Compute the SHA-256 hex digest of a file.
     */
    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
