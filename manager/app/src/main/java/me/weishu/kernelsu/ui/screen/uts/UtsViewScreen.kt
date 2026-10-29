package me.weishu.kernelsu.ui.screen.uts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.uts.UtsViewPhase
import me.weishu.kernelsu.data.uts.UtsViewMode
import me.weishu.kernelsu.data.uts.UtsViewStatus
import me.weishu.kernelsu.ui.component.kernel.BootSlotSource
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SegmentedTextField
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.FolkMotion
import me.weishu.kernelsu.ui.viewmodel.UtsViewViewModel

private val primaryFields = listOf(2, 3)
private val advancedFields = listOf(0, 1, 4, 5)

private val fieldLabels = listOf(
    R.string.uts_view_sysname,
    R.string.uts_view_nodename,
    R.string.uts_view_release,
    R.string.uts_view_version,
    R.string.uts_view_machine,
    R.string.uts_view_domainname,
)

@Composable
fun UtsViewScreen() {
    val viewModel = viewModel<UtsViewViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmTakeOver by rememberSaveable { mutableStateOf(false) }
    var confirmGlobal by rememberSaveable { mutableStateOf(false) }
    var showTargets by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    val status = state.status
    val provider = status.provider
    val globalMode = status.mode == UtsViewMode.Global
    val values = state.draft
    val dirty = state.hasLoaded && values != status.fields
    val fieldValues = listOf(
        values.sysname,
        values.nodename,
        values.release,
        values.version,
        values.machine,
        values.domainname,
    )
    val canEdit = status.supported && !state.busy && status.phase != UtsViewPhase.Error
    val canDelete = !state.busy && status.hasPersistedConfig
    val observation = status.observation
    val diagnosticsSummary = if (observation.matched > 0) {
        stringResource(R.string.uts_view_diagnostics_summary_matched, observation.matched)
    } else {
        stringResource(R.string.uts_view_diagnostics_summary_none)
    }
    val diagnosticsArrowRotation by animateFloatAsState(
        targetValue = if (showDiagnostics) 180f else 0f,
        animationSpec = FolkMotion.IconRotation,
        label = "utsDiagnosticsArrow",
    )
    val skipReasons = buildList {
        if (observation.skipNotTarget > 0) {
            add(stringResource(R.string.uts_view_skip_not_target, observation.skipNotTarget))
        }
        if (observation.skipNotApp > 0) {
            add(stringResource(R.string.uts_view_skip_not_app, observation.skipNotApp))
        }
        if (observation.skipNotZygote > 0) {
            add(stringResource(R.string.uts_view_skip_not_app_launch, observation.skipNotZygote))
        }
        if (observation.skipPrivateNamespace > 0) {
            add(stringResource(R.string.uts_view_skip_private_environment, observation.skipPrivateNamespace))
        }
        if (observation.skipIsolated > 0) {
            add(stringResource(R.string.uts_view_skip_isolated, observation.skipIsolated))
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(showTargets) {
        if (showTargets) viewModel.loadApps()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (confirmDelete) {
        val susfsProvider = provider == "susfs"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(stringResource(
                    if (susfsProvider) R.string.uts_view_delete_uts_title else R.string.uts_view_delete_title,
                ))
            },
            text = {
                Text(stringResource(
                    if (susfsProvider) R.string.uts_view_delete_uts_message else R.string.uts_view_delete_message,
                ))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.clear()
                }) {
                    Text(stringResource(
                        if (susfsProvider) R.string.uts_view_delete_uts_settings else R.string.uts_view_delete,
                    ))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (confirmTakeOver) {
        AlertDialog(
            onDismissRequest = { confirmTakeOver = false },
            title = { Text(stringResource(R.string.uts_view_take_over_title)) },
            text = { Text(stringResource(R.string.uts_view_take_over_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTakeOver = false
                    viewModel.takeOver()
                }) { Text(stringResource(R.string.uts_view_take_over)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTakeOver = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (confirmGlobal) {
        AlertDialog(
            onDismissRequest = { confirmGlobal = false },
            title = { Text(stringResource(R.string.uts_view_global_confirm_title)) },
            text = { Text(stringResource(R.string.uts_view_global_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmGlobal = false
                    viewModel.setGlobalMode(true)
                }) { Text(stringResource(R.string.uts_view_global_enable)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmGlobal = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showTargets) {
        TargetAppSheet(
            apps = state.apps,
            selectedTargets = status.targets,
            loading = state.appsLoading || !state.appsLoaded,
            onDismiss = { showTargets = false },
            onConfirm = { targets ->
                showTargets = false
                viewModel.setTargets(targets)
            },
            maxTargets = 128,
            title = stringResource(R.string.uts_view_targets_title),
            countText = { selected, maximum ->
                stringResource(R.string.uts_view_targets_count, selected, maximum)
            },
        )
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.uts_view_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SegmentedColumn {
                item {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.TextFields,
                        title = stringResource(R.string.uts_view_enable_switch),
                        summary = stringResource(
                            R.string.uts_view_state_value,
                            phaseLabel(status),
                            phaseSummary(status),
                        ),
                        checked = status.enabled,
                        enabled = canEdit && provider != "susfs",
                        onCheckedChange = { checked ->
                            if (checked && dirty) {
                                viewModel.saveAndEnable()
                            } else {
                                viewModel.setEnabled(checked)
                            }
                        },
                    )
                }
                item {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.uts_view_current_kernel)) },
                        supportingContent = {
                            Text(
                                if (provider == "susfs") {
                                    stringResource(R.string.uts_view_truth_unavailable)
                                } else {
                                    state.currentUname?.release ?: "-"
                                }
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Info, null) },
                    )
                }
                if (provider == "susfs") {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.uts_view_provider)) },
                            supportingContent = { Text(stringResource(R.string.uts_view_provider_susfs)) },
                            leadingContent = { Icon(Icons.Filled.Info, null) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.uts_view_take_over)) },
                            supportingContent = { Text(stringResource(R.string.uts_view_take_over_message)) },
                            leadingContent = { Icon(Icons.Filled.SwapHoriz, null) },
                            enabled = canEdit,
                            onClick = { confirmTakeOver = true },
                        )
                    }
                }
            }

            (
                status.configError
                    ?: status.providerError
                    ?: status.runtimeError
                    ?: status.kernelError
                )?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            SegmentedColumn(title = stringResource(R.string.uts_view_scope)) {
                item {
                    SegmentedRadioItem(
                        title = stringResource(R.string.uts_view_selected_mode),
                        summary = stringResource(R.string.uts_view_selected_mode_summary),
                        selected = !globalMode,
                        enabled = canEdit && provider != "susfs",
                        onClick = { if (globalMode) viewModel.setGlobalMode(false) },
                    )
                }
                item {
                    SegmentedRadioItem(
                        title = stringResource(R.string.uts_view_global_mode),
                        summary = stringResource(R.string.uts_view_global_mode_summary),
                        selected = globalMode,
                        enabled = canEdit && provider != "susfs",
                        onClick = { if (!globalMode) confirmGlobal = true },
                    )
                }
                if (!globalMode) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.uts_view_target_apps)) },
                            supportingContent = {
                                Text(
                                    when {
                                        status.targets.isEmpty() ->
                                            stringResource(R.string.uts_view_no_target_apps)
                                        else -> stringResource(
                                            R.string.uts_view_selected_target_count,
                                            status.targets.size,
                                        )
                                    },
                                )
                            },
                            leadingContent = { Icon(Icons.Filled.Apps, null) },
                            enabled = canEdit,
                            onClick = { showTargets = true },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            BootSlotSource(
                facts = state.kernelFacts,
                loading = state.kernelFactsLoading,
                error = state.kernelFactsError,
                onRefresh = viewModel::refresh,
                onUseKernel = { viewModel.useBootSlot(it.id) },
                draftSourceSlot = state.draftSourceSlot,
                draftSourceIdentity = state.draftSourceIdentity,
            )

            Spacer(Modifier.height(16.dp))
            SegmentedColumn(title = stringResource(R.string.uts_view_template)) {
                item {
                    val fillDetail = if (provider == "susfs") {
                        stringResource(R.string.uts_view_truth_unavailable)
                    } else if (status.active) {
                        stringResource(R.string.uts_view_fill_unavailable)
                    } else {
                        state.currentUname?.let { "${it.sysname} ${it.release} ${it.machine}" } ?: "-"
                    }
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.uts_view_fill_current)) },
                        supportingContent = {
                            Text("$fillDetail\n${stringResource(R.string.uts_view_required_hint)}")
                        },
                        leadingContent = { Icon(Icons.Filled.ContentCopy, null) },
                        enabled = canEdit && provider != "susfs",
                        onClick = viewModel::fillCurrentValues,
                    )
                }
                primaryFields.forEach { index ->
                    item {
                        SegmentedTextField(
                            label = stringResource(fieldLabels[index]),
                            value = fieldValues[index],
                            onValueChange = { viewModel.setField(index, it) },
                            enabled = canEdit,
                            singleLine = true,
                        )
                    }
                }
                item {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(R.string.uts_view_advanced_fields)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (showAdvanced) {
                                        R.string.uts_view_advanced_hide
                                    } else {
                                        R.string.uts_view_advanced_show
                                    },
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Tune, null) },
                        onClick = { showAdvanced = !showAdvanced },
                    )
                }
                if (showAdvanced) {
                    advancedFields.forEach { index ->
                        item {
                            SegmentedTextField(
                                label = stringResource(fieldLabels[index]),
                                value = fieldValues[index],
                                onValueChange = { viewModel.setField(index, it) },
                                enabled = canEdit,
                                singleLine = true,
                            )
                        }
                    }
                }
                if (dirty) {
                    item {
                        SegmentedListItem(
                            headlineContent = {
                                Text(
                                    stringResource(
                                        if (status.enabled) {
                                            R.string.uts_view_save_apply_changes
                                        } else {
                                            R.string.uts_view_save_draft
                                        },
                                    ),
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (status.enabled) {
                                            R.string.uts_view_save_apply_changes_summary
                                        } else {
                                            R.string.uts_view_save_draft_summary
                                        },
                                    ),
                                )
                            },
                            leadingContent = { Icon(Icons.Filled.Save, null) },
                            enabled = canEdit,
                            onClick = viewModel::save,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (!globalMode) {
                SegmentedColumn(title = stringResource(R.string.uts_view_check_effect)) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.uts_view_diagnostics)) },
                            supportingContent = {
                                Text(
                                    if (showDiagnostics) {
                                        stringResource(R.string.uts_view_diagnostics_hide)
                                    } else {
                                        diagnosticsSummary
                                    },
                                )
                            },
                            leadingContent = { Icon(Icons.Filled.BugReport, null) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = diagnosticsArrowRotation
                                    },
                                )
                            },
                            onClick = { showDiagnostics = !showDiagnostics },
                        )
                    }
                    item(key = "diagnostics_recent", visible = showDiagnostics) {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.uts_view_diagnostics_recent)) },
                            supportingContent = {
                                Text(
                                    if (observation.matched > 0) {
                                        stringResource(
                                            R.string.uts_view_diagnostics_latest,
                                            observation.lastUid,
                                            observation.lastPid,
                                        )
                                    } else {
                                        stringResource(R.string.uts_view_diagnostics_no_match)
                                    },
                                )
                            },
                        )
                    }
                    item(key = "diagnostics_skips", visible = showDiagnostics) {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.uts_view_diagnostics_skips)) },
                            supportingContent = {
                                Text(
                                    skipReasons.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
                                        ?: stringResource(R.string.uts_view_diagnostics_no_skips),
                                )
                            },
                        )
                    }
                    item(
                        key = "diagnostics_failure",
                        visible = showDiagnostics && observation.unshareFailed > 0,
                    ) {
                        SegmentedListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.uts_view_diagnostics_failure),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(
                                        R.string.uts_view_diagnostics_unshare_failed,
                                        observation.unshareFailed,
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                    item(key = "diagnostics_technical", visible = showDiagnostics) {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.uts_view_diagnostics_technical)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.uts_view_diagnostics_technical_value,
                                        observation.generation,
                                        observation.discarded,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SegmentedColumn(title = stringResource(R.string.uts_view_danger_zone)) {
                item {
                    SegmentedListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(
                                    if (provider == "susfs") R.string.uts_view_delete_uts_settings
                                    else R.string.uts_view_restore_real,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        supportingContent = {
                            Text(stringResource(
                                if (provider == "susfs") R.string.uts_view_delete_uts_summary
                                else R.string.uts_view_delete_summary,
                            ))
                        },
                        leadingContent = { Icon(Icons.Filled.Restore, null) },
                        enabled = canDelete,
                        onClick = { confirmDelete = true },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun phaseLabel(status: UtsViewStatus): String = stringResource(
    if (status.mode == UtsViewMode.Global && status.phase == UtsViewPhase.Enabled) {
        R.string.uts_view_phase_global_enabled
    } else {
        when (status.phase) {
            UtsViewPhase.Unsupported -> R.string.uts_view_phase_unsupported
            UtsViewPhase.Error -> R.string.uts_view_phase_error
            UtsViewPhase.Blocked -> R.string.uts_view_phase_blocked
            UtsViewPhase.Observed -> R.string.uts_view_phase_observed
            UtsViewPhase.Enabled -> R.string.uts_view_phase_enabled
            UtsViewPhase.Configured -> R.string.uts_view_phase_configured
            UtsViewPhase.NotConfigured -> R.string.uts_view_phase_not_configured
        }
    },
)

@Composable
private fun phaseSummary(status: UtsViewStatus): String = stringResource(
    if (status.mode == UtsViewMode.Global && status.phase == UtsViewPhase.Enabled) {
        R.string.uts_view_phase_global_enabled_summary
    } else {
        when (status.phase) {
            UtsViewPhase.Unsupported -> R.string.uts_view_phase_unsupported_summary
            UtsViewPhase.Error -> R.string.uts_view_phase_error_summary
            UtsViewPhase.Blocked -> R.string.uts_view_phase_blocked_summary
            UtsViewPhase.Observed -> R.string.uts_view_phase_observed_summary
            UtsViewPhase.Enabled -> R.string.uts_view_phase_enabled_summary
            UtsViewPhase.Configured -> R.string.uts_view_phase_configured_summary
            UtsViewPhase.NotConfigured -> R.string.uts_view_phase_not_configured_summary
        }
    },
)
