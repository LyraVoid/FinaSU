package me.weishu.kernelsu.ui.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import okhttp3.Request

/**
 * @author weishu
 * @date 2023/6/22.
 */
/** Downloads [url] and returns the downloaded file, or `null` when it failed. */
suspend fun download(
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {},
    onFailed: (String) -> Unit = {}
): Uri? {
    onDownloading()

    val downloadId = DownloadManager.enqueue(
        context = ksuApp,
        url = url,
        fileName = fileName,
        onCompleted = onDownloaded,
    )

    val lastState = DownloadManager.downloads
        .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
        .first { map ->
            val status = map[downloadId]?.status
            status == DownloadManager.Status.COMPLETED ||
                status == DownloadManager.Status.FAILED
        }[downloadId]

    if (lastState?.status == DownloadManager.Status.FAILED) {
        onFailed(lastState.error.orEmpty())
        return null
    }
    return lastState?.resultUri
}

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ksuApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}

/**
 * The release feed the manager checks for updates.
 *
 * It must point at FinaSU's own repository: pointing it upstream (tiann/KernelSU) compared a FinaSU
 * build against KernelSU's releases, so once upstream's version code overtook FinaSU's the manager
 * would have offered the KernelSU APK as a FinaSU update.
 */
private const val MANAGER_RELEASES_API = "https://api.github.com/repos/LyraVoid/FinaSU/releases/latest"

/** Release assets are named `<manager>_v<versionName>_<versionCode>-<buildType>.apk`. */
private val RELEASE_ASSET_NAME = Regex("v(.+?)_(\\d+)")

/**
 * Extracts the `(versionName, versionCode)` carried by a release asset name, or `null` when the
 * name is not an APK or does not carry a version code.
 */
internal fun parseReleaseAssetName(name: String): Pair<String, Int>? {
    if (!name.endsWith(".apk")) return null
    val match = RELEASE_ASSET_NAME.find(name) ?: return null
    val versionCode = match.groupValues[2].toIntOrNull() ?: return null
    return match.groupValues[1] to versionCode
}

fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    // default null value if failed
    val defaultValue = LatestVersionInfo()
    runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(MANAGER_RELEASES_API).build()).execute()
            .use { response ->
                if (!response.isSuccessful) {
                    return defaultValue
                }
                val body = response.body.string()
                val json = org.json.JSONObject(body)
                val changelog = json.optString("body")

                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val parsed = parseReleaseAssetName(asset.getString("name")) ?: continue
                    val downloadUrl = asset.getString("browser_download_url")

                    return LatestVersionInfo(
                        versionName = parsed.first,
                        versionCode = parsed.second,
                        downloadUrl = downloadUrl,
                        changelog = changelog,
                    )
                }

            }
    }
    return defaultValue
}
