package me.weishu.kernelsu.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SuiStatus
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

/**
 * Sui is a Zygisk module, so its switch stays disabled until ksud reports a usable provider.
 * The permission UI itself stays Sui's own; this row only owns install, enable and diagnosis.
 */
@Composable
internal fun SuiStatusItem(state: SettingsUiState, actions: SettingsScreenActions) {
    val status = state.suiStatus
    val confirm = rememberConfirmDialog(onConfirm = { actions.onSetSuiEnabled(true) })
    val notInstalled = stringResource(R.string.sui_not_installed)
    val stateText = status?.let { stringResource(suiStateRes(it)) }
    val reasonText = status?.let { suiReasonRes(it)?.let { res -> stringResource(res, it.detail) } }
    val zygiskText = status?.let { stringResource(zygiskStateRes(it)) }.orEmpty()
    val confirmTitle = stringResource(R.string.sui_confirm_title)
    val confirmAccept = stringResource(R.string.sui_confirm_confirm)
    val confirmCancel = stringResource(R.string.sui_confirm_cancel)
    val confirmBody = status?.let {
        stringResource(
            R.string.sui_confirm_body,
            zygiskText,
            it.payloadVersion.ifBlank { notInstalled },
        )
    }
    val summary = when {
        state.isSuiLoading -> stringResource(R.string.sui_loading)
        state.suiReadError || status == null -> stringResource(R.string.sui_read_error)
        else -> listOfNotNull(
            stateText,
            reasonText,
            stringResource(R.string.sui_dependency, zygiskText),
        ).joinToString("\n")
    }
    val actionable = status != null && !state.suiReadError && !state.isSuiLoading && !state.isSuiWriting

    SegmentedSwitchItem(
        icon = Icons.Filled.WaterDrop,
        title = stringResource(R.string.sui_title),
        summary = summary,
        checked = status?.selected == true,
        enabled = actionable && status.conflicts.isEmpty(),
        onCheckedChange = { wanted ->
            if (!wanted) {
                actions.onSetSuiEnabled(false)
            } else {
                confirm.showConfirm(
                    title = confirmTitle,
                    content = confirmBody,
                    confirm = confirmAccept,
                    dismiss = confirmCancel,
                )
            }
        },
    )

    if (status != null && status.installed) {
        SegmentedListItem(
            headlineContent = { Text(stringResource(R.string.sui_manage)) },
            supportingContent = { Text(stringResource(R.string.sui_manage_summary)) },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
            enabled = !state.isSuiWriting,
            onClick = actions.onManageSuiPermissions,
        )
    }

    if (status != null && status.needsDiagnostics) {
        SuiDiagnostics(
            status = status,
            onRepair = actions.onRepairSui,
            busy = state.isSuiWriting,
            managementAvailable = state.suiManagementAvailable,
        )
    }
}

@Composable
private fun SuiDiagnostics(
    status: SuiStatus,
    onRepair: () -> Unit,
    busy: Boolean,
    managementAvailable: Boolean?,
) {
    val payload = when {
        !status.installed -> stringResource(R.string.sui_diag_payload_absent)
        status.payloadCurrent -> stringResource(R.string.sui_diag_payload_ok)
        else -> stringResource(R.string.sui_diag_payload_stale)
    }
    SegmentedListItem(
        leadingContent = { Icon(Icons.Filled.Extension, null) },
        headlineContent = { Text(stringResource(R.string.sui_diag_injection)) },
        supportingContent = { Text(stringResource(zygiskStateRes(status))) },
    )
    SegmentedListItem(
        leadingContent = { Icon(Icons.Filled.Download, null) },
        headlineContent = { Text(stringResource(R.string.sui_diag_payload)) },
        supportingContent = { Text("$payload · ${status.payloadVersion.ifBlank { DEFAULT_VERSION }}") },
    )
    SegmentedListItem(
        leadingContent = { Icon(Icons.Filled.Memory, null) },
        headlineContent = { Text(stringResource(R.string.sui_diag_daemons)) },
        supportingContent = {
            Text(
                stringResource(
                    if (status.daemonHealthy) R.string.sui_diag_daemons_ok
                    else R.string.sui_diag_daemons_stopped,
                ),
            )
        },
    )
    if (status.conflicts.isNotEmpty()) {
        SegmentedListItem(
            leadingContent = { Icon(Icons.Filled.Warning, null) },
            headlineContent = { Text(stringResource(R.string.sui_diag_conflicts)) },
            supportingContent = { Text(status.conflicts.joinToString()) },
        )
    }
    managementAvailable?.let { available ->
        SegmentedListItem(
            leadingContent = { Icon(Icons.Filled.AdminPanelSettings, null) },
            headlineContent = { Text(stringResource(R.string.sui_diag_management)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (available) R.string.sui_management_ok
                        else R.string.sui_management_unavailable,
                    ),
                )
            },
        )
    }
    if (!status.installed || !status.payloadCurrent) {
        SegmentedListItem(
            leadingContent = { Icon(Icons.Filled.Build, null) },
            headlineContent = { Text(stringResource(R.string.sui_repair)) },
            supportingContent = { Text(stringResource(R.string.sui_repair_summary)) },
            enabled = !busy,
            onClick = onRepair,
        )
    }
}

private const val DEFAULT_VERSION = "-"

@StringRes
private fun suiStateRes(status: SuiStatus): Int = when (status.state) {
    SuiStatus.STATE_ENABLED -> R.string.sui_state_enabled
    SuiStatus.STATE_PENDING_REBOOT -> R.string.sui_state_pending
    SuiStatus.STATE_EXTERNAL_UNVERIFIED -> R.string.sui_state_external
    SuiStatus.STATE_UNAVAILABLE -> R.string.sui_state_unavailable
    SuiStatus.STATE_ERROR -> R.string.sui_state_error
    else -> R.string.sui_state_disabled
}

@StringRes
private fun suiReasonRes(status: SuiStatus): Int? = when (status.reason) {
    "zygisk_disabled" -> R.string.sui_reason_zygisk_disabled
    "zygisk_engine_missing" -> R.string.sui_reason_zygisk_engine_missing
    "zygisk_startup_protection" -> R.string.sui_reason_zygisk_startup
    "zygisk_injection_failed" -> R.string.sui_reason_injection_failed
    "conflict_existing_sui" -> R.string.sui_reason_conflict
    "payload_missing" -> R.string.sui_reason_payload_missing
    "module_disabled" -> R.string.sui_reason_module_disabled
    "enabled_externally" -> R.string.sui_reason_enabled_externally
    "not_installed" -> null
    else -> R.string.sui_reason_unknown.takeIf { status.state == SuiStatus.STATE_ERROR }
}

@StringRes
private fun zygiskStateRes(status: SuiStatus): Int = when {
    status.externalProviders.isNotEmpty() -> R.string.sui_zygisk_external
    !status.zygiskEnabled || !status.engineAvailable -> R.string.sui_zygisk_disabled
    status.injectionState == "injected" -> R.string.sui_zygisk_injected
    else -> R.string.sui_zygisk_pending
}
