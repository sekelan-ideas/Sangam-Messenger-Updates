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

object Updater {

    private const val MIRROR_REPO = "sekelan-ideas/Sangam-Messenger-Updates"
    private val RELEASES_URLS = listOf(
        "https://api.github.com/repos/$MIRROR_REPO/releases/latest",
        "https://api.github.com/repos/sekelan-ideas/Sangam-releases/releases/latest"
    )
    private val USER_AGENT get() = "Sangam-Android/${BuildConfig.VERSION_NAME}"

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
        // 1. Primary: GitHub Releases API (with required User-Agent header)
        for (url in RELEASES_URLS) {
            val info = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", USER_AGENT)
                try {
                    if (conn.responseCode != 200) return@runCatching null
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val rel = Json { ignoreUnknownKeys = true }.decodeFromString<GhRelease>(body)
                    val remote = rel.tag.removePrefix("v").removePrefix("V").trim()
                    val apkAsset = rel.assets.firstOrNull { it.url.endsWith(".apk", ignoreCase = true) }
                        ?: rel.assets.firstOrNull()
                    if (apkAsset == null || !isNewer(remote, BuildConfig.VERSION_NAME)) null
                    else UpdateInfo(remote, apkAsset.url, rel.body?.trim().orEmpty())
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            if (info != null) return@withContext info
        }

        // 2. Fallback: zero-rate-limit redirect check (works even under carrier CGNAT 403 limits)
        runCatching {
            val conn = URL("https://github.com/$MIRROR_REPO/releases/latest").openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                val loc = conn.getHeaderField("Location") ?: return@runCatching null
                val tag = loc.substringAfter("/releases/tag/", "").substringBefore("?").trim()
                if (tag.isBlank()) return@runCatching null
                val remote = tag.removePrefix("v").removePrefix("V").trim()
                if (!isNewer(remote, BuildConfig.VERSION_NAME)) return@runCatching null
                val apkUrl = "https://github.com/$MIRROR_REPO/releases/download/$tag/Sangam.apk"
                UpdateInfo(remote, apkUrl, "New Sangam release v$remote is available.")
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
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

    /** Downloads the APK into the app's private cache directory (handling cross-host redirects). */
    suspend fun download(apkUrl: String, context: Context): File = withContext(Dispatchers.IO) {
        val dest = File(context.cacheDir, "sangam-update.apk")
        var currentUrl = apkUrl
        var redirects = 0
        while (redirects < 6) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20000
            conn.readTimeout = 120000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                val code = conn.responseCode
                if (code in 301..308) {
                    val next = conn.getHeaderField("Location") ?: error("Empty redirect")
                    currentUrl = next
                    redirects++
                    continue
                }
                if (code !in 200..299) error("Download failed (HTTP $code)")
                conn.inputStream.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                return@withContext dest
            } finally {
                conn.disconnect()
            }
        }
        error("Too many redirects while downloading update")
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