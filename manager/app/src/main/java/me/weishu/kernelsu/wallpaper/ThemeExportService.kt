package me.weishu.kernelsu.wallpaper

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import org.json.JSONObject
import java.io.File

object ThemeExportService {
    /** Public folder, relative to the shared Downloads directory, where exported themes land. */
    val DOWNLOAD_DIR: String = "${Environment.DIRECTORY_DOWNLOADS}/FinaSU/Themes"

    /**
     * Writes [metadata]'s theme package into the shared Downloads collection under [DOWNLOAD_DIR].
     *
     * Unlike the SAF `CreateDocument` flow, the display name is preserved verbatim: the Downloads
     * provider used to append `.bin` because `.fpt` has no MIME mapping.
     */
    suspend fun exportToDownloads(
        context: Context,
        metadata: ThemeMetadata,
        displayName: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_DIR)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("Cannot create export file")
            try {
                export(context, uri, metadata).getOrThrow()
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            uri
        }
    }

    suspend fun export(context: Context, target: Uri, metadata: ThemeMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val retained = File(context.filesDir, "theme_passthrough")
                val original = File(retained, "theme.json").takeIf { it.isFile }
                    ?.let { JSONObject(it.readText()) } ?: JSONObject()
                val json = JSONObject(original.toString())
                json.remove("navIcons")
                FolkThemeIO.assets.forEach { it.writeConfig(json) }
                FolkThemeIO.groups.forEach { it.writeConfig(json) }
                ThemeSettingsMapping.write(json)
                metadata.write(json)
                val applied = File(retained, "applied.json").takeIf { it.isFile }
                    ?.let { JSONObject(it.readText()) }
                // An unsupported enum is retained until the corresponding local setting changes.
                listOf("customColor", "colorGenerationMode", "colorStandard", "colorStyle", "colorContrast",
                    "fontMode", "homeLayoutStyle", "statsTopLayout", "soundEffectScope").forEach { field ->
                    if (original.has(field) && applied != null &&
                        applied.opt(field) == json.opt(field) && original.opt(field) != applied.opt(field)) {
                        json.put(field, original.get(field))
                    }
                }
                // Retain unknown color tokens unless the user changed the mapped setting.
                if (original.has("customColor") &&
                    ThemeSettingsMapping.colors[original.optString("customColor")] ==
                    json.optInt(ThemeSettingsMapping.KEY_COLOR)) {
                    json.put("customColor", original.get("customColor"))
                }
                val entries = linkedMapOf<String, File>()
                val ownedBases = FolkThemeIO.assets.map { it.base }.toSet()
                val ownedNames = buildSet {
                    listOf("musicFilename", "soundEffectFilename").forEach { add(original.optString(it)) }
                    original.optJSONObject("navIcons")?.let { icons ->
                        BottomBarDestination.entries.map { it.themeKey }.forEach { add(icons.optString(it)) }
                    }
                }
                retained.listFiles()?.filter { it.isFile && it.name !in setOf("theme.json", "applied.json") &&
                    it.name.substringBefore('.') !in ownedBases && it.name !in ownedNames }
                    ?.forEach { entries[it.name] = it }
                FolkThemeIO.assets.forEach { asset ->
                    asset.currentFile(context)?.let { entries["${asset.base}.${asset.extension(it)}"] = it }
                }
                FolkThemeIO.groups.forEach { group -> group.currentFiles(context).forEach { (name, file) ->
                    require(name !in entries) { "Conflicting resource name: $name" }
                    entries[name] = file
                } }
                original.optJSONObject("navIcons")?.let { originalIcons ->
                    originalIcons.keys().forEach { destination ->
                        val name = originalIcons.optString(destination)
                        if (destination !in BottomBarDestination.entries.map { it.themeKey } && name in entries) {
                            val icons = json.optJSONObject("navIcons") ?: JSONObject().also { json.put("navIcons", it) }
                            icons.put(destination, name)
                        }
                    }
                }
                ThemeValidation.validate(JSONObject(json.toString()), entries)
                val output = context.contentResolver.openOutputStream(target) ?: error("Cannot write theme")
                output.use { FptContainer.write(json, entries, it) }
                metadata.save(context)
            }
        }
}
