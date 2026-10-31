package me.weishu.kernelsu.data.backup.webdav

import java.io.File
import java.io.IOException
import me.weishu.kernelsu.data.backup.backupResult
import me.weishu.kernelsu.data.backup.BackupContent
import me.weishu.kernelsu.data.backup.BackupKind
import me.weishu.kernelsu.data.backup.BackupManifest
import me.weishu.kernelsu.data.backup.BackupManifestEntry
import me.weishu.kernelsu.data.backup.BackupStorage
import me.weishu.kernelsu.data.backup.RemoteEntry
import me.weishu.kernelsu.data.backup.WebDavPaths
import me.weishu.kernelsu.data.backup.backupIsoTimestamp
import me.weishu.kernelsu.data.backup.sha256

/** WebDAV storage with a best-effort manifest for content hashes and duplicate detection. */
class WebDavBackupStorage(
    private val transport: WebDavTransport,
    private val baseUrl: String,
    private val manifestFileName: String = DEFAULT_MANIFEST,
) : BackupStorage {

    /** Path component of [baseUrl]; remote hrefs are relativised against it. */
    private val basePath: String = WebDavHref.toPath(baseUrl)

    private var manifest: BackupManifest? = null
    private var manifestLoaded = false

    override suspend fun test(): Result<Unit> = backupResult {
        val response = transport.propfind(baseUrl, depth = 0)
        if (!response.isSuccessful) {
            throw IOException("WebDAV unreachable: HTTP ${response.code}")
        }
    }

    override suspend fun put(relativePath: String, bytes: ByteArray): Result<Unit> =
        put(relativePath, BackupContent.Bytes(bytes))

    override suspend fun put(relativePath: String, content: BackupContent): Result<Unit> = backupResult {
        ensureDirectories(relativePath.substringBeforeLast('/', ""))
        val digest = content.sha256()
        val response = transport.put(resolve(relativePath), content)
        if (!response.isSuccessful) {
            throw IOException("PUT $relativePath failed: HTTP ${response.code}")
        }
        recordInManifest(relativePath, digest, content.sizeBytes)
    }

    override suspend fun get(relativePath: String): Result<ByteArray> = backupResult {
        val response = transport.get(resolve(relativePath))
        if (!response.isSuccessful) {
            throw IOException("GET $relativePath failed: HTTP ${response.code}")
        }
        response.body ?: ByteArray(0)
    }

    override suspend fun open(relativePath: String): Result<BackupContent> = backupResult {
        val target = File.createTempFile("finasu-restore-", ".tmp")
        try {
            val response = transport.getToFile(resolve(relativePath), target)
            if (!response.isSuccessful) {
                throw IOException("GET $relativePath failed: HTTP ${response.code}")
            }
            BackupContent.FileRef(target)
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    override suspend fun list(prefix: String): Result<List<RemoteEntry>> = backupResult {
        // An empty prefix means "everything": no server offers a practical recursive PROPFIND, so
        // walk the tree one collection at a time. The engine only ever asks for a concrete
        // directory, which stays a single Depth: 1 request.
        if (prefix.isBlank()) listRecursively() else listDirectory(prefix)
    }

    private suspend fun listDirectory(prefix: String): List<RemoteEntry> {
        val response = transport.propfind(resolve(prefix.trim('/')), depth = 1)
        if (response.code == 404 || response.code == 409) return emptyList()
        if (!response.isSuccessful) {
            throw IOException("PROPFIND $prefix failed: HTTP ${response.code}")
        }
        val xml = response.body ?: return emptyList()
        val entries = mutableListOf<RemoteEntry>()
        for (resource in WebDavMultiStatus.parse(xml.inputStream())) {
            if (resource.isCollection) continue
            val relativePath = WebDavHref.relativize(basePath, resource.href) ?: continue
            if (!relativePath.startsWith(prefix)) continue
            entries += entryOf(relativePath, resource)
        }
        return entries
    }

    private suspend fun listRecursively(): List<RemoteEntry> {
        val collected = mutableListOf<RemoteEntry>()
        val pending = ArrayDeque<String>()
        pending += ""
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val directory = pending.removeFirst()
            if (!visited.add(directory)) continue
            check(visited.size <= MAX_DIRECTORIES) { "WebDAV directory limit exceeded" }
            val response = transport.propfind(resolve(directory.trim('/')), depth = 1)
            if (response.code == 404 || response.code == 409) continue
            if (!response.isSuccessful) {
                throw IOException("PROPFIND $directory failed: HTTP ${response.code}")
            }
            val xml = response.body ?: continue
            for (resource in WebDavMultiStatus.parse(xml.inputStream())) {
                val relativePath = WebDavHref.relativize(basePath, resource.href)?.trimEnd('/') ?: continue
                // The listing always echoes the queried collection itself; never enqueue it.
                if (relativePath.isEmpty() || relativePath == directory) continue
                if (directory.isNotEmpty() && !relativePath.startsWith("$directory/")) continue
                if (resource.isCollection) {
                    if (relativePath !in visited) pending += relativePath
                } else {
                    collected += entryOf(relativePath, resource)
                }
            }
        }
        return collected
    }

    private suspend fun entryOf(relativePath: String, resource: WebDavResource): RemoteEntry = RemoteEntry(
        relativePath = relativePath,
        sizeBytes = resource.contentLength,
        sha256 = manifestSha256(relativePath),
        lastModifiedEpochMs = resource.lastModifiedEpochMs,
    )

    override suspend fun delete(relativePath: String): Result<Unit> = backupResult {
        val response = transport.delete(resolve(relativePath))
        if (!response.isSuccessful && response.code != 404) {
            throw IOException("DELETE $relativePath failed: HTTP ${response.code}")
        }
        removeFromManifest(relativePath)
    }

    /** The absolute URL of a storage-relative path. */
    fun resolve(relativePath: String): String = WebDavPaths.joinBase(baseUrl, relativePath)

    private suspend fun ensureDirectories(directory: String) {
        for (dir in WebDavPaths.parentDirs(directory)) {
            val response = transport.mkcol(resolve(dir))
            // 201 created, 405 already exists, 301 moved to an existing collection, and 409 used by
            // some servers for an existing collection are all acceptable.
            if (response.isSuccessful || response.code in listOf(301, 405, 409)) continue
            throw IOException("MKCOL $dir failed: HTTP ${response.code}")
        }
    }

    private suspend fun manifestSha256(relativePath: String): String? =
        loadManifest()?.entries?.firstOrNull { it.path == relativePath }?.sha256

    private suspend fun loadManifest(): BackupManifest? {
        if (manifestLoaded) return manifest
        manifest = backupResult {
            val response = transport.get(resolve(manifestFileName))
            if (!response.isSuccessful) return@backupResult null
            response.bodyText()?.let { BackupManifest.fromJson(it).getOrNull() }
        }.getOrNull()
        manifestLoaded = true
        return manifest
    }

    private suspend fun recordInManifest(relativePath: String, sha256: String, sizeBytes: Long) {
        if (relativePath.endsWith(META_SUFFIX)) return
        backupResult {
            val now = backupIsoTimestamp(System.currentTimeMillis())
            val current = loadManifest() ?: BackupManifest(updatedAt = now, entries = emptyList())
            val kind = kindOf(relativePath) ?: return@backupResult
            val entry = BackupManifestEntry(
                path = relativePath,
                sha256 = sha256,
                sizeBytes = sizeBytes,
                kind = kind,
                createdAt = now,
            )
            val updated = BackupManifest(
                updatedAt = now,
                entries = current.entries.filterNot { it.path == relativePath } + entry,
            )
            manifest = updated
            val bytes = updated.toJson().toByteArray(Charsets.UTF_8)
            check(transport.put(resolve(manifestFileName), BackupContent.Bytes(bytes)).isSuccessful) {
                "Could not update WebDAV manifest"
            }
        }.onFailure { invalidateManifest() }
    }

    private suspend fun removeFromManifest(relativePath: String) {
        backupResult {
            val current = loadManifest() ?: return@backupResult
            val remaining = current.entries.filterNot { it.path == relativePath }
            if (remaining.size == current.entries.size) return@backupResult
            val updated = BackupManifest(
                updatedAt = backupIsoTimestamp(System.currentTimeMillis()),
                entries = remaining,
            )
            manifest = updated
            val bytes = updated.toJson().toByteArray(Charsets.UTF_8)
            check(transport.put(resolve(manifestFileName), BackupContent.Bytes(bytes)).isSuccessful) {
                "Could not update WebDAV manifest"
            }
        }.onFailure { invalidateManifest() }
    }

    private fun invalidateManifest() {
        manifest = null
        manifestLoaded = false
    }

    private fun kindOf(relativePath: String): BackupKind? = when (relativePath.substringBefore('/')) {
        "modules" -> BackupKind.MODULE
        "allowlist" -> BackupKind.ALLOWLIST
        "boot" -> BackupKind.BOOT
        else -> null
    }

    override fun toString(): String = "webdav:$baseUrl"

    companion object {
        /** Name of the optional index document stored at the backup root. */
        const val DEFAULT_MANIFEST = "manifest.json"

        private const val META_SUFFIX = ".meta.json"

        private const val MAX_DIRECTORIES = 64
    }
}
