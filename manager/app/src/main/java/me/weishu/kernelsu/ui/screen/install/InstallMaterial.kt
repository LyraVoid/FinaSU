package me.weishu.kernelsu.ui.screen.install

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveTabRow
import me.weishu.kernelsu.ui.component.material.FolkButton
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.SegmentedCheckboxItem
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.util.LkmSelection

/**
 * @author weishu
 * @date 2024/3/12.
 */
@Composable
internal fun InstallScreenMaterial(
    uiState: InstallUiState,
    actions: InstallScreenActions,
    snackBarHost: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            TopBar(
                onBack = actions.onBack,
                scrollBehavior = scrollBehavior,
                selectedMode = uiState.installMode,
                onModeSelected = actions.onSelectMode,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.safeDrawingPadding()) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(top = 16.dp)
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            when (uiState.installMode) {
                InstallMode.Lkm -> LkmInstallContent(uiState, actions)
                InstallMode.AnyKernel -> AnyKernelInstallContent(uiState, actions)
            }
            FolkButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = uiState.installMethod != null &&
                    (uiState.installMethod !is InstallMethod.AnyKernel || uiState.installMethod.uri != null),
                onClick = actions.onNext
            ) { Text(stringResource(R.string.install_next)) }
        }
    }
}

@Composable
private fun LkmInstallContent(
    uiState: InstallUiState,
    actions: InstallScreenActions,
) {
    SelectInstallMethod(
        state = uiState,
        onSelected = actions.onSelectMethod,
        onDownloadFile = actions.onDownloadFile,
        onSelectBootImage = actions.onSelectBootImage,
        onSelectAnyKernel = actions.onSelectAnyKernel,
    )
    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = buildList {
            val isDownload = uiState.installMethod is InstallMethod.DownloadFile
            val partitionItems = if (isDownload) {
                uiState.remoteDisplayPartitions
            } else {
                uiState.displayPartitions
            }
            val partitionIndex = if (isDownload) {
                uiState.remotePartitionSelectionIndex
            } else {
                uiState.partitionSelectionIndex
            }
            if (partitionItems.isNotEmpty()) add {
                SegmentedDropdownItem(
                    enabled = uiState.canSelectPartition,
                    items = partitionItems,
                    selectedIndex = partitionIndex,
                    title = if (isDownload) {
                        stringResource(R.string.install_select_partition)
                    } else {
                        "${stringResource(R.string.install_select_partition)} (${uiState.slotSuffix})"
                    },
                    onItemSelected = actions.onSelectPartition,
                    icon = Icons.Filled.Edit
                )
            }
            if (uiState.canForceBackup) add {
                SegmentedCheckboxItem(
                    title = stringResource(R.string.install_force_backup),
                    summary = stringResource(R.string.install_force_backup_summary),
                    onCheckedChange = actions.onSelectForceBackup,
                    checked = uiState.forceBackup,
                )
            }
            add {
                SegmentedListItem(
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            null
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.install_upload_lkm_file)) },
                    supportingContent = {
                        (uiState.lkmSelection as? LkmSelection.LkmUri)?.let {
                            Text(
                                stringResource(
                                    R.string.selected_lkm,
                                    it.uri.lastPathSegment ?: "(file)"
                                )
                            )
                        }
                    },
                    trailingContent = {
                        if (uiState.lkmSelection is LkmSelection.LkmUri) {
                            FolkIconButton(onClick = actions.onClearLkm) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(android.R.string.cancel)
                                )
                            }
                        } else {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                    },
                    onClick = actions.onUploadLkm
                )
            }
        }
    )

    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        item {
            val rotationState by animateFloatAsState(
                targetValue = if (uiState.advancedOptionsShown) 180f else 0f,
                label = "RotationAnimation"
            )
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.advanced_options)) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = stringResource(R.string.expand),
                        modifier = Modifier.graphicsLayer { rotationZ = rotationState }
                    )
                },
                onClick = actions.onAdvancedOptionsClicked
            )
        }
        item(visible = uiState.advancedOptionsShown) {
            SegmentedCheckboxItem(
                title = stringResource(id = R.string.allow_shell),
                summary = stringResource(id = R.string.allow_shell_summary),
                checked = uiState.allowShell,
                onCheckedChange = actions.onSelectAllowShell,
            )
        }
        item(visible = uiState.advancedOptionsShown) {
            SegmentedCheckboxItem(
                title = stringResource(id = R.string.enable_adb),
                summary = stringResource(id = R.string.enable_adb_summary),
                checked = uiState.enableAdb,
                onCheckedChange = actions.onSelectEnableAdb,
            )
        }
        item(visible = uiState.advancedOptionsShown) {
            SegmentedCheckboxItem(
                title = stringResource(id = R.string.install_backup_before_flash),
                summary = stringResource(id = R.string.install_backup_before_flash_summary),
                checked = uiState.backupBeforeFlash,
                onCheckedChange = actions.onSelectBackupBeforeFlash,
            )
        }
    }
}

@Composable
private fun AnyKernelInstallContent(
    uiState: InstallUiState,
    actions: InstallScreenActions,
) {
    if (!uiState.anyKernelAvailable) {
        Text(
            text = stringResource(R.string.install_anykernel_root_required),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        return
    }

    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SegmentedListItem(
                leadingContent = {
                    Icon(Icons.Filled.FileUpload, null)
                },
                headlineContent = { Text(stringResource(R.string.install_anykernel)) },
                supportingContent = {
                    Text(
                        uiState.anyKernelFileName?.let {
                            stringResource(
                                R.string.install_anykernel_file_summary,
                                it,
                                uiState.anyKernelSizeLabel
                                    ?: stringResource(R.string.install_anykernel_size_unknown),
                            )
                        } ?: stringResource(R.string.install_anykernel_summary)
                    )
                },
                onClick = actions.onSelectAnyKernel,
            )
        }
        if (uiState.showAnyKernelSlotSelector) {
            item {
                SegmentedDropdownItem(
                    title = stringResource(R.string.install_anykernel_target_slot),
                    items = uiState.anyKernelSlotOptions,
                    selectedIndex = uiState.anyKernelSlotIndex,
                    onItemSelected = actions.onSelectAnyKernelSlot,
                    icon = Icons.Filled.Edit,
                )
            }
        }
    }
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SegmentedListItem(
                leadingContent = {
                    Icon(Icons.Filled.Warning, null)
                },
                headlineContent = { Text(stringResource(R.string.install_mode_anykernel)) },
                supportingContent = {
                    Text(stringResource(R.string.install_anykernel_page_hint))
                },
            )
        }
    }
}

@Composable
private fun SelectInstallMethod(
    state: InstallUiState,
    onSelected: (InstallMethod) -> Unit,
    onDownloadFile: () -> Unit,
    onSelectBootImage: () -> Unit,
    onSelectAnyKernel: () -> Unit,
) {
    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            onSelected(InstallMethod.DirectInstallToInactiveSlot)
        },
        onDismiss = null
    )
    val dialogTitle = stringResource(android.R.string.dialog_alert_title)
    val dialogContent = stringResource(R.string.install_inactive_slot_warning)

    val onClick = { option: InstallMethod ->
        when (option) {
            is InstallMethod.AnyKernel -> onSelectAnyKernel()
            is InstallMethod.SelectFile -> onSelectBootImage()
            is InstallMethod.DownloadFile -> onDownloadFile()
            is InstallMethod.DirectInstall -> onSelected(option)
            is InstallMethod.DirectInstallToInactiveSlot -> confirmDialog.showConfirm(dialogTitle, dialogContent)
        }
    }

    key(state.installMethodOptions.size) {
        SegmentedColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            content = state.installMethodOptions.map { option ->
                {
                    SegmentedRadioItem(
                        title = stringResource(option.label),
                        summary = option.summary,
                        selected = option.javaClass == state.installMethod?.javaClass,
                        onClick = { onClick(option) }
                    )
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    selectedMode: InstallMode,
    onModeSelected: (InstallMode) -> Unit,
) {
    Column {
        LargeFlexibleTopAppBar(
            title = { Text(stringResource(R.string.install)) },
            navigationIcon = {
                TopBarBackButton(onClick = onBack)
            },
            colors = expressiveTopAppBarColors(),
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            scrollBehavior = scrollBehavior
        )
        ExpressiveTabRow(
            selectedTabIndex = selectedMode.ordinal,
            tabs = listOf(
                stringResource(R.string.install_mode_lkm),
                stringResource(R.string.install_mode_anykernel),
            ),
            onTabClick = {
                onModeSelected(if (it == 0) InstallMode.Lkm else InstallMode.AnyKernel)
            },
        )
    }
}
