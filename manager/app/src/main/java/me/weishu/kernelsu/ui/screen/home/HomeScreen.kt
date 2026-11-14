package me.weishu.kernelsu.ui.screen.home

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.HomeMetrics
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.magica.MagicaService
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.theme.LocalThemePreview
import me.weishu.kernelsu.ui.util.CapabilityRepository
import me.weishu.kernelsu.ui.viewmodel.HomeViewModel

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
    superuserCount: Int = 0,
    moduleEnabledCount: Int = 0,
    pluginEnabledCount: Int = 0,
    metamodule: HomeMetamodule? = null,
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mainPagerState = LocalMainPagerState.current
    val previewing = LocalThemePreview.current != null
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // The sampler runs only while this page is the one on screen and only for a layout that draws
    // live metrics; every other case leaves it idle. The lifecycle also stops it once the app
    // leaves the foreground.
    val metrics = if (isCurrentPage && HomeLayoutStyle.usesMetrics(LocalHomeLayoutStyle.current)) {
        viewModel.metrics.collectAsStateWithLifecycle().value
    } else {
        HomeMetrics()
    }
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current

    var hasActivated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage, previewing) {
        if (!previewing && isCurrentPage) {
            // Returning to the home page re-reads the shared capability snapshot so the layout,
            // warnings and navigation agree again.
            CapabilityRepository.refresh()
            viewModel.refreshSusfs()
            if (!hasActivated) {
                hasActivated = true
                viewModel.refresh()
            }
        }
    }

    LifecycleResumeEffect(Unit, activity ?: LocalLifecycleOwner.current) {
        if (!previewing && initialResumeHandled.value && latestIsCurrentPage) {
            CapabilityRepository.refresh()
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    val actions = remember(navigator, uriHandler, context, scope, mainPagerState, loadingDialog) {
        HomeActions(
            onInstallClick = { navigator.push(Route.Install) },
            onOpenUrl = uriHandler::openUri,
            onJailbreakClick = {
                loadingDialog.showLoading()
                context.startService(Intent(context, MagicaService::class.java))
                // Manager will be force-stopped and restarted by late-load on success.
                // If that doesn't happen within timeout, jailbreak likely failed.
                scope.launch(Dispatchers.IO) {
                    delay(30_000.milliseconds)
                    withContext(Dispatchers.Main) {
                        loadingDialog.hide()
                        Toast.makeText(context, R.string.jailbreak_timeout, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onOpenSuperUser = { mainPagerState.animateToDestination(BottomBarDestination.SuperUser) },
            onOpenModule = { mainPagerState.animateToDestination(BottomBarDestination.Module) },
            onRetryRoot = { CapabilityRepository.refresh() },
            onSusfs = { navigator.push(Route.Susfs) },
        )
    }

    val state = remember(uiState, metamodule) {
        uiState.copy(mount = uiState.mount.copy(metamodule = metamodule))
    }

    HomePagerMaterial(
        state = state,
        actions = actions,
        bottomInnerPadding = bottomInnerPadding,
        superuserCount = superuserCount,
        moduleEnabledCount = moduleEnabledCount,
        pluginEnabledCount = pluginEnabledCount,
        metrics = metrics,
    )
}
