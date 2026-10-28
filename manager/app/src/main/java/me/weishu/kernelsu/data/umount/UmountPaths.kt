package me.weishu.kernelsu.data.umount

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.ui.util.execKsudResult
import org.json.JSONArray

data class UmountPathEntry(
    val path: String,
    val flags: Int,
)

enum class UmountPathSource {
    CUSTOM,
    KERNEL,
    UNKNOWN,
}

@Immutable
data class UmountPath(
    val path: String,
    val flags: Int,
    val source: UmountPathSource,
)

@Immutable
data class UmountPathsSnapshot(
    val paths: List<UmountPath>,
    val kernelSupported: Boolean,
    val configReadable: Boolean,
    val readError: Boolean,
) {
    val custom: List<UmountPath> get() = paths.filter { it.source == UmountPathSource.CUSTOM }
    val kernelEntries: List<UmountPath> get() = paths.filter { it.source == UmountPathSource.KERNEL }
    val unknown: List<UmountPath> get() = paths.filter { it.source == UmountPathSource.UNKNOWN }
}

enum class UmountPathError {
    NOT_ABSOLUTE,
    ROOT,
    TOO_LONG,
    TRAILING_SLASH,
    EMPTY_SEGMENT,
    DOT_SEGMENT,
    CONTROL_CHARACTER,
    FORBIDDEN,
}

object UmountPathsData {
    const val MNT_DETACH = 2
    private val forbidden = setOf(
        "/",
        "/data",
        "/system",
        "/system_ext",
        "/vendor",
        "/product",
        "/odm",
        "/apex",
        "/proc",
        "/sys",
        "/dev",
        "/storage",
        "/mnt",
        "/sdcard",
        "/data/adb",
        "/metadata",
        "/system_dlkm",
        "/vendor_dlkm",
        "/debug_ramdisk",
        "/linkerconfig",
        "/second_stage_resources",
        "/postinstall",
        "/config",
        "/efs",
    )

    fun parse(json: String): List<UmountPathEntry> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    UmountPathEntry(
                        path = item.getString("path"),
                        flags = item.getInt("flags"),
                    )
                )
            }
        }
    }

    fun merge(
        config: List<UmountPathEntry>?,
        kernel: List<UmountPathEntry>?,
        previous: List<UmountPath>? = null,
    ): UmountPathsSnapshot {
        if (previous != null && (config == null || kernel == null)) {
            val paths = if (config == null) {
                previous.map { it.copy(source = UmountPathSource.UNKNOWN) }
            } else {
                val custom = config.map {
                    UmountPath(it.path, it.flags, UmountPathSource.CUSTOM)
                }
                val retained = previous.filter { it.source != UmountPathSource.CUSTOM }
                (custom + retained).distinctBy { it.path }
            }
            return UmountPathsSnapshot(
                paths = paths,
                kernelSupported = kernel != null,
                configReadable = config != null,
                readError = config == null,
            )
        }

        if (config == null) {
            return UmountPathsSnapshot(
                paths = kernel.orEmpty().map {
                    UmountPath(it.path, it.flags, UmountPathSource.UNKNOWN)
                },
                kernelSupported = kernel != null,
                configReadable = false,
                readError = false,
            )
        }

        val kernelByPath = kernel.orEmpty().associateBy { it.path }
        val custom = config.map { entry ->
            UmountPath(
                path = entry.path,
                flags = kernelByPath[entry.path]?.flags ?: entry.flags,
                source = UmountPathSource.CUSTOM,
            )
        }
        val configured = config.mapTo(mutableSetOf()) { it.path }
        val modules = kernel.orEmpty()
            .filterNot { it.path in configured }
            .map { UmountPath(it.path, it.flags, UmountPathSource.KERNEL) }

        return UmountPathsSnapshot(
            paths = (custom + modules).sortedWith(
                compareBy<UmountPath> { it.source.ordinal }.thenBy { it.path }
            ),
            kernelSupported = kernel != null,
            configReadable = true,
            readError = false,
        )
    }

    fun validatePath(path: String): UmountPathError? = when {
        !path.startsWith('/') -> UmountPathError.NOT_ABSOLUTE
        path == "/" -> UmountPathError.ROOT
        path.toByteArray(Charsets.UTF_8).size > 255 -> UmountPathError.TOO_LONG
        path.endsWith('/') -> UmountPathError.TRAILING_SLASH
        path.any { it.isISOControl() } -> UmountPathError.CONTROL_CHARACTER
        path in forbidden -> UmountPathError.FORBIDDEN
        path.drop(1).split('/').any { it.isEmpty() } -> UmountPathError.EMPTY_SEGMENT
        path.drop(1).split('/').any { it == "." || it == ".." } -> UmountPathError.DOT_SEGMENT
        else -> null
    }

    fun addCommand(path: String): String =
        "umount-config add ${shellQuote(path)} --flags $MNT_DETACH"

    fun deleteCommand(path: String): String =
        "umount-config del ${shellQuote(path)}"

    fun clearCommand(): String = "umount-config clear"

    internal fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}

interface UmountPathsRepository {
    suspend fun loadConfig(): Result<List<UmountPathEntry>>
    suspend fun loadKernel(): Result<List<UmountPathEntry>>
    suspend fun add(path: String): Result<Unit>
    suspend fun delete(path: String): Result<Unit>
    suspend fun clear(): Result<Unit>
}

class UmountPathsRepositoryImpl : UmountPathsRepository {
    override suspend fun loadConfig(): Result<List<UmountPathEntry>> =
        runCatching {
            UmountPathsData.parse(runKsud("umount-config list").getOrThrow())
        }

    override suspend fun loadKernel(): Result<List<UmountPathEntry>> =
        runCatching {
            UmountPathsData.parse(runKsud("kernel umount list --json").getOrThrow())
        }

    override suspend fun add(path: String): Result<Unit> =
        runCommand(UmountPathsData.addCommand(path))

    override suspend fun delete(path: String): Result<Unit> =
        runCommand(UmountPathsData.deleteCommand(path))

    override suspend fun clear(): Result<Unit> =
        runCommand(UmountPathsData.clearCommand())

    private suspend fun runKsud(command: String): Result<String> {
        val result = execKsudResult(command)
        if (!result.isSuccess) {
            return Result.failure(
                IllegalStateException(
                    result.err.joinToString("\n").trim()
                        .ifBlank { "ksud $command failed with exit ${result.code}" }
                )
            )
        }
        return Result.success(result.out.joinToString("\n").trim())
    }

    private suspend fun runCommand(command: String): Result<Unit> =
        runCatching { runKsud(command).getOrThrow() }
}
