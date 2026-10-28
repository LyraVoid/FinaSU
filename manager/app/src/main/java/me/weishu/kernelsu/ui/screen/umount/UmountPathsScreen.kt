package me.weishu.kernelsu.ui.screen.umount

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.umount.UmountPath
import me.weishu.kernelsu.data.umount.UmountPathError
import me.weishu.kernelsu.data.umount.UmountPathSource
import me.weishu.kernelsu.data.umount.UmountPathsData
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.settings.SettingsCategory
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.UmountPathsNotice
import me.weishu.kernelsu.ui.viewmodel.UmountPathsUiState
import me.weishu.kernelsu.ui.viewmodel.UmountPathsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UmountPathsScreen() {
    val viewModel = viewModel<UmountPathsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults
        .exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<UmountPath?>(null) }
    var pendingClear by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.umount_paths_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                actions = {
                    TextButton(
                        onClick = { showAdd = true },
                        enabled = state.canWrite,
                    ) {
                        Text(stringResource(R.string.umount_paths_add))
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IntroCard()
            ConditionsSection(
                onOpenFunctionSettings = {
                    val functionSettings =
                        Route.SettingsCategory(SettingsCategory.FUNCTION.key)
                    if (functionSettings in navigator.backStack) {
                        navigator.popUntil { it == functionSettings }
                    } else {
                        navigator.push(functionSettings)
                    }
                },
            )
            StatusSection(state, viewModel)
            PathsSection(
                state = state,
                onAdd = { showAdd = true },
                onDelete = { pendingDelete = it },
                onClear = { pendingClear = true },
            )
        }
    }

    if (showAdd) {
        AddPathDialog(
            onDismiss = { showAdd = false },
            onAdd = { path ->
                showAdd = false
                viewModel.add(path)
            },
        )
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.umount_paths_delete_title)) },
            text = { Text(stringResource(R.string.umount_paths_delete_body, entry.path)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.delete(entry.path)
                    }
                ) {
                    Text(stringResource(R.string.umount_paths_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text(stringResource(R.string.umount_paths_clear_title)) },
            text = { Text(stringResource(R.string.umount_paths_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingClear = false
                        viewModel.clear()
                    }
                ) {
                    Text(stringResource(R.string.umount_paths_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun IntroCard() {
    TonalCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(imageVector = Icons.Filled.Info, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.umount_paths_intro_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.umount_paths_intro_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ConditionsSection(onOpenFunctionSettings: () -> Unit) {
    TonalCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.umount_paths_conditions_title),
                style = MaterialTheme.typography.titleMedium,
            )
            ConditionRow(
                icon = Icons.Filled.PowerSettingsNew,
                title = R.string.umount_paths_condition_kernel,
                summary = R.string.umount_paths_condition_kernel_summary,
                action = R.string.umount_paths_open_settings,
                onAction = onOpenFunctionSettings,
            )
            ConditionRow(
                icon = Icons.AutoMirrored.Filled.Rule,
                title = R.string.umount_paths_condition_profile,
                summary = R.string.umount_paths_condition_profile_summary,
            )
            ConditionRow(
                icon = Icons.Filled.Folder,
                title = R.string.umount_paths_condition_mountpoint,
                summary = R.string.umount_paths_condition_mountpoint_summary,
            )
            ConditionRow(
                icon = Icons.Filled.RestartAlt,
                title = R.string.umount_paths_condition_restart,
                summary = R.string.umount_paths_condition_restart_summary,
            )
        }
    }
}

@Composable
private fun ConditionRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes summary: Int,
    @StringRes action: Int? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(stringResource(action))
                }
            }
        }
    }
}

@Composable
private fun StatusSection(state: UmountPathsUiState, viewModel: UmountPathsViewModel) {
    if (!state.readError && state.kernelSupported && state.configReadable && state.notice == null) return
    SegmentedColumn(title = stringResource(R.string.umount_paths_status)) {
        item(visible = !state.kernelSupported) {
            Message(stringResource(R.string.umount_paths_kernel_unsupported))
        }
        item(visible = !state.configReadable) {
            Message(stringResource(R.string.umount_paths_config_unreadable))
        }
        item(visible = state.readError) {
            Message(stringResource(R.string.umount_paths_read_failed))
        }
        item(visible = state.actionError) {
            Message(stringResource(R.string.umount_paths_action_failed))
        }
        item(visible = state.notice != null) {
            Message(noticeText(state.notice))
        }
        item(visible = state.readError || !state.kernelSupported || !state.configReadable) {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.umount_paths_refresh)) },
                leadingContent = { Icon(Icons.Filled.Refresh, null) },
                onClick = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    SegmentedListItem(
        headlineContent = { Text(text) },
        enabled = false,
        onClick = {},
    )
}

@Composable
private fun noticeText(notice: UmountPathsNotice?): String = stringResource(
    when (notice) {
        UmountPathsNotice.ADDED -> R.string.umount_paths_added
        UmountPathsNotice.DELETED -> R.string.umount_paths_deleted
        UmountPathsNotice.CLEARED -> R.string.umount_paths_cleared
        null -> R.string.umount_paths_read_failed
    }
)

@Composable
private fun PathsSection(
    state: UmountPathsUiState,
    onAdd: () -> Unit,
    onDelete: (UmountPath) -> Unit,
    onClear: () -> Unit,
) {
    SegmentedColumn(title = stringResource(R.string.umount_paths_custom_section)) {
        if (state.custom.isEmpty()) {
            item {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.umount_paths_custom_empty)) },
                    supportingContent = {
                        Text(stringResource(R.string.umount_paths_custom_empty_summary))
                    },
                    leadingContent = { Icon(Icons.Filled.Add, null) },
                    enabled = state.canWrite,
                    onClick = onAdd,
                )
            }
        } else {
            state.custom.forEach { entry ->
                item {
                    PathRow(
                        entry = entry,
                        actionable = state.canWrite,
                        onClick = { onDelete(entry) },
                    )
                }
            }
            item {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.umount_paths_clear)) },
                    supportingContent = {
                        Text(stringResource(R.string.umount_paths_clear_summary))
                    },
                    leadingContent = { Icon(Icons.Filled.DeleteSweep, null) },
                    enabled = state.canWrite,
                    onClick = onClear,
                )
            }
        }
    }

    SegmentedColumn(title = stringResource(R.string.umount_paths_module_section)) {
        if (state.kernelEntries.isEmpty()) {
            item {
                Message(stringResource(R.string.umount_paths_module_empty))
            }
        } else {
            state.kernelEntries.forEach { entry ->
                item { PathRow(entry = entry, actionable = false, onClick = {}) }
            }
        }
    }

    if (state.unknown.isNotEmpty()) {
        SegmentedColumn(title = stringResource(R.string.umount_paths_unknown_section)) {
            state.unknown.forEach { entry ->
                item { PathRow(entry = entry, actionable = false, onClick = {}) }
            }
        }
    }
}

@Composable
private fun PathRow(entry: UmountPath, actionable: Boolean, onClick: () -> Unit) {
    val source = when (entry.source) {
        UmountPathSource.CUSTOM -> R.string.umount_paths_source_custom
        UmountPathSource.KERNEL -> R.string.umount_paths_source_module
        UmountPathSource.UNKNOWN -> R.string.umount_paths_source_unknown
    }
    SegmentedListItem(
        headlineContent = { Text(entry.path) },
        supportingContent = {
            Text(
                stringResource(source) + " · " +
                    flagsText(entry.flags)
            )
        },
        leadingContent = { Icon(Icons.Filled.LayersClear, null) },
        trailingContent = if (actionable) {
            {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.umount_paths_delete),
                )
            }
        } else {
            null
        },
        enabled = actionable,
        onClick = onClick,
    )
}

@Composable
private fun flagsText(flags: Int): String = stringResource(
    when (flags) {
        0 -> R.string.umount_paths_flags_standard
        UmountPathsData.MNT_DETACH -> R.string.umount_paths_flags_detach
        else -> R.string.umount_paths_flags_other
    }
)

@Composable
private fun AddPathDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var path by remember { mutableStateOf("") }
    val validation = path.takeIf(String::isNotEmpty)?.let(UmountPathsData::validatePath)
    val errorRes = validation?.let(::validationResource)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.umount_paths_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.umount_paths_add_summary))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.umount_paths_path_label)) },
                    singleLine = true,
                    isError = errorRes != null,
                    supportingText = {
                        Text(
                            errorRes?.let { stringResource(it) }
                                ?: stringResource(R.string.umount_paths_path_hint)
                        )
                    },
                )
                Text(
                    text = stringResource(R.string.umount_paths_remove_mode),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.umount_paths_flags_detach),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.umount_paths_flags_detach_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(path) },
                enabled = path.isNotEmpty() && validation == null,
            ) {
                Text(stringResource(R.string.umount_paths_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun validationResource(error: UmountPathError): Int =
    when (error) {
        UmountPathError.NOT_ABSOLUTE -> R.string.umount_path_invalid_absolute
        UmountPathError.ROOT -> R.string.umount_path_invalid_root
        UmountPathError.TOO_LONG -> R.string.umount_path_invalid_length
        UmountPathError.TRAILING_SLASH -> R.string.umount_path_invalid_trailing_slash
        UmountPathError.EMPTY_SEGMENT -> R.string.umount_path_invalid_empty_segment
        UmountPathError.DOT_SEGMENT -> R.string.umount_path_invalid_segment
        UmountPathError.CONTROL_CHARACTER -> R.string.umount_path_invalid_control
        UmountPathError.FORBIDDEN -> R.string.umount_path_invalid_blacklisted
    }
