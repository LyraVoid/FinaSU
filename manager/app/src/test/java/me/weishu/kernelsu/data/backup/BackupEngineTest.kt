package me.weishu.kernelsu.data.backup

import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEngineTest {

    private val epochMs = 1_700_000_000_000L

    @Test
    fun `happy path writes payload and sidecar`() {
        val storage = InMemoryBackupStorage()
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val engine = engineWith(artifact, storage)

        val result = runBlocking { engine.backup(BackupKind.MODULE) }

        val zipPath = "modules/example.module/${artifact.fileName}"
        val metaPath = "modules/example.module/${artifact.metaFileName}"
        assertTrue(result.isSuccess)
        assertEquals(listOf(zipPath, metaPath), result.written)
        assertEquals("payload", String(storage.files.getValue(zipPath)))
        assertTrue(storage.files.containsKey(metaPath))
    }

    @Test
    fun `identical content is skipped`() {
        val storage = InMemoryBackupStorage()
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val zipPath = "modules/example.module/${artifact.fileName}"
        storage.files[zipPath] = artifact.bytes!!

        val result = runBlocking { engineWith(artifact, storage).backup(BackupKind.MODULE) }

        assertTrue(result.isSuccess)
        assertEquals(listOf(zipPath), result.skipped)
        assertTrue(result.written.isEmpty())
    }

    @Test
    fun `a failing backend reports a partial run`() {
        val healthy = InMemoryBackupStorage("healthy")
        val broken = InMemoryBackupStorage("broken").apply {
            putFailure = { Throwable("disk full") }
        }
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val engine = BackupEngine(
            BackupSourceRegistry(listOf(FakeBackupSource(BackupKind.MODULE, artifacts = listOf(artifact)))),
            listOf(healthy, broken),
        )

        val result = runBlocking { engine.backup(BackupKind.MODULE) }

        assertFalse(result.isSuccess)
        assertTrue(result.isPartial)
        assertFalse(result.isFailure)
        assertEquals(2, result.written.size)
        assertEquals(1, result.failures.size)
        assertTrue(result.failures.all { it.storage == "broken" })
    }

    @Test
    fun `every backend failing reports a failure`() {
        val broken = InMemoryBackupStorage("broken").apply {
            putFailure = { Throwable("disk full") }
        }
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val result = runBlocking { engineWith(artifact, broken).backup(BackupKind.MODULE) }

        assertTrue(result.isFailure)
        assertFalse(result.isPartial)
        assertTrue(result.written.isEmpty())
        assertEquals(1, result.failures.size)
    }

    @Test
    fun `a listing failure is recorded and aborts that backend`() {
        val storage = InMemoryBackupStorage("offline").apply {
            listFailure = Throwable("connection refused")
        }
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val result = runBlocking { engineWith(artifact, storage).backup(BackupKind.MODULE) }

        assertTrue(result.isFailure)
        assertEquals(listOf("list"), result.failures.map { it.operation })
        assertEquals("connection refused", result.failures.single().cause.message)
    }

    @Test
    fun `a missing source reports a failure`() {
        val engine = BackupEngine(BackupSourceRegistry(emptyList()), listOf(InMemoryBackupStorage()))
        val result = runBlocking { engine.backup(BackupKind.ALLOWLIST) }

        assertTrue(result.isFailure)
        assertEquals(listOf("source"), result.failures.map { it.operation })
    }

    @Test
    fun `an export failure reports a failure`() {
        val source = FakeBackupSource(BackupKind.MODULE, exportFailure = Throwable("capture failed"))
        val engine = BackupEngine(BackupSourceRegistry(listOf(source)), listOf(InMemoryBackupStorage()))
        val result = runBlocking { engine.backup(BackupKind.MODULE) }

        assertTrue(result.isFailure)
        assertEquals(listOf("export"), result.failures.map { it.operation })
    }

    @Test
    fun `restore delegates to the source`() {
        val source = FakeBackupSource(BackupKind.MODULE)
        val storage = InMemoryBackupStorage().apply {
            files["modules/example.module/x.zip"] = "payload".toByteArray()
        }
        val engine = BackupEngine(BackupSourceRegistry(listOf(source)), listOf(storage))
        val entry = BackupEntry(
            id = "example.module",
            kind = BackupKind.MODULE,
            displayName = "example.module",
            relativePath = "modules/example.module/x.zip",
        )

        assertTrue(runBlocking { engine.restore(BackupKind.MODULE, entry, storage) }.isSuccess)
        assertEquals(1, source.restoreCount)
        assertEquals(entry, source.lastRestored)
        assertEquals("payload", String(source.lastRestoredContent!!.readBytes()))
    }

    @Test
    fun `restore without a source fails`() {
        val storage = InMemoryBackupStorage()
        val engine = BackupEngine(BackupSourceRegistry(emptyList()), listOf(storage))
        val entry = BackupEntry(id = "x", kind = BackupKind.BOOT, displayName = "x")
        assertTrue(runBlocking { engine.restore(BackupKind.BOOT, entry, storage) }.isFailure)
    }

    @Test
    fun `restore propagates a missing payload`() {
        val source = FakeBackupSource(BackupKind.MODULE)
        val storage = InMemoryBackupStorage()
        val engine = BackupEngine(BackupSourceRegistry(listOf(source)), listOf(storage))
        val entry = BackupEntry(
            id = "example.module",
            kind = BackupKind.MODULE,
            displayName = "example.module",
            relativePath = "modules/example.module/missing.zip",
        )

        assertTrue(runBlocking { engine.restore(BackupKind.MODULE, entry, storage) }.isFailure)
        assertEquals(0, source.restoreCount)
    }

    @Test
    fun `restore rejects changed content before calling the source`() {
        val source = FakeBackupSource(BackupKind.MODULE)
        val storage = InMemoryBackupStorage().apply {
            files["modules/example/module.zip"] = "changed".toByteArray()
        }
        val entry = BackupEntry(
            id = "example",
            kind = BackupKind.MODULE,
            displayName = "example",
            relativePath = "modules/example/module.zip",
            sha256 = "original".toByteArray().sha256(),
        )
        val engine = BackupEngine(BackupSourceRegistry(listOf(source)), listOf(storage))

        assertTrue(runBlocking { engine.restore(entry.kind, entry, storage) }.isFailure)
        assertEquals(0, source.restoreCount)
    }

    @Test
    fun `failed payload write does not leave a metadata sidecar`() {
        val storage = InMemoryBackupStorage().apply {
            putFailure = { path -> if (path.endsWith(".zip")) IllegalStateException("disk full") else null }
        }
        val result = runBlocking { engineWith(moduleArtifact("example", "1", 1, "payload"), storage).backup(BackupKind.MODULE) }

        assertTrue(result.isFailure)
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun `a file-backed artifact is streamed to storage`() {
        val storage = InMemoryBackupStorage()
        val payload = "partition-image-payload".toByteArray()
        val file = File.createTempFile("artifact", ".img").apply {
            writeBytes(payload)
            deleteOnExit()
        }
        val fileName = bootBackupName("boot", "deadbeef")
        val artifact = BackupArtifact(
            kind = BackupKind.BOOT,
            moduleId = null,
            fileName = fileName,
            metaFileName = null,
            sha256 = payload.sha256(),
            metaJson = null,
            file = file,
        )
        val engine = BackupEngine(
            BackupSourceRegistry(listOf(FakeBackupSource(BackupKind.BOOT, artifacts = listOf(artifact)))),
            listOf(storage),
        )

        val result = runBlocking { engine.backup(BackupKind.BOOT) }

        val path = "boot/$fileName"
        assertTrue(result.isSuccess)
        assertEquals(listOf(path), result.written)
        assertArrayEquals(payload, storage.files.getValue(path))
    }

    @Test
    fun `file-backed artifacts are released after a run`() {
        val file = File.createTempFile("artifact", ".img").apply {
            writeBytes("partition-image-payload".toByteArray())
        }
        val fileName = bootBackupName("boot", "deadbeef")
        val artifact = BackupArtifact(
            kind = BackupKind.BOOT,
            moduleId = null,
            fileName = fileName,
            metaFileName = null,
            sha256 = "partition-image-payload".toByteArray().sha256(),
            metaJson = null,
            file = file,
        )
        val engine = BackupEngine(
            BackupSourceRegistry(listOf(FakeBackupSource(BackupKind.BOOT, artifacts = listOf(artifact)))),
            listOf(InMemoryBackupStorage()),
        )

        runBlocking { engine.backup(BackupKind.BOOT) }

        assertFalse("engine must delete file-backed artifacts", file.exists())
    }

    @Test
    fun `registry resolves only registered kinds`() {
        val source = FakeBackupSource(BackupKind.MODULE)
        val registry = BackupSourceRegistry(listOf(source))

        assertEquals(source, registry.sourceFor(BackupKind.MODULE))
        assertNull(registry.sourceFor(BackupKind.BOOT))
        assertEquals(setOf(BackupKind.MODULE), registry.kinds)
    }

    private fun engineWith(
        artifact: BackupArtifact,
        storage: InMemoryBackupStorage = InMemoryBackupStorage(),
    ): BackupEngine = BackupEngine(
        BackupSourceRegistry(listOf(FakeBackupSource(BackupKind.MODULE, artifacts = listOf(artifact)))),
        listOf(storage),
    )

    private fun moduleArtifact(
        moduleId: String,
        versionName: String,
        versionCode: Int,
        payload: String,
    ): BackupArtifact {
        val bytes = payload.toByteArray()
        val fileName = moduleArchiveName(moduleId, versionName, versionCode, epochMs)
        return BackupArtifact(
            kind = BackupKind.MODULE,
            moduleId = moduleId,
            fileName = fileName,
            metaFileName = moduleMetaFileName(fileName),
            bytes = bytes,
            sha256 = bytes.sha256(),
            metaJson = """{"schema":"$MODULE_BACKUP_SCHEMA","version":$BACKUP_FORMAT_VERSION}""",
        )
    }
}
