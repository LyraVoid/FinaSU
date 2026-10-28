package me.weishu.kernelsu.ui.screen.nomount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.NomountDriverState
import me.weishu.kernelsu.data.model.NomountStatus
import me.weishu.kernelsu.ui.component.dialog.ConfirmResult
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.NomountUiState
import me.weishu.kernelsu.ui.viewmodel.NomountViewModel
import me.weishu.kernelsu.ui.viewmodel.shouldShowNomountSetup

/**
 * NoMount driver, injection and visibility state.
 *
 * Every row that can look healthy is gated on the driver actually answering:
 * an unusable driver disables the controls instead of offering actions that
 * would silently do nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomountScreen() {
    val viewModel = viewModel<NomountViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults
        .exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_nomount)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                actions = {
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.nomount_refresh))
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
            SetupSection(state, viewModel)
            DriverSection(state, viewModel)
            InjectionSection(state, viewModel)
            ModulesSection(state, viewModel)
            VisibilitySection(state, viewModel)
        }
    }
}

@Composable
private fun SetupSection(state: NomountUiState, viewModel: NomountViewModel) {
    if (!shouldShowNomountSetup(state.status, state.folkMountStatus)) return
    val mount = state.folkMountStatus ?: return
    val confirm = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.settings_folk_mount_mode_nomount)
    val confirmContent = stringResource(R.string.settings_folk_mount_nomount_confirm)
    val confirmAction = stringResource(R.string.settings_folk_mount_nomount_confirm_ok)
    val warning = mount.bootWarning.takeIf { mount.configuredMode == FolkMountMode.NOMOUNT }
    val summary = listOfNotNull(
        warning ?: stringResource(R.string.settings_folk_mount_mode_nomount_summary),
        stringResource(R.string.settings_folk_mount_reboot_hint),
        stringResource(R.string.nomount_action_failed_summary).takeIf { state.actionError },
    ).joinToString("\n")

    SegmentedColumn(title = stringResource(R.string.settings_folk_mount)) {
        item {
            SegmentedListItem(
                headlineContent = {
                    Text(title)
                },
                supportingContent = { Text(summary) },
                leadingContent = { Icon(Icons.Filled.Layers, null) },
                enabled = !state.isWriting,
                onClick = {
                    scope.launch {
                        val result = confirm.awaitConfirm(
                            title = title,
                            content = confirmContent,
                            confirm = confirmAction,
                        )
                        if (result is ConfirmResult.Confirmed) viewModel.enableNomount()
                    }
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )
        }
    }
}

@Composable
private fun DriverSection(state: NomountUiState, viewModel: NomountViewModel) {
    SegmentedColumn(title = stringResource(R.string.nomount_section_driver)) {
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_driver_state)) },
                supportingContent = { Text(driverSummary(state)) },
                leadingContent = { Icon(Icons.Filled.Layers, null) },
                onClick = viewModel::refresh,
                trailingContent = { Icon(Icons.Filled.Refresh, null) },
            )
        }
        item(visible = state.status?.deferredTo != null) {
            // Another module drives the same driver: say so instead of showing a
            // healthy backend that is not the one injecting.
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_deferred)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.nomount_deferred_summary,
                            state.status?.deferredTo.orEmpty(),
                        )
                    )
                },
                leadingContent = { Icon(Icons.Filled.Layers, null) },
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun driverSummary(state: NomountUiState): String {
    if (state.readError) return stringResource(R.string.nomount_read_error)
    val status = state.status ?: return stringResource(R.string.nomount_loading)
    val label = stringResource(
        when (status.driverState) {
            NomountDriverState.ABSENT -> R.string.nomount_driver_absent
            NomountDriverState.OUTDATED -> R.string.nomount_driver_outdated
            NomountDriverState.READY -> R.string.nomount_driver_ready
        }
    )
    val version = status.driverVersion
    val kind = when (status.driverKind) {
        "builtin" -> stringResource(R.string.nomount_driver_kind_builtin)
        "module" -> stringResource(R.string.nomount_driver_kind_module)
        else -> null
    }
    val reason = if (
        status.unavailableCode == "not_loaded" &&
        state.folkMountStatus?.configuredMode != FolkMountMode.NOMOUNT
    ) {
        stringResource(R.string.settings_folk_mount_mode_nomount_summary)
    } else {
        nomountUnavailableReason(status)
    }
    return listOfNotNull(
        label,
        version?.takeIf { it.isNotEmpty() }?.let { "($it)" },
        kind,
        reason,
    ).joinToString(" ")
}

/** Localized "why is it not usable", so the user gets an actionable sentence. */
@Composable
internal fun nomountUnavailableReason(status: NomountStatus): String? {
    val detail = status.unavailableDetail.orEmpty()
    return when (status.unavailableCode) {
        "no_asset_for_kmi" -> stringResource(R.string.nomount_unavailable_no_asset, detail)
        "outdated" -> stringResource(R.string.nomount_unavailable_outdated, detail)
        "not_loaded" -> stringResource(R.string.nomount_unavailable_not_loaded)
        "unknown_kmi" -> stringResource(R.string.nomount_unavailable_unknown_kmi)
        else -> null
    }
}

@Composable
private fun InjectionSection(state: NomountUiState, viewModel: NomountViewModel) {
    val status = state.status
    val ready = status?.apiOk == true && !state.isWriting
    SegmentedColumn(title = stringResource(R.string.nomount_section_injection)) {
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_rules)) },
                supportingContent = { Text(rulesSummary(status)) },
                leadingContent = { Icon(Icons.Filled.PlayArrow, null) },
                enabled = ready,
                onClick = viewModel::injectRules,
                trailingContent = { Icon(Icons.Filled.PlayArrow, null) },
            )
        }
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_clear_rules)) },
                supportingContent = { Text(stringResource(R.string.nomount_clear_rules_summary)) },
                leadingContent = { Icon(Icons.Filled.DeleteSweep, null) },
                enabled = ready,
                onClick = viewModel::clearRules,
            )
        }
        item(visible = state.actionError) {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_action_failed)) },
                supportingContent = {
                    Text(stringResource(R.string.nomount_action_failed_summary))
                },
                leadingContent = { Icon(Icons.Filled.Refresh, null) },
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun rulesSummary(status: NomountStatus?): String {
    val state = status ?: return stringResource(R.string.nomount_loading)
    val summary = stringResource(
        R.string.nomount_rules_value,
        state.rulesApplied,
        state.rulesExpected,
    )
    return if (state.injected) summary else "$summary\n${stringResource(R.string.nomount_not_injected)}"
}

@Composable
private fun VisibilitySection(state: NomountUiState, viewModel: NomountViewModel) {
    val status = state.status
    val ready = status?.apiOk == true && !state.isWriting
    val navigator = LocalNavigator.current
    SegmentedColumn(title = stringResource(R.string.nomount_section_visibility)) {
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_uid_exclusions)) },
                supportingContent = {
                    val count = status?.uidsActive?.size ?: (status?.uidCount ?: 0)
                    Text(
                        if (count == 0) {
                            stringResource(R.string.nomount_uid_exclusions_none)
                        } else {
                            stringResource(R.string.nomount_uid_exclusions_value, count)
                        }
                    )
                },
                overlineContent = { Text(stringResource(R.string.nomount_uid_source_hint)) },
                leadingContent = { Icon(Icons.Filled.VisibilityOff, null) },
                enabled = ready,
                onClick = { navigator.push(Route.NomountExclusions) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )
        }
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_visibility_model)) },
                supportingContent = {
                    Text(stringResource(R.string.nomount_visibility_model_hint))
                },
                leadingContent = { Icon(Icons.Filled.VisibilityOff, null) },
                enabled = false,
                onClick = {},
            )
        }
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_clear_exclusions)) },
                supportingContent = {
                    Text(stringResource(R.string.nomount_clear_exclusions_summary))
                },
                leadingContent = { Icon(Icons.Filled.DeleteSweep, null) },
                enabled = ready && status?.uids?.isNotEmpty() == true,
                onClick = viewModel::clearExclusions,
            )
        }
        item {
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.nomount_clear_everything)) },
                supportingContent = {
                    Text(stringResource(R.string.nomount_clear_everything_summary))
                },
                leadingContent = { Icon(Icons.Filled.DeleteSweep, null) },
                enabled = ready &&
                    ((status?.rulesApplied ?: 0) > 0 || status?.uids?.isNotEmpty() == true),
                onClick = viewModel::clearEverything,
            )
        }
        item {
            SegmentedSwitchItem(
                icon = Icons.Filled.VisibilityOff,
                title = stringResource(R.string.nomount_block_isolated),
                summary = stringResource(R.string.nomount_block_isolated_summary),
                checked = status?.isolatedBlocked == true,
                enabled = ready,
                onCheckedChange = viewModel::setIsolated,
            )
        }
    }
}

/**
 * Per-module control: how many rules each module asks for, and the hot
 * load/unload the metamodule's WebUI offered without a reboot.
 */
@Composable
private fun ModulesSection(state: NomountUiState, viewModel: NomountViewModel) {
    val status = state.status
    val modules = status?.modules.orEmpty()
    if (modules.isEmpty()) return
    val ready = status?.apiOk == true && !state.isWriting
    SegmentedColumn(title = stringResource(R.string.nomount_section_modules)) {
        item {
            SegmentedListItem(
                headlineContent = {
                    Text(stringResource(R.string.nomount_modules_count, modules.size))
                },
                supportingContent = { Text(stringResource(R.string.nomount_modules_hint)) },
                leadingContent = { Icon(Icons.Filled.Layers, null) },
                enabled = false,
                onClick = {},
            )
        }
        modules.forEach { module ->
            item(key = module.id) {
                SegmentedListItem(
                    headlineContent = { Text(module.id) },
                    supportingContent = {
                        Text(stringResource(R.string.nomount_module_rules, module.rules))
                    },
                    leadingContent = { Icon(Icons.Filled.PlayArrow, null) },
                    enabled = ready,
                    onClick = { viewModel.hotLoad(module.id) },
                    trailingContent = {
                        TextButton(
                            enabled = ready,
                            onClick = { viewModel.hotUnload(module.id) },
                        ) {
                            Text(stringResource(R.string.nomount_hot_unload))
                        }
                    },
                )
            }
        }
    }
}
