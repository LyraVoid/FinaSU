package me.weishu.kernelsu.ui.screen.builtinzygisk

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.ui.graphics.vector.ImageVector
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkZygiskStatus
import me.weishu.kernelsu.data.model.RuntimeStatus

enum class PanelAction(val title: Int, val consequence: Int, val icon: ImageVector) {
    Enable(R.string.folkzygisk_next_enabled, R.string.folkzygisk_reboot_required, Icons.Filled.PowerSettingsNew),
    Disable(R.string.folkzygisk_next_disabled, R.string.folkzygisk_reboot_required, Icons.Filled.PowerSettingsNew),
    Recover(R.string.folkzygisk_recover, R.string.folkzygisk_recover_summary, Icons.Filled.Build),
    Reset(R.string.fz_reset, R.string.fz_reset_hint, Icons.Filled.RestartAlt),
    Install(R.string.fz_install, R.string.fz_install_hint, Icons.Filled.Download),
}

enum class Phase(val title: Int, val hint: Int) {
    Injected(R.string.folkzygisk_injected, R.string.fz_healthy),
    Partial(R.string.folkzygisk_partial, R.string.fz_check_abi),
    External(R.string.fz_external, R.string.folkzygisk_external_precedence),
    Protected(R.string.folkzygisk_startup_protection, R.string.folkzygisk_startup_paused),
    Missing(R.string.folkzygisk_engine_missing, R.string.fz_install_hint),
    Update(R.string.fz_update, R.string.folkzygisk_reboot_required),
    Failed(R.string.folkzygisk_failed, R.string.fz_check_abi),
    Starting(R.string.folkzygisk_starting, R.string.fz_check_abi),
    Pending(R.string.folkzygisk_next_enabled, R.string.folkzygisk_reboot_required),
    Unverified(R.string.folkzygisk_unverified, R.string.fz_check_abi),
    Disabled(R.string.folkzygisk_next_disabled, R.string.fz_off_hint),
}

data class AbiRow(val bits: String, val label: Int, val reason: String?, val daemonPid: Int?, val zygotePid: Int?)

data class ModuleRow(val title: String, val abis: String)

data class PanelState(
    val phase: Phase,
    val actions: List<PanelAction>,
    val abis: List<AbiRow>,
    val modules: List<ModuleRow>,
    val canToggle: Boolean,
) {
    val providing get() = phase == Phase.Injected || phase == Phase.Partial
}

internal fun failureHint(code: String) = when (code) {
    "engine_missing" -> R.string.folkzygisk_failure_engine_missing
    "engine_update_failed" -> R.string.fz_update_failed
    "runtime_unsafe" -> R.string.folkzygisk_failure_runtime_unsafe
    "monitor_start_failed" -> R.string.folkzygisk_failure_monitor_start_failed
    "monitor_exited" -> R.string.folkzygisk_failure_monitor_exited
    "readiness_timeout" -> R.string.folkzygisk_failure_readiness_timeout
    "injection_unconfirmed" -> R.string.folkzygisk_failure_injection_unconfirmed
    "runtime_invalid" -> R.string.folkzygisk_failure_runtime_invalid
    else -> R.string.folkzygisk_failure_unknown
}

fun mapStatus(status: FolkZygiskStatus, runtime: RuntimeStatus?): PanelState = with(status) {
    val phase = when {
        injectionState == "partial" && runtime != null -> Phase.Partial
        injectionState == "injected" && runtime != null -> Phase.Injected
        externalProviders.isNotEmpty() -> Phase.External
        startupBlocked -> Phase.Protected
        !engineAvailable -> Phase.Missing
        matchesAssets == false -> Phase.Update
        injectionState == "failed" || runtime == null && startupFailureCode != null -> Phase.Failed
        injectionState == "starting" -> Phase.Starting
        enabled && pendingRestart -> Phase.Pending
        enabled -> Phase.Unverified
        else -> Phase.Disabled
    }
    PanelState(phase, actions = buildList {
        if (startupBlocked) {
            if (externalProviders.isEmpty() && (engineAvailable || canInstall)) add(PanelAction.Recover)
            add(PanelAction.Reset)
        }
        if (canInstall && runtime == null && (!engineAvailable || matchesAssets == false)) add(PanelAction.Install)
    }, abis = listOf("64", "32").map { bits ->
        val abi = runtime?.abis?.get(bits)
        AbiRow(bits, when {
            abi == null -> R.string.selinux_status_unknown
            !abi.supported -> R.string.home_unsupported
            abi.injected -> R.string.folkzygisk_injected
            else -> R.string.folkzygisk_unverified
        }, abi?.reason?.takeIf { abi.supported && !abi.injected && it.isNotBlank() },
            abi?.daemonPid?.takeIf { abi.supported && abi.daemonRunning && it > 0 },
            abi?.zygotePid?.takeIf { abi.supported && it > 0 })
    }, modules = modules.map {
        ModuleRow(if (it.name == it.id) it.name else "${it.name} (${it.id})", it.abis.joinToString(" / "))
    },
        canToggle = enabled || engineAvailable && !startupBlocked && enableBlockers.isEmpty() && externalProviders.isEmpty())
}
