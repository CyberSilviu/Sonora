package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.metrolist.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String, // "foss" or "gms"
)

enum class ApkInstallResult {
    LAUNCHED_INSTALLER,
    NEEDS_PERMISSION,
    FAILED,
}

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set

    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()

    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    const val GITHUB_REPO_URL = "https://github.com/CyberSilviu/Sonora"
    private const val GITHUB_API_BASE = "https://api.github.com/repos/CyberSilviu/Sonora"

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("v").substringBefore("+").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.removePrefix("v").substringBefore("+").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(v1Parts.size, v2Parts.size)

        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * Checks if the latest version is newer than the current version.
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean =
        compareVersions(latestVersion, currentVersion) > 0

    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    internal fun parseReleaseJson(response: String): ReleaseInfo {
        val json = JSONObject(response)
        val tagName = json.optString("tag_name")
        val rawName = json.optString("name")
        val versionName =
            rawName
                .removePrefix("Sonora ")
                .removePrefix("v")
                .ifBlank { tagName.removePrefix("v") }
        val description = json.optString("body").takeUnless { json.isNull("body") }.orEmpty()
        return ReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            description = description,
            releaseDate = json.optString("published_at"),
            assets = parseAssets(json.optJSONArray("assets") ?: JSONArray()),
        )
    }

    /**
     * Parse release assets from GitHub API response.
     */
    internal fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()

        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue

            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.optLong("size", 0L)

            val (arch, variant) =
                when {
                    name.equals("Sonora.apk", ignoreCase = true) -> "universal" to "foss"
                    name.equals("Sonora-foss.apk", ignoreCase = true) -> "universal" to "foss"
                    name.equals("Sonora-with-Google-Cast.apk", ignoreCase = true) -> "universal" to "gms"
                    name.contains("gms", ignoreCase = true) || name.contains("Google-Cast", ignoreCase = true) ->
                        "universal" to "gms"
                    name.startsWith("app-") && name.endsWith("-release.apk") -> {
                        val parsedArch = name.removePrefix("app-").removeSuffix("-release.apk")
                        parsedArch to "foss"
                    }
                    else -> "universal" to "foss"
                }

            assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
        }

        return assets
    }

    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }

                val response = client.get("$GITHUB_API_BASE/releases/latest").bodyAsText()
                val releaseInfo = parseReleaseJson(response)

                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }
        }

    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }

                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true

                while (hasMore && page <= 10) {
                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30").bodyAsText()
                    val json = JSONArray(response)

                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }

                    for (i in 0 until json.length()) {
                        releases.add(parseReleaseJson(json.getJSONObject(i).toString()))
                    }

                    page++
                }

                cachedAllReleases = releases
                releases
            }
        }

    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()

        return releaseInfo.assets
            .find { it.architecture == currentArch && it.variant == currentVariant }
            ?.downloadUrl
            ?: releaseInfo.assets.find { it.variant == currentVariant }?.downloadUrl
            ?: releaseInfo.assets.firstOrNull()?.downloadUrl
    }

    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shouldFetch =
                    forceRefresh ||
                        (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS

                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, cachedReleaseInfo!!.versionName)
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }

                val releaseInfo = getLatestRelease(forceRefresh = true).getOrThrow()
                val hasUpdate = isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, releaseInfo.versionName)
                releaseInfo to hasUpdate
            }
        }

    suspend fun downloadApkToCache(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                updatesDir.listFiles()?.forEach { it.delete() }
                val targetFile = File(updatesDir, "sonora-update.apk")

                val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Sonora-Updater")
                }

                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        error("HTTP ${connection.responseCode}")
                    }
                    val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
                    var downloaded = 0L
                    connection.inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalBytes > 0L) {
                                    onProgress((downloaded.toFloat() / totalBytes).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    onProgress(1f)
                    targetFile
                } finally {
                    connection.disconnect()
                }
            }
        }

    fun installApk(context: Context, apkFile: File): ApkInstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val permIntent =
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(permIntent) }
            return ApkInstallResult.NEEDS_PERMISSION
        }

        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apkFile)
            val installIntent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(installIntent)
            ApkInstallResult.LAUNCHED_INSTALLER
        }.getOrElse {
            ApkInstallResult.FAILED
        }
    }

    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo
}
