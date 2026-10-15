package me.weishu.kernelsu.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults.flingBehavior
import androidx.compose.foundation.pager.PagerDefaults.pageNestedScrollConnection
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.component.security.AuthenticationHost
import me.weishu.kernelsu.ui.component.security.AppLockGate
import me.weishu.kernelsu.ui.component.security.LocalAppUnlocked
import me.weishu.kernelsu.ui.component.security.LocalAppAuthenticator
import me.weishu.kernelsu.ui.component.security.rememberAuthenticationSession
import me.weishu.kernelsu.ui.component.bottombar.BottomBar
import me.weishu.kernelsu.ui.component.bottombar.FloatingBar
import me.weishu.kernelsu.ui.component.bottombar.FloatingBarConfig
import me.weishu.kernelsu.ui.component.bottombar.LocalFloatingBarReservedHeight
import me.weishu.kernelsu.ui.component.bottombar.LocalFloatingBarVisible
import me.weishu.kernelsu.ui.component.bottombar.LocalIsFloatingNavMode
import me.weishu.kernelsu.ui.component.bottombar.MainPagerState
import me.weishu.kernelsu.ui.component.bottombar.NavMode
import me.weishu.kernelsu.ui.component.bottombar.NavModeConfig
import me.weishu.kernelsu.ui.component.bottombar.NavigationBadgeState
import me.weishu.kernelsu.ui.component.bottombar.SideRail
import me.weishu.kernelsu.ui.component.bottombar.floatingBarReservedHeight
import me.weishu.kernelsu.ui.component.bottombar.isRealTimeBlurAvailable
import me.weishu.kernelsu.ui.component.bottombar.navBarLiquefiable
import me.weishu.kernelsu.ui.component.bottombar.rememberFloatingBarVisibilityState
import me.weishu.kernelsu.ui.component.bottombar.rememberMainPagerState
import me.weishu.kernelsu.ui.component.bottombar.rememberNavBarGlassLiquidState
import me.weishu.kernelsu.ui.component.bottombar.useNavigationRail
import me.weishu.kernelsu.ui.navigation3.EXTRA_INCOMING_URIS
import me.weishu.kernelsu.ui.navigation3.IntentDispatcher
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.PredictiveBackConfig
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.navigation3.rememberPredictiveBackEntryDecorator
import me.weishu.kernelsu.ui.navigation3.rememberPredictiveBackTransitions
import me.weishu.kernelsu.ui.navigation3.rememberNavigator
import me.weishu.kernelsu.ui.screen.about.AboutScreen
import me.weishu.kernelsu.ui.screen.appprofile.AppProfileScreen
import me.weishu.kernelsu.ui.screen.colorpalette.ColorPaletteScreen
import me.weishu.kernelsu.ui.screen.executemoduleaction.ExecuteModuleActionScreen
import me.weishu.kernelsu.ui.screen.flash.FlashScreen
import me.weishu.kernelsu.ui.screen.home.HomePager
import me.weishu.kernelsu.ui.screen.home.LocalHomeLayoutStyle
import me.weishu.kernelsu.ui.screen.home.LocalSupportLinksStyle
import me.weishu.kernelsu.ui.screen.install.InstallScreen
import me.weishu.kernelsu.ui.screen.module.ModulePager
import me.weishu.kernelsu.ui.screen.plugin.PluginPager
import me.weishu.kernelsu.ui.screen.modulerepo.ModuleRepoDetailScreen
import me.weishu.kernelsu.ui.screen.modulerepo.ModuleRepoScreen
import me.weishu.kernelsu.ui.screen.plugin.PluginStoreScreen
import me.weishu.kernelsu.ui.component.settings.ProfileConfig
import me.weishu.kernelsu.ui.screen.settings.CategorySettingsScreen
import me.weishu.kernelsu.ui.screen.settings.LanguagePickerScreen
import me.weishu.kernelsu.ui.screen.settings.SettingPager
import me.weishu.kernelsu.ui.screen.sulog.SulogScreen
import me.weishu.kernelsu.ui.screen.superuser.SuperUserPager
import me.weishu.kernelsu.ui.screen.template.AppProfileTemplateScreen
import me.weishu.kernelsu.ui.screen.templateeditor.TemplateEditorScreen
import me.weishu.kernelsu.ui.screen.font.FontScreen
import me.weishu.kernelsu.ui.screen.themestore.MyThemesScreen
import me.weishu.kernelsu.ui.screen.themestore.ThemeStoreScreen
import me.weishu.kernelsu.ui.screen.wallpaper.WallpaperGalleryScreen
import me.weishu.kernelsu.ui.screen.wallpaper.WallpaperScreen
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import me.weishu.kernelsu.ui.theme.LocalColorMode
import me.weishu.kernelsu.ui.theme.LocalEnableNavigationBadge
import me.weishu.kernelsu.ui.theme.LocalModuleDescriptionMaxLines
import me.weishu.kernelsu.ui.util.CapabilityRepository
import me.weishu.kernelsu.ui.util.LocalCapabilityState
import me.weishu.kernelsu.ui.util.PagerNavigationSpringSpec
import me.weishu.kernelsu.ui.util.RootShell
import me.weishu.kernelsu.ui.util.getSuperuserCount
import me.weishu.kernelsu.ui.util.install
import me.weishu.kernelsu.ui.util.rememberContentReady
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import me.weishu.kernelsu.ui.viewmodel.MainActivityViewModel
import me.weishu.kernelsu.ui.viewmodel.MainPagerConfig
import me.weishu.kernelsu.ui.viewmodel.ModuleViewModel
import me.weishu.kernelsu.ui.viewmodel.PluginViewModel
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.LocalWallpaperPage
import me.weishu.kernelsu.wallpaper.WallpaperBackgroundLayer
import me.weishu.kernelsu.ui.theme.FontConfig
import me.weishu.kernelsu.wallpaper.WallpaperConfig

open class MainActivity : FragmentActivity() {
    override fun dispatchTouchEvent(event: android.view.MotionEvent?): Boolean {
        if (event?.action == android.view.MotionEvent.ACTION_UP) {
            me.weishu.kernelsu.media.MediaFeedback.touch(this)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onStop() {
        me.weishu.kernelsu.media.SoundEffectManager.release()
        super.onStop()
    }

    private val intentChannel = Channel<Intent>(capacity = Channel.BUFFERED)
    private var contentReady = false
    private var splashStartedAt = 0L
    private val splashAnimationDurationMs = 500L

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(me.weishu.kernelsu.wallpaper.ThemeLanguage.wrap(newBase))
    }


    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashStartedAt = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            !contentReady || SystemClock.uptimeMillis() - splashStartedAt < splashAnimationDurationMs
        }

        val isManager = Natives.isManager
        if (isManager && Natives.kernelUAPIVersion == Natives.managerUAPIVersion) install()

        // Warm the shell slots and build the first capability snapshot off the main thread.
        RootShell.initialize()
        CapabilityRepository.refresh()

        @Suppress("DEPRECATION")
        val queuedIntents = savedInstanceState?.getParcelableArrayList<Intent>("authentication_pending_intents")
        if (queuedIntents != null) {
            queuedIntents.forEach { intentChannel.trySend(it) }
        } else if (savedInstanceState == null) {
            intent?.let(::queueIntent)
        }

        WallpaperConfig.load(this)
        FontConfig.load(this)
        ProfileConfig.load(this)
        // Activity recreation must not overwrite a slider edit still waiting for its debounce.
        me.weishu.kernelsu.modulebanner.ModuleBannerConfig.loadOnce()
        me.weishu.kernelsu.modulebanner.ModuleCardConfig.load()
        me.weishu.kernelsu.media.MusicConfig.load(this)
        me.weishu.kernelsu.media.SoundEffectConfig.load(this)
        me.weishu.kernelsu.media.VibrationConfig.load(this)
        me.weishu.kernelsu.media.VisualMediaConfig.load(this)
        me.weishu.kernelsu.media.MusicManager.init(this)
        if (savedInstanceState == null) me.weishu.kernelsu.media.SoundEffectManager.playStartup(this)

        setContent {
            val viewModel = viewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val darkMode = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    me.weishu.kernelsu.data.sui.SuiPermissionHost.register(this@MainActivity)
                }
            }
            me.weishu.kernelsu.ui.screen.sui.SuiPermissionRequestDialog()

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val navigator = rememberNavigator(Route.Main)
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }

            val capability by CapabilityRepository.state.collectAsStateWithLifecycle()
            val authenticationSession = rememberAuthenticationSession(this@MainActivity, ksuApp.securityRepository)
            // Deterministic entry for verification: am start ... --es route sui_permissions
            val requestedRoute = intent.getStringExtra("route")
            val fakeRequest = intent.getStringExtra("fake_request")
            androidx.compose.runtime.LaunchedEffect(requestedRoute) {
                if (requestedRoute == "sui_permissions") {
                    navigator.push(Route.SuiPermissions)
                }
                if (fakeRequest != null) {
                    me.weishu.kernelsu.data.sui.SuiPermissionHost.fake(fakeRequest)
                }
            }

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDensity provides density,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableNavigationBadge provides uiState.enableNavigationBadge,
                LocalModuleDescriptionMaxLines provides uiState.moduleDescriptionMaxLines,
                LocalHomeLayoutStyle provides uiState.homeLayoutStyle,
                LocalSupportLinksStyle provides uiState.supportLinksStyle,
                LocalLayoutVariant provides uiState.layoutVariant,
                LocalWallpaperPage provides selectedMainPage,
                LocalCapabilityState provides capability,
                LocalAppAuthenticator provides authenticationSession.authenticator,
            ) {
                KernelSUTheme(appSettings = appSettings) {
                    AppLockGate(authenticationSession, onCancel = { finish() }) {
                    if (LocalAppUnlocked.current) IntentDispatcher(intentChannel = intentChannel)
                    val flashRoute = navigator.current() as? Route.Flash
                    var flashBackEnabled by remember(flashRoute) { mutableStateOf(false) }
                    // Shared by the display and by the predictive back interceptor, so a gesture
                    // that is handled outside the display behaves exactly like a system back.
                    val navigateBack: () -> Unit = {
                        when (val top = navigator.current()) {
                            is Route.Flash -> if (flashBackEnabled) navigator.pop()
                            is Route.TemplateEditor -> {
                                if (!top.readOnly) {
                                    navigator.setResult("template_edit", true)
                                } else {
                                    navigator.pop()
                                }
                            }

                            else -> navigator.pop()
                        }
                    }
                    val predictiveBackRevision by PredictiveBackConfig.revision.collectAsStateWithLifecycle()
                    val predictiveBackAnimation = remember(predictiveBackRevision) {
                        PredictiveBackConfig.animation
                    }
                    val predictiveBackExitDirection = remember(predictiveBackRevision) {
                        PredictiveBackConfig.exitDirection
                    }
                    val predictiveBackTransitions = rememberPredictiveBackTransitions<NavKey>(
                        animation = predictiveBackAnimation,
                        exitDirection = predictiveBackExitDirection,
                    )
                    val predictiveBackEntryDecorator = rememberPredictiveBackEntryDecorator<NavKey>(
                        interceptPredictiveBack = predictiveBackTransitions.interceptsPredictiveBack,
                        isBackEnabled = { navigator.backStackSize() > 1 },
                        onBack = navigateBack,
                    )
                    // The interceptor is only added for the style that previews nothing, so every
                    // other style keeps the plain entry decoration it had before.
                    val saveableStateHolderDecorator =
                        rememberSaveableStateHolderNavEntryDecorator<NavKey>()
                    val entryDecorators = if (predictiveBackTransitions.interceptsPredictiveBack) {
                        listOf(saveableStateHolderDecorator, predictiveBackEntryDecorator)
                    } else {
                        listOf(saveableStateHolderDecorator)
                    }
                    val mainScreenEntry = @Composable {
                        MainScreen(
                            initialPage = selectedMainPage,
                            onPageChanged = viewModel::setSelectedMainPage,
                        )
                    }

                    val navDisplay = @Composable {
                        NavDisplay(
                            backStack = navigator.backStack,
                            onBack = navigateBack,
                            transitionSpec = predictiveBackTransitions.transitionSpec,
                            popTransitionSpec = predictiveBackTransitions.popTransitionSpec,
                            predictivePopTransitionSpec = predictiveBackTransitions.predictivePopTransitionSpec,
                            entryDecorators = entryDecorators,
                            entryProvider = entryProvider<NavKey> {
                                entry<Route.Main> { mainScreenEntry() }
                                entry<Route.About> { AboutScreen() }
                                entry<Route.Sulog> { SulogScreen() }
                                entry<Route.Susfs> { me.weishu.kernelsu.ui.screen.SusfsScreen() }
                                entry<Route.Nomount> {
                                    me.weishu.kernelsu.ui.screen.nomount.NomountScreen()
                                }
                                entry<Route.SuiPermissions> {
                                    me.weishu.kernelsu.ui.screen.sui.SuiPermissionsScreen()
                                }
                                entry<Route.NomountExclusions> {
                                    me.weishu.kernelsu.ui.screen.nomount.NomountExclusionsScreen()
                                }
                                entry<Route.ColorPalette> { ColorPaletteScreen() }
                                entry<Route.AppProfileTemplate> { AppProfileTemplateScreen() }
                                entry<Route.TemplateEditor> { key -> TemplateEditorScreen(key.template, key.readOnly) }
                                entry<Route.AppProfile> { key -> AppProfileScreen(key.uid) }
                                entry<Route.ModuleRepo> { ModuleRepoScreen() }
                                entry<Route.ModuleRepoDetail> { key -> ModuleRepoDetailScreen(key.module) }
                                entry<Route.PluginStore> { PluginStoreScreen() }
                                entry<Route.Install> { InstallScreen() }
                                entry<Route.Flash> { key ->
                                    FlashScreen(
                                        key.flashIt,
                                        onBackEnabledChange = { enabled ->
                                            if (navigator.current() == key) flashBackEnabled = enabled
                                        },
                                    )
                                }
                                entry<Route.ExecuteModuleAction> { key ->
                                    ExecuteModuleActionScreen(
                                        key.moduleId,
                                        key.fromShortcut
                                    )
                                }
                                entry<Route.Home> { mainScreenEntry() }
                                entry<Route.SuperUser> { mainScreenEntry() }
                                entry<Route.Module> { mainScreenEntry() }
                                entry<Route.Settings> { mainScreenEntry() }
                                entry<Route.Wallpaper> { WallpaperScreen() }
                                entry<Route.Font> { FontScreen() }
                                entry<Route.SettingsCategory> { key -> CategorySettingsScreen(key.key) }
                                entry<Route.WebDavBrowser> { key ->
                                    me.weishu.kernelsu.ui.screen.settings.WebDavBrowserScreen(key.path)
                                }
                                entry<Route.BackupList> {
                                    me.weishu.kernelsu.ui.screen.settings.BackupListScreen()
                                }
                                entry<Route.Language> { LanguagePickerScreen() }
                                entry<Route.ThemeStore> { ThemeStoreScreen() }
                                entry<Route.MyThemes> { MyThemesScreen() }
                                entry<Route.WallpaperGallery> { WallpaperGalleryScreen() }
                                entry<Route.ApiMarketplace> {
                                    me.weishu.kernelsu.ui.screen.bannerapi.ApiMarketplaceScreen()
                                }
                            },
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        WallpaperBackgroundLayer()
                        androidx.compose.material3.Scaffold(
                            containerColor = if (LocalFolkWallpaperTokens.current != null) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        ) { navDisplay() }
                    }
                    }
                    SideEffect { contentReady = true }
                    AuthenticationHost(authenticationSession)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        queueIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Snapshot only unconsumed intents. An unlocked dispatcher has already removed its intents.
        val queued = arrayListOf<Intent>()
        while (true) {
            val pending = intentChannel.tryReceive().getOrNull() ?: break
            queued.add(pending)
        }
        queued.forEach { intentChannel.trySend(it) }
        outState.putParcelableArrayList("authentication_pending_intents", queued)
        super.onSaveInstanceState(outState)
    }

    /** Queue an external intent once, with its file payload flattened for [IntentDispatcher]. */
    private fun queueIntent(intent: Intent) {
        intentChannel.trySend(intent.normalizedForDispatch())
    }
}

/** Some file managers put the payload of a VIEW intent into this legacy list extra. */
private const val EXTRA_LEGACY_URIS = "uris"

/** Internal deep-link scheme; its URI is not a file payload and stays out of the flattened list. */
private const val SCHEME_INTERNAL_DEEP_LINK = "ksu"

/**
 * Collect the external file URIs an inbound intent carries. Each action delivers its payload
 * differently: SEND through `EXTRA_STREAM`, SEND_MULTIPLE through the same extra as a list, and
 * everything else (VIEW from a file manager or the desktop "open with" sheet) through
 * [Intent.getData], with a legacy `"uris"` list as a fallback.
 */
@Suppress("DEPRECATION")
private fun Intent.externalFileUris(): List<Uri> {
    val uris: List<Uri> = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
        )

        Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.toList().orEmpty()
        } else {
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
        }

        else -> listOfNotNull(
            data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(EXTRA_LEGACY_URIS, Uri::class.java)?.firstOrNull()
            } else {
                getParcelableArrayListExtra<Uri>(EXTRA_LEGACY_URIS)?.firstOrNull()
            }
        )
    }
    return uris.filterNotNull().filter { it.scheme != SCHEME_INTERNAL_DEEP_LINK }
}

/** Attach the flattened payload so the dispatcher resolves every entry point through one list. */
private fun Intent.normalizedForDispatch(): Intent = apply {
    putParcelableArrayListExtra(EXTRA_INCOMING_URIS, ArrayList(externalFileUris()))
}

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> { error("LocalMainPagerState not provided") }

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
) {
    val navController = LocalNavigator.current
    val useNavigationRail = useNavigationRail()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MainPagerConfig.PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(
        pagerState = pagerState,
        animatePageChanges = !useNavigationRail,
    )
    val isFullFeatured = useFullFeaturedLayout()
    var userScrollEnabled by remember(isFullFeatured) { mutableStateOf(isFullFeatured) }

    // Re-probe identity and the root shell whenever the app returns to the foreground, so a
    // grant/revocation done while we were backgrounded is reflected without a restart.
    LifecycleResumeEffect(Unit) {
        CapabilityRepository.refresh()
        onPauseOrDispose { }
    }

    val enableNavigationBadge = LocalEnableNavigationBadge.current
    val badgeEnabled = enableNavigationBadge && isFullFeatured
    val moduleViewModel = viewModel<ModuleViewModel>()
    val moduleUiState by moduleViewModel.uiState.collectAsStateWithLifecycle()
    val pluginViewModel = viewModel<PluginViewModel>()

    LaunchedEffect(isFullFeatured) {
        if (isFullFeatured) pluginViewModel.ensureLoaded()
    }

    val superUserViewModel = viewModel<SuperUserViewModel>()
    val grantedUidCount by remember(superUserViewModel) {
        superUserViewModel.uiState
            .map { state -> state.groupedApps.count { it.anyAllowSu } }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(0)

    var startupPreloadStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isFullFeatured) {
        if (!isFullFeatured || startupPreloadStarted) {
            return@LaunchedEffect
        }

        moduleViewModel.initializePreferences()
        val moduleState = moduleViewModel.uiState.value
        if (!moduleState.hasLoaded) {
            if (!moduleState.isRefreshing) moduleViewModel.fetchModuleList()
            moduleViewModel.uiState.first { it.hasLoaded }
        }
        moduleViewModel.syncModuleUpdateInfo(moduleViewModel.uiState.value.modules)

        val superUserState = superUserViewModel.uiState.value
        if (!superUserState.hasLoaded) {
            superUserViewModel.initializePreferences()
            if (superUserState.isRefreshing) {
                superUserViewModel.uiState.first { it.hasLoaded }
            } else {
                superUserViewModel.loadAppList().join()
            }
        }

        startupPreloadStarted = true
    }

    // Loading the app list just for a badge is too expensive; read the kernel allowlist instead.
    var superuserCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(isFullFeatured, grantedUidCount) {
        superuserCount = if (isFullFeatured) withContext(Dispatchers.IO) { getSuperuserCount() } else 0
    }

    val moduleEnabledCount = moduleUiState.modules.count { it.enabled }

    val navigationBadge = if (badgeEnabled) {
        NavigationBadgeState(
            superuserCount = superuserCount,
            moduleEnabledCount = moduleEnabledCount,
            moduleUpdatableCount = moduleUiState.updateInfo.count { it.value.downloadUrl.isNotBlank() },
        )
    } else {
        NavigationBadgeState()
    }
    val settledPage = mainPagerState.pagerState.settledPage
    val livePage = mainPagerState.pagerState.currentPage
    // Report the page as soon as the swipe crosses into it rather than when the fling settles, so
    // anything scoped to the page (the wallpaper layer) follows the gesture instead of arriving a
    // beat late.
    LaunchedEffect(livePage) {
        onPageChanged(livePage)
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    MainScreenBackHandler(mainPagerState, navController)

    CompositionLocalProvider(
        LocalMainPagerState provides mainPagerState
    ) {
        val contentReady = rememberContentReady()
        val floatingBarRevision = FloatingBarConfig.revision.collectAsStateWithLifecycle().value
        val navModeRevision = NavModeConfig.revision.collectAsStateWithLifecycle().value
        val navMode = remember(navModeRevision) { NavModeConfig.mode }
        val floatingBarEnabled = navMode == NavMode.Floating
        val floatingBarStyle = remember(floatingBarRevision) { FloatingBarConfig.style }
        val floatingBarCompact = remember(floatingBarRevision) { FloatingBarConfig.compact }
        val floatingBarGlassEnabled = floatingBarEnabled &&
            remember(floatingBarRevision) { FloatingBarConfig.glass } &&
            isRealTimeBlurAvailable()
        val floatingLiquidState = if (floatingBarGlassEnabled) rememberNavBarGlassLiquidState() else null
        val floatingAutoHide = remember(floatingBarRevision) { FloatingBarConfig.autoHide }
        val floatingSwipeHide = remember(floatingBarRevision) { FloatingBarConfig.swipeHide }
        val floatingBarVisibility = rememberFloatingBarVisibilityState(
            enabled = floatingBarEnabled,
            autoHide = floatingAutoHide,
            swipeHide = floatingSwipeHide,
        )
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(modifier = Modifier.navBarLiquefiable(floatingLiquidState)) {
                HorizontalPager(
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = if (contentReady) 3 else 0,
                    overscrollEffect = null,
                    userScrollEnabled = userScrollEnabled,
                    pageNestedScrollConnection = pageNestedScrollConnection(
                        state = mainPagerState.pagerState,
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    ),
                    flingBehavior = flingBehavior(
                        state = mainPagerState.pagerState,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { page ->
                    val isCurrentPage = page == settledPage
                    when (page) {
                        0 -> if (contentReady || isCurrentPage) HomePager(navController, bottomInnerPadding, isCurrentPage, superuserCount, moduleEnabledCount)
                        1 -> if (contentReady || isCurrentPage) PluginPager(bottomInnerPadding, isCurrentPage)
                        2 -> if (contentReady || isCurrentPage) SuperUserPager(navController, bottomInnerPadding, isCurrentPage)
                        3 -> if (contentReady || isCurrentPage) ModulePager(bottomInnerPadding, isCurrentPage)
                        4 -> if (contentReady || isCurrentPage) SettingPager(navController, bottomInnerPadding, isCurrentPage)
                    }
                }
            }
        }

        val wallpaperActive = LocalFolkWallpaperTokens.current != null
        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            androidx.compose.material3.Scaffold(
                containerColor = if (wallpaperActive) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            ) {
                Row {
                    SideRail(
                        navigationBadge = navigationBadge,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .consumeWindowInsets(startInsets)
                    ) {
                        pagerContent(navBarBottomPadding)
                    }
                }
            }
        } else if (floatingBarEnabled) {
            val floatingBarVisible = when {
                !floatingAutoHide && !floatingSwipeHide -> true
                !floatingAutoHide -> !floatingBarVisibility.isScrollingDown
                !floatingSwipeHide -> floatingBarVisibility.visible
                else -> floatingBarVisibility.visible && !floatingBarVisibility.isScrollingDown
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(floatingBarVisibility.connection)
            ) {
                val floatingBarReserved =
                    floatingBarReservedHeight(floatingBarCompact, floatingBarStyle)
                CompositionLocalProvider(
                    LocalIsFloatingNavMode provides true,
                    LocalFloatingBarVisible provides floatingBarVisible,
                    LocalFloatingBarReservedHeight provides floatingBarReserved,
                ) {
                    pagerContent(
                        floatingBarReserved +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                    AnimatedVisibility(
                        visible = floatingBarVisible,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    ) {
                        FloatingBar(
                            navigationBadge = navigationBadge,
                            liquidState = floatingLiquidState,
                            onUserInteraction = { floatingBarVisibility.onUserInteraction() },
                        )
                    }
                }
            }
        } else {
            val bottomBar = @Composable {
                BottomBar(navigationBadge)
            }

            androidx.compose.material3.Scaffold(
                bottomBar = bottomBar,
                containerColor = if (wallpaperActive) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            ) { innerPadding ->
                pagerContent(innerPadding.calculateBottomPadding())
            }
        }
    }
}


@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main && navController.backStackSize() == 1 && mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        }
    )
}
