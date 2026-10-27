package com.balaji.provision.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.network.AppReleaseResponse
import com.balaji.provision.data.network.SupabaseApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    object Idle : UpdateState
    data class UpdateAvailable(
        val release: AppReleaseResponse,
        val isMandatory: Boolean
    ) : UpdateState
    data class Downloading(
        val release: AppReleaseResponse,
        val progressPercent: Int,
        val isMandatory: Boolean
    ) : UpdateState
    data class ReadyToInstall(
        val release: AppReleaseResponse,
        val apkFile: File,
        val isMandatory: Boolean
    ) : UpdateState
    data class Error(
        val message: String,
        val isMandatory: Boolean
    ) : UpdateState
}

/**
 * Fetches the latest published APK for this flavour and installs it through the
 * system package installer.
 *
 * The release record is public data, so it is treated as untrusted input: the
 * download URL must be HTTPS, the payload is capped at [MAX_APK_BYTES], and the
 * file name is derived from a sanitised version string so a crafted
 * `version_name` cannot escape the cache directory. The Android installer
 * additionally refuses any APK that is not signed with the installed app's
 * signing key.
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SupabaseApi,
    private val okHttpClient: OkHttpClient
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdates() {
        if (_updateState.value !is UpdateState.Idle) return

        withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.SUPABASE_ANON_KEY
                val audience = if (BuildConfig.IS_STAFF) "staff" else "customer"
                val response = api.getLatestAppRelease(
                    apiKey = apiKey,
                    auth = "Bearer $apiKey",
                    body = mapOf("p_audience" to audience)
                )

                if (!response.isSuccessful) {
                    logDebug("Update check failed with HTTP ${response.code()}")
                    return@withContext
                }

                val release = response.body() ?: return@withContext
                if (release.version_code <= BuildConfig.VERSION_CODE) return@withContext
                if (!release.apk_url.startsWith("https://", ignoreCase = true)) {
                    logDebug("Ignoring release ${release.version_code}: APK URL is not HTTPS")
                    return@withContext
                }

                val isMandatory = release.force_update ||
                    BuildConfig.VERSION_CODE < release.min_version_code

                // An optional update the user already declined is not shown again
                // for the same version code.
                if (!isMandatory && prefs.getInt(KEY_DISMISSED_VERSION, 0) >= release.version_code) {
                    return@withContext
                }

                _updateState.value = UpdateState.UpdateAvailable(release, isMandatory)
            } catch (e: Exception) {
                // A failed update check must never block the app.
                logDebug("Update check failed: ${e.message}")
            }
        }
    }

    suspend fun downloadAndInstall(release: AppReleaseResponse) {
        val isMandatory = (updateState.value as? UpdateState.UpdateAvailable)?.isMandatory ?: false

        if (!release.apk_url.startsWith("https://", ignoreCase = true)) {
            _updateState.value = UpdateState.Error(
                "This update was published with an insecure download link.",
                isMandatory
            )
            return
        }

        _updateState.value = UpdateState.Downloading(release, 0, isMandatory)

        var apkFile: File? = null
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(release.apk_url).build()

                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        _updateState.value = UpdateState.Error(
                            "Failed to download the update (HTTP ${response.code}).",
                            isMandatory
                        )
                        return@withContext
                    }

                    val contentLength = body.contentLength()
                    if (contentLength > MAX_APK_BYTES) {
                        _updateState.value = UpdateState.Error(
                            "The update file is larger than the supported limit.",
                            isMandatory
                        )
                        return@withContext
                    }

                    val downloadDir =
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                    downloadDir.mkdirs()
                    clearStaleDownloads(downloadDir)

                    val targetFile = File(downloadDir, downloadFileName(release))
                    apkFile = targetFile
                    var bytesReadTotal = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } >= 0) {
                                bytesReadTotal += bytes
                                if (bytesReadTotal > MAX_APK_BYTES) {
                                    throw IllegalStateException(
                                        "Update file exceeded the supported limit"
                                    )
                                }
                                output.write(buffer, 0, bytes)
                                if (contentLength > 0) {
                                    val progress =
                                        ((bytesReadTotal * 100) / contentLength).toInt()
                                    _updateState.value = UpdateState.Downloading(
                                        release,
                                        progress.coerceIn(0, 100),
                                        isMandatory
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    if (bytesReadTotal == 0L) {
                        targetFile.delete()
                        _updateState.value = UpdateState.Error(
                            "The downloaded update file was empty.",
                            isMandatory
                        )
                        return@withContext
                    }

                    _updateState.value = UpdateState.ReadyToInstall(release, targetFile, isMandatory)
                    triggerInstall(targetFile)
                }
            } catch (e: Exception) {
                // Never leave a partial file behind: a half-downloaded APK cannot
                // be installed and only wastes the user's storage.
                apkFile?.delete()
                _updateState.value = UpdateState.Error(
                    e.message ?: "Failed to download the update.",
                    isMandatory
                )
            }
        }
    }

    fun triggerInstall(apkFile: File) {
        val isMandatory = when (val state = _updateState.value) {
            is UpdateState.ReadyToInstall -> state.isMandatory
            is UpdateState.Error -> state.isMandatory
            else -> false
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                // Keep the downloaded file and the ReadyToInstall state so the
                // user can retry once the permission is granted.
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".toUri()
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(
                "Could not open the package installer. Install the update manually from Downloads.",
                isMandatory
            )
        }
    }

    /** Called when the user dismisses an optional update prompt. */
    fun dismiss() {
        val state = _updateState.value
        val release = when (state) {
            is UpdateState.UpdateAvailable -> state.release
            is UpdateState.ReadyToInstall -> state.release
            is UpdateState.Error -> null
            is UpdateState.Downloading -> null
            is UpdateState.Idle -> null
        }
        val isMandatory = when (state) {
            is UpdateState.UpdateAvailable -> state.isMandatory
            is UpdateState.ReadyToInstall -> state.isMandatory
            is UpdateState.Error -> state.isMandatory
            is UpdateState.Downloading -> state.isMandatory
            is UpdateState.Idle -> false
        }
        if (!isMandatory && release != null) {
            prefs.edit { putInt(KEY_DISMISSED_VERSION, release.version_code) }
        }
        _updateState.value = UpdateState.Idle
    }

    private fun downloadFileName(release: AppReleaseResponse): String {
        val safeVersion = release.version_name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
            .take(32)
            .ifBlank { "unknown" }
        return "balaji-update-v$safeVersion-${release.version_code}.apk"
    }

    private fun clearStaleDownloads(dir: File) {
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("balaji-update-") && it.extension == "apk" }
            ?.forEach { stale ->
                if (!stale.delete()) {
                    logDebug("Could not delete stale update file ${stale.name}")
                }
            }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AppUpdateManager"
        const val PREFS_NAME = "app_updates"
        const val KEY_DISMISSED_VERSION = "dismissed_version_code"
        const val MAX_APK_BYTES = 150L * 1024 * 1024
    }
}
