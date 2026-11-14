package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID

class PreparedTheme internal constructor(
    internal val directory: File,
    internal val json: JSONObject,
    internal val files: Map<String, File>,
    val metadata: ThemeMetadata,
    val warnings: List<String>,
    val available: Set<ThemePart>,
) : Closeable {
    val resourceCount: Int get() = files.size
    val previewFile: File? get() = files.entries.firstOrNull {
        it.key.substringBefore('.') in setOf("background_home", "background_kernel", "background", "title_image")
    }?.value
    override fun close() { directory.deleteRecursively() }
}

/** File picker and store both prepare once, confirm the same immutable payload, then apply. */
object ThemeImportService {
    private val mutex = Mutex()

    suspend fun prepare(context: Context, uri: Uri): Result<PreparedTheme> {
        val directory = File(context.cacheDir, "fpt-${UUID.randomUUID()}")
        return try { withContext(Dispatchers.IO) {
            val (json, files) = context.contentResolver.openInputStream(uri)?.use {
                FptContainer.read(it, directory)
            } ?: error("Cannot read theme")
            val original = JSONObject(json.toString())
            val warnings = ThemeValidation.validate(json, files)
            File(directory, "original.json").writeText(original.toString())
            val available = themePartsIn(
                json,
                files.keys.mapTo(mutableSetOf(), ::zipStem),
                FolkThemeIO.assets.mapTo(mutableSetOf()) { it.base },
            )
            Result.success(PreparedTheme(directory, json, files, ThemeMetadata.read(original), warnings, available))
        } } catch (error: Exception) {
            directory.deleteRecursively()
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    suspend fun apply(
        context: Context,
        theme: PreparedTheme,
        selection: ThemeSelection = ThemeSelection.All,
    ): Result<Unit> = mutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching {
                check(theme.directory.isDirectory) { "Prepared theme has expired" }
                val backup = File(context.cacheDir, "fpt-backup-${UUID.randomUUID()}")
                val transaction = ThemeTransaction(context, backup)
                try {
                    val appliedParts = FolkThemeIO.applyAssets(context, theme.json, theme.files, selection)
                        .toMutableSet()
                    if (ThemePart.COLORS in selection) {
                        ThemeSettingsMapping.applyColors(theme.json)
                        appliedParts += ThemePart.COLORS
                    }
                    if (ThemePart.INTERFACE in selection) {
                        ThemeSettingsMapping.applyInterface(theme.json)
                        appliedParts += ThemePart.INTERFACE
                    }
                    if (ThemePart.LANGUAGE in selection) appliedParts += ThemePart.LANGUAGE
                    theme.metadata.save(context)
                    val passthrough = File(context.filesDir, "theme_passthrough")
                    check(!passthrough.exists() || passthrough.deleteRecursively())
                    check(passthrough.mkdirs())
                    File(theme.directory, "original.json").copyTo(File(passthrough, "theme.json"))
                    // Preserve FP-only pages/icons and unknown resources for a later FP re-import.
                    theme.files.forEach { (name, file) -> file.copyTo(File(passthrough, name)) }
                    File(passthrough, "applied.json").writeText(JSONObject().apply {
                        FolkThemeIO.assets
                            .filter { themePartOf(it.base) in appliedParts }
                            .forEach { it.writeConfig(this) }
                        FolkThemeIO.groups
                            .filter { it.part in appliedParts }
                            .forEach { it.writeConfig(this) }
                        if (ThemePart.COLORS in appliedParts) ThemeSettingsMapping.writeColors(this)
                        if (ThemePart.INTERFACE in appliedParts) ThemeSettingsMapping.writeInterface(this)
                        if (ThemePart.LANGUAGE in appliedParts) ThemeSettingsMapping.writeLanguage(this)
                    }.toString())
                    transaction.persist()
                    // Locale is the final operation because it can recreate the activity.
                    if (ThemePart.LANGUAGE in appliedParts) {
                        withContext(Dispatchers.Main) { ThemeSettingsMapping.applyLanguage(theme.json) }
                    }
                } catch (error: Exception) {
                    try { transaction.rollback() } catch (rollback: Exception) { error.addSuppressed(rollback) }
                    throw error
                } finally { backup.deleteRecursively() }
            }
        }
    }
}
