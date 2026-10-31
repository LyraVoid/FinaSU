package me.weishu.kernelsu.data.backup.local

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import me.weishu.kernelsu.data.backup.backupResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.backup.BackupContent
import me.weishu.kernelsu.data.backup.BackupStorage
import me.weishu.kernelsu.data.backup.RemoteEntry
import me.weishu.kernelsu.data.backup.sha256
import me.weishu.kernelsu.ksuApp

/**
 * A [BackupStorage] backed by the public Downloads directory through MediaStore.
 *
 * Files live under `Download/FinaSU/` (see [LocalBackupLayout]) so they stay visible to the user and
 * survive app uninstall. Every entry is addressed by its storage-relative path; the content is
 * streamed on write, so partition images are never buffered into the heap. Because the app owns
 * these MediaStore entries, no storage permission is required.
 */
class LocalBackupStorage(
    private val context: Context = ksuApp,
) : BackupStorage {

    private val collection: Uri
        get() = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    override suspend fun test(): Result<Unit> = withContext(Dispatchers.IO) {
        backupResult {
            val probe = "finasu_probe.tmp"
            put(probe, ByteArray(0)).getOrThrow()
            delete(probe).getOrThrow()
        }
    }

    override suspend fun put(relativePath: String, bytes: ByteArray): Result<Unit> =
        put(relativePath, BackupContent.Bytes(bytes))

    override suspend fun put(relativePath: String, content: BackupContent): Result<Unit> =
        withContext(Dispatchers.IO) {
            backupResult {
                val directory = LocalBackupLayout.mediaRelativeDir(relativePath)
                val name = LocalBackupLayout.displayName(relativePath)
                // Replace any existing entry so the display name stays stable instead of being
                // auto-renamed to "name (1)".
                findId(relativePath)?.let { id -> context.contentResolver.delete(idUri(id), null, null) }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values)
                    ?: throw IOException("could not create MediaStore entry for $relativePath")
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        content.open().use { input -> input.copyTo(output) }
                    } ?: throw IOException("could not open output stream for $relativePath")
                    context.contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                } catch (t: Throwable) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    throw t
                }
                Unit
            }
        }

    override suspend fun get(relativePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        backupResult {
            val id = findId(relativePath)
                ?: throw NoSuchElementException("missing: $relativePath")
            context.contentResolver.openInputStream(idUri(id))?.use { it.readBytes() }
                ?: throw IOException("could not open input stream for $relativePath")
        }
    }

    override suspend fun open(relativePath: String): Result<BackupContent> = withContext(Dispatchers.IO) {
        backupResult {
            val id = findId(relativePath) ?: throw NoSuchElementException("missing: $relativePath")
            val target = File.createTempFile("backup-restore-", ".tmp", context.cacheDir)
            try {
                context.contentResolver.openInputStream(idUri(id))?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } ?: throw IOException("could not open input stream for $relativePath")
                BackupContent.FileRef(target)
            } catch (t: Throwable) {
                target.delete()
                throw t
            }
        }
    }

    override suspend fun list(prefix: String): Result<List<RemoteEntry>> = withContext(Dispatchers.IO) {
        backupResult {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.SIZE,
            )
            val entries = mutableListOf<RemoteEntry>()
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dirColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: continue
                    val directory = cursor.getString(dirColumn).orEmpty()
                    val relativePath = LocalBackupLayout.toRelativePath(directory, name) ?: continue
                    if (!relativePath.startsWith(prefix)) continue
                    val id = cursor.getLong(idColumn)
                    entries += RemoteEntry(
                        relativePath = relativePath,
                        sizeBytes = cursor.getLong(sizeColumn),
                        sha256 = hashOf(id),
                    )
                }
            }
            entries
        }
    }

    override suspend fun delete(relativePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        backupResult {
            val id = findId(relativePath) ?: return@backupResult
            context.contentResolver.delete(idUri(id), null, null)
            Unit
        }
    }

    private fun idUri(id: Long): Uri = ContentUris.withAppendedId(collection, id)

    /** Resolves the MediaStore id of [relativePath], or `null` when it is absent. */
    private fun findId(relativePath: String): Long? {
        val name = LocalBackupLayout.displayName(relativePath)
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(name),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dirColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val directory = cursor.getString(dirColumn).orEmpty()
                if (LocalBackupLayout.toRelativePath(directory, name) == relativePath) {
                    return cursor.getLong(idColumn)
                }
            }
        }
        return null
    }

    private fun hashOf(id: Long): String? =
        context.contentResolver.openInputStream(idUri(id))?.use { it.sha256() }

    override fun toString(): String = "local:Downloads/${LocalBackupLayout.ROOT_DIR_NAME}"
}
