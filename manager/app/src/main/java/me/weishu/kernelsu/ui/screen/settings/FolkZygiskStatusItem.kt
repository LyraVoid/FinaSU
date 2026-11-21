package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.dialog.ConfirmResult
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.screen.builtinzygisk.PanelAction
import me.weishu.kernelsu.ui.screen.builtinzygisk.mapStatus

internal fun showFolkZygiskRecovery(state: SettingsUiState) = state.folkZygiskStatus?.startupBlocked == true

@Composable
internal fun FolkZygiskStatusItem(state: SettingsUiState, actions: SettingsScreenActions) {
    val panel = state.folkZygiskStatus?.takeUnless { state.folkZygiskReadError }?.let { mapStatus(it, it.runtime) }
    val status = state.folkZygiskStatus
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val selectedAction = if (status?.enabled == true) PanelAction.Disable else PanelAction.Enable
    val consequence = stringResource(selectedAction.consequence)
    val title = stringResource(selectedAction.title)
    val summary = stringResource(panel?.phase?.title ?: if (state.isFolkZygiskLoading) R.string.folkzygisk_loading else R.string.folkzygisk_read_error) +
        status?.let { "\n" + stringResource(if (it.enabled) R.string.folkzygisk_next_enabled else R.string.folkzygisk_next_disabled) }.orEmpty()
    SegmentedSwitchItem(icon = Icons.Filled.Code, title = stringResource(R.string.settings_folk_zygisk),
        summary = summary,
        checked = status?.enabled == true, enabled = !state.isFolkZygiskWriting && panel?.canToggle == true,
        onCheckedChange = { checked -> scope.launch {
            if (confirm.awaitConfirm(title = title, content = consequence) is ConfirmResult.Confirmed) actions.onSetFolkZygiskEnabled(checked)
        } })
    if (!state.isFolkZygiskLoading) {
        SegmentedListItem(headlineContent = { Text(stringResource(R.string.uts_view_diagnostics)) },
            supportingContent = { Text(stringResource(if (showFolkZygiskRecovery(state)) R.string.folkzygisk_recover_summary else R.string.settings_folk_zygisk)) },
            leadingContent = { Icon(Icons.Filled.Info, null) },
            onClick = actions.onOpenFolkZygisk)
    }
}
