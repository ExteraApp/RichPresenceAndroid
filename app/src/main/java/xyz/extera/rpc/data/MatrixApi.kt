package xyz.extera.rpc.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.concurrent.TimeUnit

/**
 * Helper for Matrix client-server API operations.
 */
object MatrixApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val JSON_ACTIVITY_TYPE = "application/json; charset=utf-8".toMediaType()

    // --- Well-known discovery ---

    data class WellKnownResponse(
        @SerializedName("m.homeserver") val homeserver: WellKnownHomeserver?
    )

    data class WellKnownHomeserver(
        @SerializedName("base_url") val baseUrl: String?
    )

    /**
     * Resolve the homeserver base URL from a server name using .well-known discovery.
     * Falls back to https://<serverName> if .well-known is unavailable.
     */
    suspend fun resolveHomeserver(serverName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$serverName/.well-known/matrix/client"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val wellKnown = gson.fromJson(body, WellKnownResponse::class.java)
                    val baseUrl = wellKnown.homeserver?.baseUrl?.trimEnd('/')
                    if (!baseUrl.isNullOrBlank()) {
                        return@withContext Result.success(baseUrl)
                    }
                }
            }
            // Fallback: use the server name directly
            Result.success("https://$serverName")
        } catch (e: Exception) {
            // Fallback on network errors
            Result.success("https://$serverName")
        }
    }

    // --- Login ---

    data class LoginRequest(
        val type: String = "m.login.password",
        val identifier: LoginIdentifier,
        val password: String,
        @SerializedName("initial_device_display_name")
        val initialDeviceDisplayName: String = "Extera RPC"
    )

    data class LoginIdentifier(
        val type: String = "m.id.user",
        val user: String
    )

    data class LoginResponse(
        @SerializedName("user_id") val userId: String?,
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("home_server") val homeServer: String?,
        @SerializedName("device_id") val deviceId: String?
    )

    data class MatrixError(
        val errcode: String?,
        val error: String?
    )

    /**
     * Log in to a Matrix homeserver with username and password.
     * Returns Credentials on success.
     *
     * @param homeserverUrl The base URL of the homeserver (e.g. https://matrix.org)
     * @param username The local part of the user ID (e.g. "alice") or full Matrix ID
     * @param password The password
     */
    suspend fun login(
        homeserverUrl: String,
        username: String,
        password: String
    ): Result<Credentials> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = homeserverUrl.trimEnd('/')
            val url = "$baseUrl/_matrix/client/v3/login"

            val loginBody = LoginRequest(
                identifier = LoginIdentifier(user = username),
                password = password
            )
            val json = gson.toJson(loginBody)
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val loginResponse = gson.fromJson(body, LoginResponse::class.java)
                if (loginResponse.accessToken != null && loginResponse.userId != null) {
                    Result.success(
                        Credentials(
                            homeserverUrl = baseUrl,
                            accessToken = loginResponse.accessToken,
                            userId = loginResponse.userId
                        )
                    )
                } else {
                    Result.failure(Exception("Login response missing access_token or user_id"))
                }
            } else {
                val error = if (body != null) {
                    try {
                        val matrixError = gson.fromJson(body, MatrixError::class.java)
                        matrixError.error ?: "Unknown error (${response.code})"
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                } else {
                    "HTTP ${response.code}"
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- File upload ---

    data class UploadResponse(
        @SerializedName("content_uri") val contentUri: String?
    )

    /**
     * Upload a file to the Matrix content repository.
     * Returns the MXC URI (e.g. mxc://server/mediaId) on success.
     *
     * @param homeserverUrl The base URL of the homeserver
     * @param accessToken   The user's access token
     * @param file          The file to upload
     * @param mimeType      The MIME type of the file (e.g. "image/png")
     * @param fileName      Optional filename to send to the server
     */
    suspend fun uploadFile(
        homeserverUrl: String,
        accessToken: String,
        file: java.io.File,
        mimeType: String,
        fileName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = homeserverUrl.trimEnd('/')
            val urlBuilder = StringBuilder("$baseUrl/_matrix/media/v3/upload")
            if (!fileName.isNullOrBlank()) {
                urlBuilder.append("?filename=")
                urlBuilder.append(java.net.URLEncoder.encode(fileName, "UTF-8"))
            }

            val requestBody = file.asRequestBody(mimeType.toMediaType())

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val uploadResponse = gson.fromJson(body, UploadResponse::class.java)
                if (!uploadResponse.contentUri.isNullOrBlank()) {
                    Result.success(uploadResponse.contentUri)
                } else {
                    Result.failure(Exception("Upload response missing content_uri"))
                }
            } else {
                val error = if (body != null) {
                    try {
                        val matrixError = gson.fromJson(body, MatrixError::class.java)
                        matrixError.error ?: "Unknown error (${response.code})"
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                } else {
                    "HTTP ${response.code}"
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Rich Presence Delete ---
    /**
     * Delete the custom Rich Presence field for the given user.
     * Endpoint: DELETE /_matrix/client/v3/profile/{userId}/com.ip-logger.msc4320.rpc
     *
     * @param homeserverUrl Base URL of the homeserver (e.g. https://matrix.org)
     * @param accessToken   User's access token
     * @param userId        Full Matrix user ID (e.g. "@alice:matrix.org")
     */
    suspend fun deleteRichPresence(
        homeserverUrl: String,
        accessToken: String,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = homeserverUrl.trimEnd('/')
            // URL‑encode the userId for the path segment
            val encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8")
            val url = "$baseUrl/_matrix/client/v3/profile/$encodedUserId/com.ip-logger.msc4320.rpc"

            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = if (body != null) {
                    try {
                        val matrixError = gson.fromJson(body, MatrixError::class.java)
                        matrixError.error ?: "Unknown error (${response.code})"
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                } else {
                    "HTTP ${response.code}"
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class RpcActivity(
        val name: String,
        val details: String
    )

    suspend fun setRpcActivity(
        homeserverUrl: String,
        accessToken: String,
        userId: String,
        activity: RpcActivity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = homeserverUrl.trimEnd('/')
            val encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8")
            val url = "$baseUrl/_matrix/client/v3/profile/$encodedUserId/com.ip-logger.msc4320.rpc"

            val innerMap = mutableMapOf(
                "type" to "com.ip-logger.msc4320.rpc.activity",
                "name" to activity.name,
                "details" to activity.details
            )

            val bodyMap = mapOf("com.ip-logger.msc4320.rpc" to innerMap)
            val json = gson.toJson(bodyMap)
            val requestBody = json.toRequestBody(JSON_ACTIVITY_TYPE)

            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = if (body != null) {
                    try {
                        val matrixError = gson.fromJson(body, MatrixError::class.java)
                        matrixError.error ?: "Unknown error (${response.code})"
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                } else {
                    "HTTP ${response.code}"
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Rich Presence Set Media ---

    data class RpcMedia(
        val artist: String,
        val album: String,
        val track: String,
        @SerializedName("cover_art") val coverArt: String? = null,
        val player: String? = null
    )

    /**
     * Set the Rich Presence to a media (music) state.
     * Sends PUT to /_matrix/client/v3/profile/{userId}/com.ip-logger.msc4320.rpc
     *
     * @param homeserverUrl Base URL of the homeserver
     * @param accessToken   User's access token
     * @param userId        Full Matrix user ID
     * @param media         Media info to publish
     */
    suspend fun setRpcMedia(
        homeserverUrl: String,
        accessToken: String,
        userId: String,
        media: RpcMedia
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = homeserverUrl.trimEnd('/')
            val encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8")
            val url = "$baseUrl/_matrix/client/v3/profile/$encodedUserId/com.ip-logger.msc4320.rpc"

            val innerMap = mutableMapOf<String, String>(
                "type" to "com.ip-logger.msc4320.rpc.media",
                "artist" to media.artist,
                "album" to media.album,
                "track" to media.track
            )
            if (media.coverArt != null) innerMap["cover_art"] = media.coverArt
            if (media.player != null) innerMap["player"] = media.player

            val bodyMap = mapOf("com.ip-logger.msc4320.rpc" to innerMap)
            val json = gson.toJson(bodyMap)
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = if (body != null) {
                    try {
                        val matrixError = gson.fromJson(body, MatrixError::class.java)
                        matrixError.error ?: "Unknown error (${response.code})"
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                } else {
                    "HTTP ${response.code}"
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

