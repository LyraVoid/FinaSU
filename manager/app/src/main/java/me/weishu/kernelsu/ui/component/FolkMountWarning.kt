package me.weishu.kernelsu.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkMountStatus

@Composable
internal fun folkMountWarningText(status: FolkMountStatus): String? =
    when (status.bootWarningCode) {
        "nomount_ui_fallback" -> stringResource(R.string.settings_folk_mount_nomount_ui_fallback)
        "nomount_ui_unconfirmed" -> stringResource(R.string.settings_folk_mount_nomount_ui_unconfirmed)
        "nomount_driver_unavailable" ->
            stringResource(R.string.settings_folk_mount_nomount_unavailable_hint)
        "mount_step_interrupted" -> stringResource(R.string.settings_folk_mount_step_interrupted)
        else -> status.bootWarning
    }
