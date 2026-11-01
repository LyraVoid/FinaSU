package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.FolkMountProvider
import me.weishu.kernelsu.data.model.FolkMountStatus
import me.weishu.kernelsu.ui.util.RootShellStatus
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo

/** The installed metamodule, named by its module entry when the module list has loaded. */
@Immutable
data class HomeMetamodule(
    val id: String,
    val name: String,
    val version: String,
)

/** The provider that actually mounted modules this boot; a null provider hides the row. */
@Immutable
data class HomeMount(
    val provider: FolkMountProvider? = null,
    val metamoduleId: String? = null,
    val metamodule: HomeMetamodule? = null,
)

enum class KernelHookBackend {
    TRACEPOINT,
    SUSFS_INLINE,
    UNKNOWN,
    INVALID,
}

@Immutable
data class KernelHookState(
    val backend: KernelHookBackend = KernelHookBackend.UNKNOWN,
    val ready: Boolean? = null,
) {
    val failed: Boolean
        get() = backend == KernelHookBackend.INVALID || (backend != KernelHookBackend.UNKNOWN && ready == false)
}

internal fun resolveKernelHookState(flags: Int): KernelHookState {
    val tracepoint = flags and Natives.KSU_GET_INFO_FLAG_HOOK_TRACEPOINT != 0
    val inline = flags and Natives.KSU_GET_INFO_FLAG_HOOK_SUSFS_INLINE != 0

    if (tracepoint && inline) return KernelHookState(KernelHookBackend.INVALID, false)
    if (!tracepoint && !inline) return KernelHookState()

    return KernelHookState(
        backend = if (inline) KernelHookBackend.SUSFS_INLINE else KernelHookBackend.TRACEPOINT,
        ready = flags and Natives.KSU_GET_INFO_FLAG_HOOK_READY != 0,
    )
}

@Immutable
data class HomeUiState(
    val kernelVersion: KernelVersion,
    val ksuVersion: Int?,
    val managerUAPIVersion: Int,
    val kernelUAPIVersion: Int?,
    val kernelHook: KernelHookState = KernelHookState(),
    val lkmMode: Boolean?,
    val isLkmBundled: Boolean,
    val isManager: Boolean,
    val isManagerPrBuild: Boolean,
    val isKernelPrBuild: Boolean,
    val requiresNewKernel: Boolean,
    val requiresNewManager: Boolean,
    val isRootAvailable: Boolean,
    val rootStatus: RootShellStatus = if (isRootAvailable) RootShellStatus.Ready else RootShellStatus.Unavailable,
    val isSafeMode: Boolean,
    val isLateLoadMode: Boolean,
    val checkUpdateEnabled: Boolean,
    val latestVersionInfo: LatestVersionInfo,
    val currentManagerVersionCode: Long,
    val systemInfo: SystemInfo,
    val susfs: me.weishu.kernelsu.data.susfs.SusfsStatus = me.weishu.kernelsu.data.susfs.SusfsStatus(),
    /** The active Zygisk implementation ("Built-In" or a provider's name); null hides the row. */
    val zygiskProvider: String? = null,
    /** The provider that really mounted modules this boot; a null provider hides the row. */
    val mount: HomeMount = HomeMount(),
) {
    val isSELinuxPermissive: Boolean
        get() = systemInfo.selinuxStatus == "Permissive"

    val showGkiWarning: Boolean
        get() = ksuVersion != null && lkmMode == false

    val showLkmUpdate: Boolean
        get() = isManager &&
                lkmMode == true &&
                !isLateLoadMode &&
                isLkmBundled &&
                ksuVersion?.toLong() != currentManagerVersionCode &&
                !requiresNewKernel &&
                !requiresNewManager

    // Jailbreak mode runs on locked bootloaders, so flashing a boot image would brick the device.
    val canInstallKernelUpdate: Boolean
        get() = lkmMode == true && !isLateLoadMode

    val showKernelUpdateAction: Boolean
        get() = canInstallKernelUpdate && (requiresNewKernel || showLkmUpdate)

    val showCustomLkmBadge: Boolean
        get() = lkmMode == true && !isLkmBundled

    /** A root probe is still in flight: show a placeholder, not a failure. */
    val showRootRecovering: Boolean
        get() = ksuVersion != null && rootStatus == RootShellStatus.Probing

    /** A definite root failure, only after a completed probe came back non-root. */
    val showRootWarning: Boolean
        get() = ksuVersion != null && rootStatus == RootShellStatus.Unavailable

    val showManagerPrBuildWarning: Boolean
        get() = isManager && isManagerPrBuild

    val showKernelPrBuildWarning: Boolean
        get() = isManager && !isManagerPrBuild && isKernelPrBuild

    val hasUpdate: Boolean
        get() = latestVersionInfo.versionCode > currentManagerVersionCode
}

@Immutable
data class HomeActions(
    val onInstallClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onJailbreakClick: () -> Unit = {},
    val onOpenSuperUser: () -> Unit = {},
    val onOpenModule: () -> Unit = {},
    val onRetryRoot: () -> Unit = {},
    val onSusfs: () -> Unit = {},
) {
    fun openManagerReleases() = onOpenUrl("https://github.com/LyraVoid/FinaSU/releases")
}

/**
 * The provider that really ran this boot. The runtime record already reflects a NoMount
 * fallback to the built-in engine; the configured provider only predicts the next boot,
 * so use it only when this boot has no record yet.
 */
internal fun resolveHomeMountProvider(status: FolkMountStatus?): FolkMountProvider? {
    status ?: return null
    if (status.bootResult == "skipped") return FolkMountProvider.NONE
    return status.bootProvider ?: status.nextProvider
}
