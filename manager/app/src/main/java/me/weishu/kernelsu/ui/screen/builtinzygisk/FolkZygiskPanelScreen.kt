package me.weishu.kernelsu.ui.screen.builtinzygisk

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.superuser.io.SuFileInputStream
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkZygiskStatus
import me.weishu.kernelsu.ui.component.dialog.ConfirmResult
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.*
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import org.json.JSONObject

@Composable
private fun Detail(icon: ImageVector, title: String, value: String, click: (() -> Unit)? = null, enabled: Boolean = true) {
    me.weishu.kernelsu.ui.component.material.SegmentedListItem(modifier = Modifier.semantics(mergeDescendants = true) {},
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) }, supportingContent = { Text(value) }, onClick = click, enabled = enabled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolkZygiskPanelScreen() {
    val vm = viewModel<FolkZygiskPanelViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val lines = SuFileInputStream.open("/data/adb/ksu/log/logcat.log").bufferedReader().useLines { rows ->
                    val tag = Regex(" [VDIWEF] (?:zygisk(?:-ptrace|-core|d)(?:32|64)\\s*:|KernelSU\\s*: ksud::zygisk:)")
                    rows.filter(tag::containsMatchIn).take(2000).toList()
                }
                check(lines.isNotEmpty())
                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { w -> lines.forEach { w.appendLine(it) } }
            } }
            Toast.makeText(context, if (result.isSuccess) R.string.log_saved else R.string.fz_log_error, Toast.LENGTH_LONG).show()
        }
    }
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose {} }
    val unknown = stringResource(R.string.selinux_status_unknown)
    val status = state.status
    val panel = state.panel
    ExpressiveScaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.settings_folk_zygisk)) },
            navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
            colors = expressiveTopAppBarColors(), actions = {
                TextButton(onClick = vm::refresh, enabled = !state.busy) { Text(stringResource(R.string.nomount_refresh)) }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SegmentedColumn {
                item {
                    val phase = panel?.phase
                    val hint = when (phase) {
                        Phase.External -> stringResource(phase.hint, status!!.externalProviders.joinToString())
                        Phase.Protected -> stringResource(phase.hint, status!!.startupFailures)
                        null -> stringResource(if (state.readError) R.string.folkzygisk_read_error else R.string.folkzygisk_loading)
                        else -> stringResource(phase.hint)
                    }
                    Detail(Icons.Filled.Code, stringResource(phase?.title ?: R.string.folkzygisk_unverified), listOfNotNull(hint,
                        state.checked?.let { stringResource(R.string.fz_checked, DateFormat.getDateTimeInstance().format(Date(it))) },
                        status?.takeIf { !state.readError && (it.startupBlocked || it.startupFailures > 0 || it.startupFailureCode != null) }?.let {
                            stringResource(R.string.fz_failures, it.startupFailures, it.startupFailureCode ?: unknown)
                        }, status?.startupFailureCode?.takeUnless { state.readError }?.let { stringResource(failureHint(it)) },
                        state.message?.let { stringResource(it) }).joinToString("\n"))
                }
            }
            if (panel != null && status != null) {
                SegmentedColumn {
                    for (abi in panel.abis) item {
                        Detail(Icons.Filled.Memory, stringResource(R.string.fz_abi, abi.bits), listOfNotNull(stringResource(abi.label), abi.reason,
                            stringResource(R.string.fz_pids, abi.daemonPid?.toString() ?: unknown,
                                abi.zygotePid?.toString() ?: unknown)).joinToString("\n"))
                    }
                }
                SegmentedColumn {
                    item { Detail(Icons.Filled.Tag, stringResource(R.string.home_version), status.engineVersion ?: unknown) }
                    item { Detail(Icons.Filled.FolderOpen, stringResource(R.string.fz_asset), when (status.matchesAssets) {
                        true -> stringResource(R.string.fz_match); false -> stringResource(R.string.fz_update); null -> unknown
                    }) }
                    item { Detail(Icons.Filled.RestartAlt, stringResource(if (status.enabled) R.string.folkzygisk_next_enabled else R.string.folkzygisk_next_disabled),
                        if (status.pendingRestart || status.rebootRequired == true) stringResource(R.string.folkzygisk_reboot_required) else unknown) }
                }
                SegmentedColumn(title = stringResource(R.string.module)) {
                    if (panel.modules.isEmpty()) item {
                        Detail(Icons.Filled.Extension, stringResource(R.string.settings_folk_zygisk), stringResource(R.string.fz_no_modules))
                    } else for (module in panel.modules) item {
                        Detail(Icons.Filled.Extension, module.title, stringResource(R.string.fz_module_bits, module.abis))
                    }
                }
                if (panel.actions.isNotEmpty()) SegmentedColumn(title = stringResource(R.string.action)) {
                    for (action in panel.actions) item {
                        val title = stringResource(action.title); val hint = stringResource(action.consequence)
                        Detail(action.icon, title, hint, { scope.launch {
                            if (confirm.awaitConfirm(title = title, content = hint) is ConfirmResult.Confirmed) vm.apply(action)
                        } }, !state.busy)
                    }
                }
            }
            SegmentedColumn(title = stringResource(R.string.uts_view_diagnostics)) {
                item { Detail(Icons.Filled.Save, stringResource(R.string.save_log), stringResource(R.string.fz_log_hint), { export.launch("folkzygisk.log") }) }
                if (panel != null && status != null) item { Detail(Icons.Filled.ContentCopy, stringResource(R.string.fz_copy), stringResource(R.string.fz_copy_hint), {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("JSON", sanitized(status).toString(2)))
                }) }
            }
        }
    }
}

internal fun sanitized(s: FolkZygiskStatus) = JSONObject().apply {
    put("schema_version", 1); put("enabled", s.enabled); put("engine_available", s.engineAvailable)
    put("injection_state", s.injectionState); put("startup_blocked", s.startupBlocked); put("startup_failures", s.startupFailures)
    put("reboot_required", s.rebootRequired ?: JSONObject.NULL)
    put("startup_failure_code", s.startupFailureCode?.takeIf { it in setOf("engine_missing", "engine_update_failed", "runtime_unsafe", "monitor_start_failed", "monitor_exited", "readiness_timeout", "injection_unconfirmed", "runtime_invalid") } ?: JSONObject.NULL)
    put("external_provider_count", s.externalProviders.size)
    put("abis", s.runtime?.let { JSONObject().apply { for ((bits, abi) in it.abis) put(bits, JSONObject().put("supported", abi.supported).put("injected", abi.injected).put("daemon_running", abi.daemonRunning)) } } ?: JSONObject.NULL)
}
