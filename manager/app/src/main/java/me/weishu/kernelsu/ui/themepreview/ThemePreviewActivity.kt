package me.weishu.kernelsu.ui.themepreview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.data.security.AuthMethod
import me.weishu.kernelsu.data.security.SecurityTarget
import me.weishu.kernelsu.ui.LocalLayoutVariant
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.bottombar.BottomBar
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import me.weishu.kernelsu.ui.component.bottombar.NavigationBadgeState
import me.weishu.kernelsu.ui.component.bottombar.rememberMainPagerState
import me.weishu.kernelsu.ui.component.security.AppAuthenticator
import me.weishu.kernelsu.ui.component.security.LocalAppAuthenticator
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.navigation3.rememberNavigator
import me.weishu.kernelsu.ui.screen.home.HomePager
import me.weishu.kernelsu.ui.screen.home.LocalHomeLayoutStyle
import me.weishu.kernelsu.ui.screen.home.LocalSupportLinksCustom
import me.weishu.kernelsu.ui.screen.home.LocalSupportLinksStyle
import me.weishu.kernelsu.ui.screen.home.SupportLinksCustomContent
import me.weishu.kernelsu.ui.screen.module.ModulePager
import me.weishu.kernelsu.ui.screen.plugin.PluginPager
import me.weishu.kernelsu.ui.screen.settings.SettingPager
import me.weishu.kernelsu.ui.screen.superuser.SuperUserPager
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import me.weishu.kernelsu.ui.theme.LocalColorMode
import me.weishu.kernelsu.ui.theme.LocalEnableNavigationBadge
import me.weishu.kernelsu.ui.theme.LocalModuleDescriptionMaxLines
import me.weishu.kernelsu.ui.theme.LocalThemePreview
import me.weishu.kernelsu.ui.theme.LocalThemePreviewBack
import me.weishu.kernelsu.ui.theme.ThemePreviewSnapshot
import me.weishu.kernelsu.ui.theme.themePreviewSnapshot
import me.weishu.kernelsu.ui.util.CapabilityState
import me.weishu.kernelsu.ui.util.LocalCapabilityState
import me.weishu.kernelsu.ui.util.RootShellStatus
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.LocalWallpaperPage
import me.weishu.kernelsu.wallpaper.ThemePart
import me.weishu.kernelsu.wallpaper.ThemeSelection
import me.weishu.kernelsu.wallpaper.WallpaperBackgroundLayer
import org.json.JSONObject
import java.io.File

class ThemePreviewActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_DIRECTORY = "theme_directory"

        fun start(context: Context, directory: File, selection: ThemeSelection) {
            context.startActivity(
                Intent(context, ThemePreviewActivity::class.java)
                    .putExtra(EXTRA_DIRECTORY, directory.absolutePath)
                    .putStringArrayListExtra("theme_parts", ArrayList(selection.parts.map { it.name }))
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val directory = intent.getStringExtra(EXTRA_DIRECTORY)
        val parts = intent.getStringArrayListExtra("theme_parts").orEmpty()
            .mapNotNull { name -> ThemePart.entries.firstOrNull { it.name == name } }
            .toSet()
        setContent {
            val loaded = remember(directory, parts) {
                loadSnapshot(directory, ThemeSelection(parts))
            }
            val snapshot = loaded.getOrNull()
            if (snapshot == null) {
                KernelSUTheme {
                    Text(
                        text = loaded.exceptionOrNull()?.message.orEmpty(),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                ThemePreviewContent(snapshot, onBack = ::finish)
            }
        }
    }
}

private fun loadSnapshot(directory: String?, selection: ThemeSelection): Result<ThemePreviewSnapshot> = runCatching {
    val dir = File(requireNotNull(directory) { "Missing theme directory" })
    val json = JSONObject(File(dir, "original.json").readText())
    val files = (dir.listFiles() ?: error("Theme files are missing"))
        .filter { it.isFile && it.name != "original.json" }
        .associateBy { it.name }
    themePreviewSnapshot(json.optString("meta_name"), json, files, selection = selection)
}

@Composable
private fun ThemePreviewContent(snapshot: ThemePreviewSnapshot, onBack: () -> Unit) {
    val repository = remember { SettingsRepositoryImpl() }
    val systemDensity = LocalDensity.current
    val density = remember(systemDensity) {
        Density(systemDensity.density * repository.pageScale, systemDensity.fontScale)
    }
    val destinations = remember { BottomBarDestination.entries }
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val scope = rememberCoroutineScope()
    val mainPagerState = rememberMainPagerState(pagerState, destinations, scope)
    val navigator = rememberNavigator(Route.Main)
    val layout = remember(snapshot.homeLayout) {
        snapshot.homeLayout ?: repository.homeLayoutStyle
    }

    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    CompositionLocalProvider(
        LocalWallpaperPage provides pagerState.currentPage,
        LocalDensity provides density,
        LocalColorMode provides snapshot.appSettings.colorMode.value,
        LocalCapabilityState provides CapabilityState(
            isManager = true,
            uapiCompatible = true,
            rootStatus = RootShellStatus.Ready,
        ),
        LocalLayoutVariant provides repository.layoutVariant,
        LocalEnableNavigationBadge provides repository.enableNavigationBadge,
        LocalModuleDescriptionMaxLines provides repository.moduleDescriptionMaxLines,
        LocalSupportLinksStyle provides repository.supportLinksStyle,
        LocalSupportLinksCustom provides SupportLinksCustomContent(
            title = repository.supportLinksCustomTitle,
            subtitle = repository.supportLinksCustomSubtitle,
            url = repository.supportLinksCustomUrl,
        ),
    ) {
        KernelSUTheme(appSettings = snapshot.appSettings, preview = snapshot) {
            CompositionLocalProvider(
                LocalThemePreview provides snapshot,
                LocalThemePreviewBack provides onBack,
                LocalHomeLayoutStyle provides layout,
                LocalMainPagerState provides mainPagerState,
                LocalNavigator provides navigator,
                LocalAppAuthenticator provides PreviewAuthenticator,
            ) {
                Box(Modifier.fillMaxSize()) {
                    WallpaperBackgroundLayer()
                    Scaffold(
                        bottomBar = { BottomBar(NavigationBadgeState()) },
                        containerColor = if (LocalFolkWallpaperTokens.current != null) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ) { innerPadding ->
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            userScrollEnabled = false,
                        ) { page ->
                            val current = page == pagerState.settledPage
                            val bottomPadding = innerPadding.calculateBottomPadding()
                            Box(Modifier.fillMaxSize()) {
                                when (destinations[page]) {
                                    BottomBarDestination.Home ->
                                        HomePager(navigator, bottomPadding, current)
                                    BottomBarDestination.Plugin ->
                                        PluginPager(bottomPadding, current)
                                    BottomBarDestination.SuperUser ->
                                        SuperUserPager(navigator, bottomPadding, current)
                                    BottomBarDestination.Module ->
                                        ModulePager(bottomPadding, current)
                                    BottomBarDestination.Setting ->
                                        SettingPager(navigator, bottomPadding, current)
                                }
                                PreviewTouchBlocker(onBack)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The preview is a render target, so page content cannot mutate settings or navigate.
 * The leading toolbar area is routed back because the real back control sits below this guard.
 */
@Composable
private fun PreviewTouchBlocker(onBack: () -> Unit) {
    val statusBarTop = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toFloat() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(onBack, statusBarTop) {
                val backWidth = 76.dp.toPx()
                val backBottom = statusBarTop + 76.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val closesPreview = down.position.x <= backWidth && down.position.y <= backBottom
                    down.consume()
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    if (closesPreview && up != null) onBack()
                }
            }
    )
}

private object PreviewAuthenticator : AppAuthenticator {
    override fun isProtected(target: SecurityTarget) = false
    override suspend fun require(target: SecurityTarget, fresh: Boolean) = false
    override suspend fun changeDefault(method: AuthMethod) = false
    override suspend fun setTargetProtected(target: SecurityTarget, protected: Boolean) = false
    override fun invalidate() = Unit
}
