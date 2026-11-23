package com.sangam.messenger.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sangam.messenger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater. Checks the public Sangam-releases mirror repo on GitHub
 * (no login needed) for a newer version, downloads the APK into the app's
 * private cache, and hands it to the Android installer with the user's
 * confirmation. No secrets are involved anywhere in this flow.
 */
object Updater {

    private val RELEASES_URLS = listOf(
        "https://api.github.com/repos/sekelan-ideas/Sangam-Messenger-Updates/releases/latest",
        "https://api.github.com/repos/sekelan-ideas/Sangam-releases/releases/latest",
        "https://api.github.com/repos/sekelan-ideas/Sangam/releases/latest"
    )

    data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

    @Serializable
    private data class GhAsset(
        @SerialName("browser_download_url") val url: String = ""
    )

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tag: String = "",
        val body: String? = null,
        val assets: List<GhAsset> = emptyList()
    )

    /** Returns update info if a newer release exists on the public mirror repo. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        for (url in RELEASES_URLS) {
            val info = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (conn.responseCode != 200) return@runCatching null
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val rel = Json { ignoreUnknownKeys = true }.decodeFromString<GhRelease>(body)
                    val remote = rel.tag.removePrefix("v")
                    val apkAsset = rel.assets.firstOrNull { it.url.endsWith(".apk", ignoreCase = true) } ?: rel.assets.firstOrNull()
                    if (apkAsset == null || !isNewer(remote, BuildConfig.VERSION_NAME)) null
                    else UpdateInfo(remote, apkAsset.url, rel.body?.trim().orEmpty())
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            if (info != null) return@withContext info
        }
        null
    }

    /** Compares dotted version strings like 1.2.3 vs 1.10.0. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i) ?: 0
            val y = b.getOrNull(i) ?: 0
            if (x != y) return x > y
        }
        return false
    }

    /** Downloads the APK into the app's private cache directory. */
    suspend fun download(apkUrl: String, context: Context): File = withContext(Dispatchers.IO) {
        val dest = File(context.cacheDir, "sangam-update.apk")
        val conn = URL(apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 120000
        try {
            conn.inputStream.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        } finally {
            conn.disconnect()
        }
        dest
    }

    /** Opens the Android installer; the user taps Install to confirm. */
    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
