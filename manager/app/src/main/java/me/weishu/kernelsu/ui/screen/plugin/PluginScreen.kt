package me.weishu.kernelsu.ui.screen.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.PluginViewModel

@Composable
fun PluginPager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val viewModel = viewModel<PluginViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) viewModel.ensureLoaded()
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh(showIndicator = false)
        }
        initialResumeHandled.value = true
        onPauseOrDispose { viewModel.markNeedRefresh() }
    }

    val actions = remember(viewModel, scope, context, navigator) {
        PluginActions(
            onRefresh = { viewModel.refresh() },
            onToggleEnabled = viewModel::setEnabled,
            onOpenLog = viewModel::loadLog,
            onDismissLog = viewModel::dismissLog,
            onInstallZip = { uri ->
                scope.launch {
                    val path = withContext(Dispatchers.IO) { stagePluginPackage(context, uri) }
                    if (path == null) {
                        viewModel.reportError("cannot read the selected package")
                    } else {
                        viewModel.install(path)
                    }
                }
            },
            onOpenStore = {
                viewModel.markNeedRefresh()
                navigator.push(Route.PluginStore)
            },
            onRequestUninstall = viewModel::requestUninstall,
            onConfirmUninstall = viewModel::confirmUninstall,
            onDismissUninstall = viewModel::dismissUninstall,
            onOpenConfig = viewModel::openConfig,
            onChangeConfigValue = viewModel::updateConfigValue,
            onResetConfigValue = viewModel::resetConfigValue,
            onSaveConfig = viewModel::saveConfig,
            onDismissConfig = viewModel::dismissConfig,
            onRun = viewModel::runCallback,
            onNoticeShown = viewModel::clearNotice,
            onErrorShown = viewModel::clearError,
        )
    }

    PluginPagerMaterial(
        uiState = uiState,
        actions = actions,
        bottomInnerPadding = bottomInnerPadding,
    )
}
