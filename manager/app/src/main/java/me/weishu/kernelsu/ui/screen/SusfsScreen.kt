package me.weishu.kernelsu.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.susfs.SusfsInputs
import me.weishu.kernelsu.data.susfs.SusfsErrorKind
import me.weishu.kernelsu.data.susfs.SusfsStatus
import me.weishu.kernelsu.data.susfs.susfsErrorKind
import me.weishu.kernelsu.ui.component.material.*
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.FolkMotion
import me.weishu.kernelsu.ui.theme.FolkShape
import me.weishu.kernelsu.ui.viewmodel.SusfsUiState
import me.weishu.kernelsu.ui.viewmodel.SusfsViewModel
import org.json.JSONArray
import org.json.JSONObject
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

@Composable
fun susfsManagementLabel(status: String): String = stringResource(when (status) {
    "applied" -> R.string.susfs_applied
    "acknowledged_unknown" -> R.string.susfs_acknowledged_unknown
    "unknown" -> R.string.susfs_state_unknown
    "partial" -> R.string.susfs_partial
    "skipped" -> R.string.susfs_skipped
    "failed" -> R.string.susfs_failed
    "pending_reboot" -> R.string.susfs_pending
    "deferred" -> R.string.susfs_deferred
    "idle" -> R.string.susfs_idle
    else -> R.string.susfs_saved
})

private val susfsPages = listOf(R.string.susfs_overview, R.string.susfs_settings,
    R.string.susfs_tab_paths, R.string.susfs_tab_spoof, R.string.susfs_tab_redirect, R.string.susfs_tab_extensions)

@Composable
fun SusfsScreen() {
    val vm = viewModel<SusfsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pager = rememberPagerState { susfsPages.size }
    // Keep all six positions outside pager composition: distant pages may be disposed.
    val scrolls = susfsPages.map { rememberScrollState() }
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var draftPreview by rememberSaveable { mutableStateOf<String?>(null) }
    var draftWarnings by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmApplyAfterSave by rememberSaveable { mutableStateOf(false) }
    var ownershipAction by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreSchema1 by rememberSaveable { mutableStateOf(false) }
    val config = remember(state.config) { state.config?.let(::JSONObject) }
    val writable = state.status.writable && !state.busy && config != null
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(4096)
                        while (buffer.size() <= 128 * 1024) {
                            val count = input.read(chunk, 0, minOf(chunk.size, 128 * 1024 + 1 - buffer.size()))
                            if (count < 0) break
                            buffer.write(chunk, 0, count)
                        }
                        require(buffer.size() <= 128 * 1024) { "Configuration exceeds 128 KiB" }
                        buffer.toByteArray().toString(Charsets.UTF_8)
                    } ?: error("Cannot read configuration")
                }
                var warnings: String? = null
                val preview = runCatching { SusfsInputs.preview(text) }.getOrElse {
                    val response = JSONObject(me.weishu.kernelsu.data.susfs.SusfsRepository().preview(text))
                    warnings = response.optJSONArray("warnings")?.let { values ->
                        List(values.length()) { values.getString(it) }.joinToString("\n")
                    }
                    response.getJSONObject("config")
                }
                draft = text
                draftPreview = preview.toString()
                draftWarnings = warnings
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { vm.message(e.message ?: "Import failed") }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                // Export a fresh saved file, not the screen's potentially obsolete snapshot.
                val text = me.weishu.kernelsu.data.susfs.SusfsRepository().export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Cannot write configuration")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { vm.message(e.message ?: "Export failed") }
        }
    }
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }
    ExpressiveScaffold(topBar = {
        LargeFlexibleTopAppBar(
            title = { Text(stringResource(R.string.susfs_title)) },
            navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
            actions = { TextButton(enabled = !state.busy, onClick = vm::refresh) { Text(stringResource(R.string.susfs_refresh)) } },
            colors = expressiveTopAppBarColors(), scrollBehavior = scrollBehavior,
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            SusfsNavigation(pager.currentPage) { page -> scope.launch { pager.animateScrollToPage(page) } }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                SusfsPage(scrolls[page]) {
                    SusfsNotice(state) { confirmApplyAfterSave = true }
                    when (page) {
                        0 -> SusfsOverview(state, config, writable, vm, { ownershipAction = it },
                            { restoreSchema1 = true },
                            { import.launch(arrayOf("application/json", "text/plain")) },
                            { export.launch("folksu-susfs.json") })
                        1 -> {
                            SusfsBasicPage(config, state.status, writable, vm)
                            SusfsUnameSection(
                                config,
                                state.status,
                                writable,
                                state.kernelFacts,
                                state.kernelFactsLoading,
                                state.kernelFactsError,
                                vm,
                            )
                            SusfsCmdlineSection(
                                config,
                                state.status,
                                writable,
                                state.kernelFacts,
                                state.kernelFactsLoading,
                                state.kernelFactsError,
                                vm,
                            )
                        }
                        2 -> {
                            SusfsRuleSection("path", config, state.status, writable, vm)
                        }
                        3 -> {
                            SusfsRuleSection("map", config, state.status, writable, vm)
                            SusfsRuleSection("kstat", config, state.status, writable, vm)
                        }
                        4 -> SusfsRuleSection("redirect", config, state.status, writable, vm)
                        5 -> SusfsExtensionsPage(config, state.status, writable, vm)
                    }
                }
            }
        }
    }
    ownershipAction?.let { action ->
        AlertDialog(
            onDismissRequest = { ownershipAction = null },
            title = { Text(stringResource(R.string.susfs_owner_confirm_title)) },
            text = { Text(stringResource(when (action) { "confirm" -> R.string.susfs_owner_confirm_message; "cancel" -> R.string.susfs_owner_cancel_message; else -> R.string.susfs_owner_switch_message })) },
            confirmButton = { TextButton(enabled = !state.busy, onClick = {
                when (action) { "confirm" -> vm.confirmOwnership(); "cancel" -> vm.cancelOwnership(); else -> vm.switchOwnership(action) }
                ownershipAction = null
            }) { Text(stringResource(R.string.susfs_owner_confirm)) } },
            dismissButton = { TextButton(onClick = { ownershipAction = null }) { Text(stringResource(R.string.susfs_cancel)) } },
        )
    }
    draft?.let { text ->
        val preview = remember(draftPreview) { JSONObject(checkNotNull(draftPreview)) }
        fun dismissPreview() {
            draft = null
            draftPreview = null
            draftWarnings = null
        }
        AlertDialog(onDismissRequest = ::dismissPreview, title = { Text(stringResource(R.string.susfs_import_preview)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.susfs_import_counts, preview.getJSONArray("rules").length(),
                    preview.getJSONObject("lkm").getJSONArray("hidden_modules").length(),
                    preview.getJSONObject("lkm").getJSONArray("mount_prefixes").length()))
                Text(stringResource(if (preview.getBoolean("automatic")) R.string.susfs_import_auto_on else R.string.susfs_import_auto_off))
                draftWarnings?.let {
                    Text(stringResource(R.string.susfs_import_converted))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.susfs_import_warning))
            } }, confirmButton = { TextButton(enabled = state.status.canImport && !state.busy,
                onClick = { vm.save(text); dismissPreview() }) { Text(stringResource(R.string.susfs_save)) } },
            dismissButton = { TextButton(onClick = ::dismissPreview) { Text(stringResource(R.string.susfs_cancel)) } })
    }
    if (state.pendingUnameApply) {
        AlertDialog(
            onDismissRequest = vm::dismissPendingUnameApply,
            title = { Text(stringResource(R.string.susfs_uts_apply_confirm_title)) },
            text = { Text(stringResource(R.string.susfs_uts_apply_confirm_message)) },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = vm::confirmPendingUnameApply) {
                    Text(stringResource(R.string.susfs_uts_apply_confirm_action))
                }
            },
            dismissButton = {
                TextButton(enabled = !state.busy, onClick = vm::dismissPendingUnameApply) {
                    Text(stringResource(R.string.susfs_cancel))
                }
            },
        )
    }
    if (confirmApplyAfterSave) {
        AlertDialog(
            onDismissRequest = { confirmApplyAfterSave = false },
            title = { Text(stringResource(R.string.susfs_apply_saved_title)) },
            text = { Text(stringResource(R.string.susfs_apply_saved_message)) },
            confirmButton = {
                TextButton(
                    enabled = state.status.writable && !state.busy,
                    onClick = {
                        confirmApplyAfterSave = false
                        vm.apply()
                    },
                ) { Text(stringResource(R.string.susfs_apply_saved_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmApplyAfterSave = false }) {
                    Text(stringResource(R.string.susfs_cancel))
                }
            },
        )
    }
    if (restoreSchema1) {
        AlertDialog(
            onDismissRequest = { restoreSchema1 = false },
            title = { Text(stringResource(R.string.susfs_restore_schema1_title)) },
            text = { Text(stringResource(R.string.susfs_restore_schema1_message)) },
            confirmButton = {
                TextButton(enabled = state.status.canImport && !state.busy, onClick = {
                    restoreSchema1 = false
                    vm.restoreSchema1()
                }) { Text(stringResource(R.string.susfs_restore_schema1_action)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreSchema1 = false }) {
                    Text(stringResource(R.string.susfs_cancel))
                }
            },
        )
    }
}

@Composable
private fun SusfsNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val wallpaper = LocalFolkWallpaperTokens.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        susfsPages.forEachIndexed { index, title ->
            val source = remember { MutableInteractionSource() }
            val requester = remember { BringIntoViewRequester() }
            val pressed by source.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) FolkMotion.PressedScale else 1f,
                animationSpec = if (pressed) FolkMotion.PressDown else FolkMotion.PressScale, label = "susfsTabPress")
            val active = selected == index
            LaunchedEffect(active) { if (active) requester.bringIntoView() }
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                .bringIntoViewRequester(requester)
                .background(if (active) wallpaper?.raised?.fill ?: MaterialTheme.colorScheme.secondaryContainer
                    else wallpaper?.group?.fill ?: MaterialTheme.colorScheme.surfaceContainerLow, FolkShape.CornerFull)
                .selectable(active, interactionSource = source, indication = null, role = Role.Tab) {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onSelect(index)
                }.heightIn(min = 48.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(title), style = MaterialTheme.typography.labelLarge,
                    color = if (active) wallpaper?.raised?.contentColor ?: MaterialTheme.colorScheme.onSecondaryContainer
                        else wallpaper?.group?.supportingColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SusfsPage(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 840.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}

@Composable
internal fun susfsDisabledReason(status: SusfsStatus, supported: Boolean, writable: Boolean? = null): String? = when {
    status.protocol == "root_unavailable" -> stringResource(R.string.susfs_root)
    status.protocol == "unknown_protocol" -> stringResource(R.string.susfs_readonly)
    status.protocol == "not_detected" -> stringResource(R.string.susfs_absent)
    status.protocol != "compatible" -> stringResource(R.string.susfs_probe)
    status.error != null -> stringResource(R.string.susfs_config_invalid)
    status.ownership == "external" -> stringResource(R.string.susfs_owner_external_reason)
    status.ownership == "unknown" -> stringResource(R.string.susfs_owner_unknown_reason)
    status.pendingOwner != null -> stringResource(R.string.susfs_owner_pending_reason)
    writable == false -> stringResource(R.string.susfs_busy_reason)
    !supported -> stringResource(R.string.susfs_unsupported)
    else -> null
}

@Composable
private fun SusfsNotice(state: SusfsUiState, onApplyAfterSave: () -> Unit) {
    val reason = susfsDisabledReason(state.status, true)
    val showManagement = state.status.detected && state.status.management != "idle"
    if (reason != null || showManagement || state.message != null || state.savedNotice) {
        SegmentedColumn {
            reason?.let { item { SegmentedListItem(headlineContent = { Text(it) },
                supportingContent = state.status.error?.let { detail -> ({ Text(detail) }) },
                leadingContent = { Icon(Icons.Rounded.Info, null) }) } }
            if (showManagement) item {
                SegmentedListItem(
                    headlineContent = { Text(susfsManagementLabel(state.status.management)) },
                    supportingContent = { Text(stringResource(susfsManagementHint(state.status))) },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null) },
                )
            }
            state.message?.let { message ->
                item {
                    SegmentedListItem(
                        headlineContent = { Text(message, color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text(susfsErrorHint(message)) },
                    )
                }
            }
            if (state.savedNotice) item { SegmentedListItem(headlineContent = { Text(stringResource(R.string.susfs_save_success)) },
                supportingContent = { Text(stringResource(R.string.susfs_save_success_hint)) }) }
            if (state.savedNotice && state.status.writable) item {
                SegmentedListItem(
                    enabled = !state.busy,
                    onClick = onApplyAfterSave,
                    headlineContent = { Text(stringResource(R.string.susfs_apply_saved)) },
                    leadingContent = { Icon(Icons.Rounded.PlayArrow, null) },
                )
            }
        }
    }
}

@Composable
private fun SusfsOverview(state: SusfsUiState, config: JSONObject?, writable: Boolean, vm: SusfsViewModel,
    ownershipAction: (String) -> Unit, restoreSchema1: () -> Unit, import: () -> Unit, export: () -> Unit) {
    val status = state.status
    SegmentedColumn(title = stringResource(R.string.susfs_overview)) {
        item { SegmentedListItem(headlineContent = { Text(stringResource(when (status.protocol) {
            "compatible" -> R.string.susfs_compatible
            "not_detected" -> R.string.susfs_absent
            "unknown_protocol" -> R.string.susfs_readonly
            "root_unavailable" -> R.string.susfs_root
            else -> R.string.susfs_probe
        })) }, supportingContent = { Text(listOf(status.version, status.implementation, status.variant).filter(String::isNotEmpty).joinToString(" · ")) },
            leadingContent = { Icon(Icons.Rounded.VisibilityOff, null) }) }
        if (status.ownership == "builtin" && status.pendingOwner == null) item {
            SegmentedListItem(headlineContent = { Text(susfsManagementLabel(status.management)) },
                supportingContent = { Text(stringResource(susfsManagementHint(status))) })
        }
        item { SegmentedListItem(
            headlineContent = { Text(stringResource(when {
                status.pendingOwner != null -> R.string.susfs_owner_pending
                status.ownership == "builtin" -> R.string.susfs_owner_builtin
                status.ownership == "external" -> R.string.susfs_owner_external
                else -> R.string.susfs_owner_unknown
            })) },
            supportingContent = { Text(stringResource(R.string.susfs_ownership_unconfirmed)) },
        ) }
        if (status.pendingOwner != null) item { SegmentedListItem(
            enabled = status.switchReady && !state.busy,
            onClick = { ownershipAction("confirm") },
            headlineContent = { Text(stringResource(R.string.susfs_owner_confirm)) },
            supportingContent = { Text(stringResource(if (status.switchReady) R.string.susfs_owner_ready else R.string.susfs_owner_reboot)) },
        ) } else if (status.ownership in setOf("builtin", "external")) item { SegmentedListItem(
            enabled = !state.busy,
            onClick = { ownershipAction(if (status.ownership == "builtin") "external" else "builtin") },
            headlineContent = { Text(stringResource(if (status.ownership == "builtin") R.string.susfs_owner_to_external else R.string.susfs_owner_to_builtin)) },
            supportingContent = { Text(stringResource(R.string.susfs_owner_switch_hint)) },
        ) }
        if (status.pendingOwner != null) item { SegmentedListItem(
            enabled = !state.busy,
            onClick = { ownershipAction("cancel") },
            headlineContent = { Text(stringResource(R.string.susfs_owner_cancel)) },
        ) }
        item { SegmentedListItem(headlineContent = { Text(if (config == null) stringResource(R.string.susfs_config_unavailable)
            else stringResource(R.string.susfs_saved_counts, config.getJSONArray("rules").length())) },
            supportingContent = { Text(stringResource(R.string.susfs_ownership_unconfirmed)) }) }
        item { SegmentedSwitchItem(title = stringResource(R.string.susfs_automatic),
            summary = stringResource(R.string.susfs_automatic_hint), checked = status.automatic, enabled = writable,
            onCheckedChange = { value -> vm.change { it.put("automatic", value) } }) }
        item { SegmentedListItem(enabled = writable && !Natives.isSafeMode, onClick = vm::apply,
            headlineContent = { Text(stringResource(R.string.susfs_apply)) },
            supportingContent = { Text(stringResource(if (Natives.isSafeMode) R.string.susfs_safe_mode else R.string.susfs_apply_hint)) },
            leadingContent = { Icon(Icons.Rounded.PlayArrow, null) }) }
    }
    (state.report ?: status.receipt)?.let { report ->
        SegmentedColumn(title = stringResource(R.string.susfs_last_report)) {
            item { SegmentedListItem(headlineContent = { Text(susfsManagementLabel(report.status)) },
                supportingContent = {
                    Text(listOf(
                        stringResource(R.string.susfs_report_counts, report.applied.size,
                            report.failures.size, report.skipped.size, report.deferred.size,
                            report.pendingReboot.size),
                        stringResource(if (state.report == null) R.string.susfs_report_persisted_hint
                            else R.string.susfs_report_hint),
                    ).joinToString("\n"))
                }) }
            report.applied.forEach { key -> item(key = "ok:$key") { SegmentedListItem(
                headlineContent = { Text(susfsOperationName(key)) }, supportingContent = { Text(stringResource(R.string.susfs_acknowledged)) },
                leadingContent = { Icon(Icons.Rounded.CheckCircle, null) }) } }
            report.failures.forEach { (key, error) -> item(key = "error:$key") { SegmentedListItem(
                headlineContent = { Text(susfsOperationName(key)) }, supportingContent = { Text(error, color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) }) } }
            report.skipped.forEach { (key, reason) -> item(key = "skipped:$key") { SegmentedListItem(
                headlineContent = { Text(susfsOperationName(key)) },
                supportingContent = { Text(reason.ifBlank { stringResource(R.string.susfs_skipped) }) },
                leadingContent = { Icon(Icons.Rounded.Block, null) }) } }
            report.deferred.forEach { key -> item(key = "deferred:$key") { SegmentedListItem(
                headlineContent = { Text(susfsOperationName(key)) }, supportingContent = { Text(stringResource(R.string.susfs_deferred)) },
                leadingContent = { Icon(Icons.Rounded.Schedule, null) }) } }
            report.pendingReboot.forEach { key -> item(key = "pending:$key") { SegmentedListItem(
                headlineContent = { Text(susfsOperationName(key)) }, supportingContent = { Text(stringResource(R.string.susfs_pending)) },
                leadingContent = { Icon(Icons.Rounded.Schedule, null) }) } }
        }
    }
    SegmentedColumn(title = stringResource(R.string.susfs_config)) {
        item { SegmentedListItem(enabled = status.canImport && !state.busy, onClick = import,
            headlineContent = { Text(stringResource(R.string.susfs_import)) }, leadingContent = { Icon(Icons.Rounded.FileOpen, null) }) }
        item { SegmentedListItem(enabled = config != null && !state.busy, onClick = export,
            headlineContent = { Text(stringResource(R.string.susfs_export)) }, leadingContent = { Icon(Icons.Rounded.SaveAlt, null) }) }
        if (status.schema1BackupAvailable) item {
            SegmentedListItem(
                enabled = status.canImport && !state.busy,
                onClick = restoreSchema1,
                headlineContent = { Text(stringResource(R.string.susfs_restore_schema1)) },
                supportingContent = { Text(stringResource(R.string.susfs_restore_schema1_hint)) },
                leadingContent = { Icon(Icons.Rounded.Restore, null) },
            )
        }
    }
    val staticFieldNames = susfsStaticFieldNames(status.capabilities.staticKstatFields)
    SegmentedColumn(title = stringResource(R.string.susfs_capabilities)) {
        val capabilities = listOf(
            Triple(R.string.susfs_hide_mounts, status.capabilities.hideMounts, null),
            Triple(R.string.susfs_logging, status.capabilities.logging, null),
            Triple(R.string.susfs_avc, status.capabilities.avcSpoof, null),
            Triple(R.string.susfs_uname, status.capabilities.uname, null),
            Triple(R.string.susfs_cmdline, status.capabilities.cmdline, null),
            Triple(R.string.susfs_path_rules, status.capabilities.path, null),
            Triple(R.string.susfs_map_rules, status.capabilities.map, null),
            Triple(R.string.susfs_kstat_rules, status.capabilities.dynamicKstat, null),
            Triple(R.string.susfs_clone, status.capabilities.fullClone, null),
            Triple(R.string.susfs_static_title, status.capabilities.staticKstat,
                staticFieldNames.ifEmpty { null }),
            Triple(R.string.susfs_redirect_rules, status.capabilities.redirect,
                status.capabilities.redirectUidSchemes.joinToString().ifEmpty { null }),
            Triple(R.string.susfs_modules, status.capabilities.hiddenModules, null),
            Triple(R.string.susfs_prefixes, status.capabilities.mountPrefixes, null),
        )
        capabilities.forEachIndexed { index, (title, supported, detail) -> item(key = "capability:$index") {
            SegmentedListItem(
                headlineContent = { Text(stringResource(title)) },
                supportingContent = {
                    Text(listOfNotNull(
                        stringResource(if (supported) R.string.susfs_available else R.string.susfs_unavailable),
                        detail,
                    ).joinToString(" · "))
                },
                leadingContent = { Icon(
                    if (supported) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                    null,
                ) },
            )
        } }
    }
}

@Composable
internal fun susfsStaticFieldNames(fields: List<String>): String {
    val names = mutableListOf<String>()
    for (field in fields) {
        names += when (field) {
            "size" -> stringResource(R.string.susfs_field_size)
            "ctime_sec" -> stringResource(R.string.susfs_field_ctime)
            else -> field
        }
    }
    return names.joinToString()
}

@Composable
private fun susfsErrorHint(message: String): String = stringResource(when (susfsErrorKind(message)) {
    SusfsErrorKind.Capability -> R.string.susfs_error_capability
    SusfsErrorKind.StaticKstat -> R.string.susfs_error_static
    SusfsErrorKind.PendingReboot -> R.string.susfs_error_reboot
    SusfsErrorKind.Uts -> R.string.susfs_error_uts
    SusfsErrorKind.Ownership -> R.string.susfs_error_ownership
    SusfsErrorKind.Busy -> R.string.susfs_error_busy
    SusfsErrorKind.Timeout -> R.string.susfs_error_timeout
    SusfsErrorKind.Validation -> R.string.susfs_error_validation
    SusfsErrorKind.Root -> R.string.susfs_error_root
    SusfsErrorKind.Unknown -> R.string.susfs_error_hint
})

private fun susfsManagementHint(status: SusfsStatus): Int = when {
    status.management == "partial" -> R.string.susfs_partial_hint
    status.management == "skipped" -> R.string.susfs_skipped_hint
    status.management == "idle" -> R.string.susfs_idle_hint
    status.management == "deferred" -> R.string.susfs_deferred_hint
    status.management == "saved" && !status.automatic -> R.string.susfs_saved_reboot
    else -> R.string.susfs_ack_hint
}

@Composable
private fun susfsOperationName(key: String): String {
    val prefix = key.substringBefore(':')
    val name = stringResource(when (prefix) {
        "path" -> R.string.susfs_path_rules
        "map" -> R.string.susfs_map_rules
        "kstat" -> R.string.susfs_kstat_rules
        "static_kstat" -> R.string.susfs_static_title
        "redirect" -> R.string.susfs_redirect_rules
        "module" -> R.string.susfs_modules
        "prefix" -> R.string.susfs_prefixes
        "uname" -> R.string.susfs_uname
        "cmdline" -> R.string.susfs_cmdline
        "hide_mounts" -> R.string.susfs_hide_mounts
        "logging" -> R.string.susfs_logging
        "avc_spoof" -> R.string.susfs_avc
        else -> R.string.susfs_rules
    })
    return if (':' in key) "$name · ${key.substringAfter(':')}" else name
}
