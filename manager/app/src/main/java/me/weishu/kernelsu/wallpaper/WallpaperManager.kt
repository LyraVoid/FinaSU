package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import com.materialkolor.ktx.themeColorOrNull
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import java.util.concurrent.ConcurrentHashMap

/**
 * File storage and derived-color extraction for the custom wallpaper.
 *
 * Blocking helpers ([computeLuminance], [computeSeed], [applyFile]) must be called off the main
 * thread; the public entry points already hop to [Dispatchers.IO].
 */
object WallpaperManager {

    private const val FILENAME_BASE = "wallpaper"
    private const val WORK_CARD_FILENAME_BASE = "work_card_background"
    private const val PAGE_HOME_FILENAME_BASE = "wallpaper_home"
    private const val PAGE_PLUGIN_FILENAME_BASE = "wallpaper_plugin"
    private const val PAGE_SUPERUSER_FILENAME_BASE = "wallpaper_superuser"
    private const val PAGE_MODULE_FILENAME_BASE = "wallpaper_module"
    private const val PAGE_SETTINGS_FILENAME_BASE = "wallpaper_settings"
    private val KNOWN_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

    private val surfaceLocks = ConcurrentHashMap<SurfaceId, Mutex>()

    /** Copies the picked image into app storage, extracts its color/luminance and enables it. */
    suspend fun save(context: Context, source: Uri): Boolean {
        val revision = WallpaperConfig.switchRevision
        return withContext(Dispatchers.IO) {
            runCatching {
                val ext = resolveExtension(context, source)
                // Stage then rename, so a failed copy never deletes the wallpaper already in place.
                val target = replaceSurfaceImage(context.filesDir, FILENAME_BASE, ext) { staged ->
                    copyToFile(context, source, staged)
                }
                clearOldFiles(context, except = target)
                val luminance = computeLuminance(target) ?: -1f
                val seed = computeSeed(target) ?: 0
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    WallpaperConfig.updateUri("${Uri.fromFile(target)}?revision=${UUID.randomUUID()}")
                    WallpaperConfig.updateDerivedLuminance(luminance)
                    WallpaperConfig.updateDerivedSeed(seed)
                    WallpaperConfig.activatePickedImage(revision)
                    WallpaperConfig.save(context)
                }
                true
            }.getOrDefault(false)
        }
    }

    /** Deletes the stored image and turns the wallpaper off, keeping the user's slider values. */
    fun clear(context: Context) {
        clearOldFiles(context)
        WallpaperConfig.updateUri(null)
        WallpaperConfig.updateEnabled(false)
        WallpaperConfig.updateDerivedSeed(0)
        WallpaperConfig.updateDerivedLuminance(-1f)
        WallpaperConfig.save(context, durable = true)
    }

    /** Points the config at [target] and (re)derives luminance + seed. Blocking. */
    fun applyFile(context: Context, target: File) {
        val stamped = "${Uri.fromFile(target)}?t=${System.currentTimeMillis()}"
        WallpaperConfig.updateUri(stamped)
        WallpaperConfig.updateDerivedLuminance(computeLuminance(target) ?: -1f)
        WallpaperConfig.updateDerivedSeed(computeSeed(target) ?: 0)
        WallpaperConfig.updateEnabled(true)
        WallpaperConfig.save(context, durable = true)
    }

    /** Copies the picked image into app storage and points the grid work card at it. */
    suspend fun saveWorkCardBackground(context: Context, source: Uri): Boolean =
        saveSurfaceImage(context, SurfaceRegistry.GRID_WORK_CARD, source)

    /** Deletes the stored work-card image and turns the feature off, keeping the user's slider values. */
    suspend fun clearWorkCardBackground(context: Context) =
        clearSurfaceImage(context, SurfaceRegistry.GRID_WORK_CARD)

    /**
     * Copies the picked image into app storage and points surface [id] at it.
     *
     * When [enableParent] is set and [id] is a child of a grouped feature, the parent's master
     * switch is turned on too, so a fresh pick is visible without a second step. Theme import
     * passes false so it can restore the parent's own explicit enabled bit afterwards.
     * [validateImage] remains enabled for interactive picks; imports copy optional resources like FP.
     */
    suspend fun saveSurfaceImage(
        context: Context,
        id: SurfaceId,
        source: Uri,
        enableParent: Boolean = true,
        validateImage: Boolean = true,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val stem = surfaceStem(id) ?: return@withContext false
            surfaceLocks.getOrPut(id) { Mutex() }.withLock {
                try {
                    val ext = resolveExtension(context, source)
                    val target = replaceSurfaceImage(context.filesDir, stem, ext) {
                        if (!copyToFile(context, source, it)) {
                            false
                        } else if (validateImage) {
                            val probe = decodeSampled(it, 64)
                            val valid = probe != null
                            probe?.recycle()
                            valid
                        } else true
                    }
                    // Once the file is replaced, publish even if the initiating UI is disposed.
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        val stamped = "${Uri.fromFile(target)}?revision=${UUID.randomUUID()}"
                        SurfaceStore.update(id) { it.copy(imageUri = stamped, enabled = true) }
                        if (enableParent) {
                            SurfaceRegistry.descriptor(id)?.parentId?.let { parent ->
                                SurfaceStore.update(parent) { it.copy(enabled = true) }
                            }
                        }
                        WallpaperConfig.save(context)
                    }
                    clearSurfaceFiles(context, stem, except = target)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            }
        }

    /** Serializes removal with replacement, keeping the user's slider values. */
    suspend fun clearSurfaceImage(context: Context, id: SurfaceId) {
        val stem = surfaceStem(id) ?: return
        surfaceLocks.getOrPut(id) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                clearSurfaceFiles(context, stem)
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    SurfaceStore.update(id) { it.copy(imageUri = null, enabled = false) }
                    WallpaperConfig.save(context)
                }
            }
        }
    }

    /** The currently stored image file for surface [id], if any. */
    fun currentSurfaceFile(context: Context, id: SurfaceId): File? {
        val path = SurfaceStore.config(id).imageUri?.let { it.toUri().path } ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0L) file else null
    }

    /** File-name stem for surface [id], or null when the descriptor declares none. */
    private fun surfaceStem(id: SurfaceId): String? =
        SurfaceRegistry.descriptor(id)?.storageStem

    /** Deletes every stored file variant for a surface background. */
    private fun clearSurfaceFiles(context: Context, stem: String, except: File? = null) {
        KNOWN_EXTENSIONS.forEach { ext ->
            val file = File(context.filesDir, "$stem$ext")
            if (file != except && file.exists()) file.delete()
        }
    }

    /**
     * Copies the picked image into app storage as the background for [page] (0..4).
     *
     * Interactive picks enable multi mode unless a newer switch decision superseded the pick.
     * Imports pass [activate] = false to preserve the theme's explicit switch values.
     * Unknown page indexes are ignored.
     */
    suspend fun savePageBackground(
        context: Context,
        page: Int,
        source: Uri,
        activate: Boolean = true,
    ): Boolean {
        val revision = WallpaperConfig.switchRevision
        return withContext(Dispatchers.IO) {
            val base = pageFilenameBase(page) ?: return@withContext false
            runCatching {
                val ext = resolveExtension(context, source)
                // Stage then rename, so a failed copy never deletes the page image already in place.
                val target = replaceSurfaceImage(context.filesDir, base, ext) { staged ->
                    copyToFile(context, source, staged)
                }
                clearPageFiles(context, base, except = target)
                val luminance = computeLuminance(target) ?: -1f
                val seed = computeSeed(target) ?: 0
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    updatePageUri(page, "${Uri.fromFile(target)}?revision=${UUID.randomUUID()}")
                    updatePageLuminance(page, luminance)
                    updatePageSeed(page, seed)
                    if (activate) WallpaperConfig.activatePickedImage(revision, multi = true)
                    WallpaperConfig.save(context)
                }
                true
            }.getOrDefault(false)
        }
    }

    /** Deletes [page]'s stored image and clears its URI/derived values. Unknown indexes are ignored. */
    fun clearPageBackground(context: Context, page: Int) {
        val base = pageFilenameBase(page) ?: return
        clearPageFiles(context, base)
        updatePageUri(page, null)
        updatePageLuminance(page, -1f)
        updatePageSeed(page, 0)
        WallpaperConfig.save(context)
    }

    /** File-name stem for a page background, or null when [page] is out of range. */
    private fun pageFilenameBase(page: Int): String? = when (page) {
        WallpaperConfig.PAGE_HOME -> PAGE_HOME_FILENAME_BASE
        WallpaperConfig.PAGE_PLUGIN -> PAGE_PLUGIN_FILENAME_BASE
        WallpaperConfig.PAGE_SUPERUSER -> PAGE_SUPERUSER_FILENAME_BASE
        WallpaperConfig.PAGE_MODULE -> PAGE_MODULE_FILENAME_BASE
        WallpaperConfig.PAGE_SETTINGS -> PAGE_SETTINGS_FILENAME_BASE
        else -> null
    }

    private fun updatePageUri(page: Int, value: String?) {
        when (page) {
            WallpaperConfig.PAGE_HOME -> WallpaperConfig.updateHomeBackgroundUri(value)
            WallpaperConfig.PAGE_PLUGIN -> WallpaperConfig.updatePluginBackgroundUri(value)
            WallpaperConfig.PAGE_SUPERUSER -> WallpaperConfig.updateSuperuserBackgroundUri(value)
            WallpaperConfig.PAGE_MODULE -> WallpaperConfig.updateModuleBackgroundUri(value)
            WallpaperConfig.PAGE_SETTINGS -> WallpaperConfig.updateSettingsBackgroundUri(value)
        }
    }

    private fun updatePageLuminance(page: Int, value: Float) {
        when (page) {
            WallpaperConfig.PAGE_HOME -> WallpaperConfig.updateHomeBackgroundLuminance(value)
            WallpaperConfig.PAGE_PLUGIN -> WallpaperConfig.updatePluginBackgroundLuminance(value)
            WallpaperConfig.PAGE_SUPERUSER -> WallpaperConfig.updateSuperuserBackgroundLuminance(value)
            WallpaperConfig.PAGE_MODULE -> WallpaperConfig.updateModuleBackgroundLuminance(value)
            WallpaperConfig.PAGE_SETTINGS -> WallpaperConfig.updateSettingsBackgroundLuminance(value)
        }
    }

    private fun updatePageSeed(page: Int, value: Int) {
        when (page) {
            WallpaperConfig.PAGE_HOME -> WallpaperConfig.updateHomeBackgroundSeed(value)
            WallpaperConfig.PAGE_PLUGIN -> WallpaperConfig.updatePluginBackgroundSeed(value)
            WallpaperConfig.PAGE_SUPERUSER -> WallpaperConfig.updateSuperuserBackgroundSeed(value)
            WallpaperConfig.PAGE_MODULE -> WallpaperConfig.updateModuleBackgroundSeed(value)
            WallpaperConfig.PAGE_SETTINGS -> WallpaperConfig.updateSettingsBackgroundSeed(value)
        }
    }

    /** Deletes every stored file variant for a page background, except [except]. */
    private fun clearPageFiles(context: Context, base: String, except: File? = null) {
        KNOWN_EXTENSIONS.forEach { ext ->
            val file = File(context.filesDir, "$base$ext")
            if (file != except && file.exists()) file.delete()
        }
    }

    /** Backfills color/luminance for a wallpaper that was restored without derived values. */
    fun refreshDerivedIfMissing(context: Context) {
        val path = WallpaperConfig.uri?.let { it.toUri().path } ?: return
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return
        var changed = false
        if (WallpaperConfig.derivedLuminance < 0f) {
            WallpaperConfig.updateDerivedLuminance(computeLuminance(file) ?: -1f)
            changed = true
        }
        if (WallpaperConfig.derivedSeed == 0) {
            WallpaperConfig.updateDerivedSeed(computeSeed(file) ?: 0)
            changed = true
        }
        if (changed) WallpaperConfig.save(context)
    }

    /** Copies [source] into app storage (replacing any existing wallpaper) without touching config. */
    fun replaceFile(context: Context, source: File, extension: String): File {
        clearOldFiles(context)
        val target = File(context.filesDir, "$FILENAME_BASE$extension")
        source.copyTo(target, overwrite = true)
        return target
    }

    /** The currently stored wallpaper file, if any. */
    fun currentFile(context: Context): File? {
        val path = WallpaperConfig.uri?.let { it.toUri().path } ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0L) file else null
    }

    /** Copies [source] into app storage (replacing any existing work-card background). */
    fun replaceWorkCardFile(context: Context, source: File, extension: String): File {
        clearOldWorkCardFiles(context)
        val target = File(context.filesDir, "$WORK_CARD_FILENAME_BASE$extension")
        source.copyTo(target, overwrite = true)
        return target
    }

    /** The currently stored work-card background file, if any. */
    fun currentWorkCardFile(context: Context): File? =
        currentSurfaceFile(context, SurfaceRegistry.GRID_WORK_CARD)

    /**
     * Best-effort extension (with leading dot) for [source], so a GIF stays a GIF.
     *
     * The content itself is checked first, because a picked or imported file may carry a wrong or
     * missing name: `contentResolver.getType` is null for `file://` URIs (the path a theme import
     * comes through), and some theme packages store a PNG/GIF under a `.jpg` entry name. Only when
     * the bytes are not a recognized image does it fall back to the MIME type, then the URI name.
     */
    fun resolveExtension(context: Context, source: Uri): String =
        sniffExtension(context, source)
            .ifEmpty { getFileExtension(context.contentResolver.getType(source)) }
            .ifEmpty { nameExtension(source) }
            .ifEmpty { ".jpg" }

    /** Extension for a MIME type, or "" when the type is unknown. */
    fun getFileExtension(mime: String?): String = when {
        mime == null -> ""
        mime.contains("png", ignoreCase = true) -> ".png"
        mime.contains("webp", ignoreCase = true) -> ".webp"
        mime.contains("gif", ignoreCase = true) -> ".gif"
        mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> ".jpg"
        else -> ""
    }

    /**
     * Extension (with leading dot) matching [file]'s actual bytes, falling back to its name.
     *
     * Used on export so an asset that was stored under a wrong name (a GIF kept as `.jpg`) is still
     * written into the container under the right extension.
     */
    fun resolveFileExtension(file: File): String {
        val sniffed = try {
            file.inputStream().use { input ->
                val head = ByteArray(12)
                classifyHead(head, input.read(head))
            }
        } catch (_: Throwable) {
            ""
        }
        if (sniffed.isNotEmpty()) return sniffed
        val byName = "." + file.extension.lowercase()
        return if (byName in KNOWN_EXTENSIONS) byName else ".jpg"
    }

    private fun nameExtension(source: Uri): String {
        val name = source.path?.substringAfterLast('/') ?: return ""
        val ext = "." + name.substringAfterLast('.', "").lowercase()
        return if (ext in KNOWN_EXTENSIONS) ext else ""
    }

    private fun sniffExtension(context: Context, source: Uri): String = try {
        context.contentResolver.openInputStream(source)?.use { input ->
            val head = ByteArray(12)
            classifyHead(head, input.read(head))
        } ?: ""
    } catch (_: Throwable) {
        ""
    }

    /** Identifies an image format from its leading bytes, or "" when unrecognized. */
    private fun classifyHead(head: ByteArray, read: Int): String = when {
        read < 12 -> ""
        head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() -> ".gif"
        head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> ".png"
        head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> ".jpg"
        head[0] == 'R'.code.toByte() && head[8] == 'W'.code.toByte() -> ".webp"
        else -> ""
    }

    /**
     * Decodes [file] scaled so its longest side is at most [maxDimension].
     *
     * Exact rather than a power-of-two sample step, which halved a 3120px picture to 1560px for
     * the panel to stretch back up. [ImageDecoder] also honours the EXIF orientation.
     */
    fun decodeSampled(file: File, maxDimension: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val longest = if (width > height) width else height
                if (maxDimension > 0 && longest > maxDimension) {
                    val ratio = maxDimension.toFloat() / longest
                    decoder.setTargetSize(
                        (width * ratio).roundToInt().coerceAtLeast(1),
                        (height * ratio).roundToInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Average perceived luminance (0..1) of [file], or null when it cannot be decoded. */
    fun computeLuminance(file: File): Float? {
        val bitmap = decodeSampled(file, 64) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) {
            bitmap.recycle()
            return null
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        var sum = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        }
        return (sum / pixels.size).toFloat()
    }

    /** Dominant seed color (ARGB) of [file], or null when it cannot be decoded. */
    fun computeSeed(file: File): Int? {
        val bitmap = decodeSampled(file, 256) ?: return null
        return try {
            bitmap.asImageBitmap().themeColorOrNull()?.toArgb()
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun copyToFile(context: Context, source: Uri, target: File): Boolean = try {
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            target.length() > 0L
        } ?: false
    } catch (e: CancellationException) {
        target.delete()
        throw e
    } catch (_: Exception) {
        target.delete()
        false
    }

    /** Deletes every stored wallpaper variant, except [except]. */
    private fun clearOldFiles(context: Context, except: File? = null) {
        KNOWN_EXTENSIONS.forEach { ext ->
            val file = File(context.filesDir, "$FILENAME_BASE$ext")
            if (file != except && file.exists()) file.delete()
        }
    }

    private fun clearOldWorkCardFiles(context: Context) {
        KNOWN_EXTENSIONS.forEach { ext ->
            val file = File(context.filesDir, "$WORK_CARD_FILENAME_BASE$ext")
            if (file.exists()) file.delete()
        }
    }
}
