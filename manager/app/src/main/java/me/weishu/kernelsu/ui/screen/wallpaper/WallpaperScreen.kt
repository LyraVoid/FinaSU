package me.weishu.kernelsu.ui.screen.wallpaper

import me.weishu.kernelsu.ui.component.rememberThemeImportRequest
import me.weishu.kernelsu.ui.component.ThemeMetadataEditor
import me.weishu.kernelsu.wallpaper.ThemeMetadata
import me.weishu.kernelsu.wallpaper.ThemeExportService

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.media.VisualMediaConfig
import me.weishu.kernelsu.ui.component.ImageCropPickerHost
import me.weishu.kernelsu.ui.component.SurfaceImagePickerHost
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.FolkWallpaperSurface
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SliderTrackConfig
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SliderCommitEffect
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.material.snapToPercent
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.settings.ClearMediaRow
import me.weishu.kernelsu.ui.screen.settings.MediaFileRow
import me.weishu.kernelsu.ui.screen.settings.MediaSlider
import me.weishu.kernelsu.wallpaper.FolkThemeIO
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceField
import me.weishu.kernelsu.wallpaper.surface.SurfaceFlag
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import kotlin.math.roundToInt

/** Sentinel for "no per-page background is being picked". */
private const val NO_PAGE = -1

@Immutable
data class WallpaperUiState(
    val enabled: Boolean,
    val hasImage: Boolean,
    val uri: String?,
    val opacity: Float,
    val blur: Float,
    val dim: Float,
    val dualDimEnabled: Boolean,
    val dayDim: Float,
    val nightDim: Float,
    val useWallpaperColor: Boolean,
    val multiBackgroundEnabled: Boolean,
    val homeBackgroundSelected: Boolean,
    val pluginBackgroundSelected: Boolean,
    val superuserBackgroundSelected: Boolean,
    val moduleBackgroundSelected: Boolean,
    val settingsBackgroundSelected: Boolean,
    val statsTopLayout: String,
    val videoEnabled: Boolean,
    val videoFilename: String?,
    val videoVolume: Float,
    val isSaving: Boolean,
) {
    /** The config of the surface [id], or null when no surface uses it. */
    fun surfaceFor(id: SurfaceId): SurfaceConfig? =
        if (SurfaceRegistry.descriptor(id) != null) SurfaceStore.config(id) else null
}

@Immutable
data class WallpaperScreenActions(
    val onBack: () -> Unit,
    val onToggleEnabled: (Boolean) -> Unit,
    val onPickImage: () -> Unit,
    val onClear: () -> Unit,
    val onSetOpacity: (Float) -> Unit,
    val onSetBlur: (Float) -> Unit,
    val onSetDim: (Float) -> Unit,
    val onToggleDualDim: (Boolean) -> Unit,
    val onSetDayDim: (Float) -> Unit,
    val onSetNightDim: (Float) -> Unit,
    val onToggleUseWallpaperColor: (Boolean) -> Unit,
    val onToggleMultiBackground: (Boolean) -> Unit,
    val onPickHomeBackground: () -> Unit,
    val onPickPluginBackground: () -> Unit,
    val onPickSuperuserBackground: () -> Unit,
    val onPickModuleBackground: () -> Unit,
    val onPickSettingsBackground: () -> Unit,
    val onToggleVideo: (Boolean) -> Unit,
    val onPickVideo: () -> Unit,
    val onClearVideo: () -> Unit,
    val onSetVideoVolume: (Float) -> Unit,
    val onSetSurfaceToggle: (SurfaceId, SurfaceField, Boolean) -> Unit,
    val onSetSurfaceFlag: (SurfaceId, SurfaceFlag, Boolean) -> Unit,
    val onSetSurfaceScalar: (SurfaceId, SurfaceField, Float) -> Unit,
    /** Persists the wallpaper (and its surfaces) after a live drag; the setters above only touch memory. */
    val onCommitWallpaper: () -> Unit,
    /** The video volume lives in [VisualMediaConfig], not in the wallpaper store. */
    val onCommitVideoVolume: () -> Unit,
    val onPickSurfaceImage: (SurfaceId) -> Unit,
    val onClearSurfaceImage: (SurfaceId) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onResetTheme: () -> Unit,
)

@Composable
fun WallpaperScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var isSaving by remember { mutableStateOf(false) }
    var pendingSurface by remember { mutableStateOf<SurfaceId?>(null) }
    // Page index and crop tokens survive a config change so an in-flight pick is not dropped.
    var pendingPage by rememberSaveable { mutableStateOf(NO_PAGE) }
    var mainPickToken by rememberSaveable { mutableStateOf(0) }
    var pagePickToken by rememberSaveable { mutableStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val persist: (Uri) -> Unit = { picked ->
        scope.launch {
            isSaving = true
            val ok = WallpaperManager.save(context, picked)
            isSaving = false
            snackbarHost.showSnackbar(
                resources.getString(if (ok) R.string.wallpaper_saved else R.string.wallpaper_save_failed)
            )
        }
    }

    // The video is background media too, so it shares the wallpaper page's save/feedback flow.
    val persistVideo: (Uri) -> Unit = { picked ->
        scope.launch {
            isSaving = true
            val ok = VisualMediaConfig.select(context, picked, true)
            isSaving = false
            if (!ok) {
                Toast.makeText(context, R.string.media_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val persistSurface: (SurfaceId, Uri) -> Unit = { id, picked ->
        scope.launch {
            isSaving = true
            val ok = WallpaperManager.saveSurfaceImage(context, id, picked)
            isSaving = false
            val savedRes = if (id == SurfaceRegistry.GRID_WORK_CARD) {
                R.string.wallpaper_work_card_saved
            } else {
                R.string.wallpaper_saved
            }
            val failedRes = if (id == SurfaceRegistry.GRID_WORK_CARD) {
                R.string.wallpaper_work_card_save_failed
            } else {
                R.string.wallpaper_save_failed
            }
            snackbarHost.showSnackbar(resources.getString(if (ok) savedRes else failedRes))
        }
    }

    val persistPage: (Int, Uri) -> Unit = { page, picked ->
        scope.launch {
            isSaving = true
            val ok = WallpaperManager.savePageBackground(context, page, picked)
            isSaving = false
            snackbarHost.showSnackbar(
                resources.getString(if (ok) R.string.wallpaper_saved else R.string.wallpaper_save_failed)
            )
        }
    }

    val resetTheme: () -> Unit = {
        scope.launch {
            val ok = FolkThemeIO.resetTheme(context)
            // Android 12 and older only pick the restored locale up on recreation, matching import.
            if (ok && Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
            snackbarHost.showSnackbar(
                resources.getString(
                    if (ok) R.string.wallpaper_theme_reset else R.string.wallpaper_theme_reset_failed
                )
            )
        }
    }

    // Wallpapers fill the app window with ContentScale.Crop, so the crop frame is locked to the
    // window aspect: whatever the user frames is exactly what the wallpaper shows.
    val configuration = LocalConfiguration.current
    val windowAspect = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        if (configuration.screenWidthDp > 0 && configuration.screenHeightDp > 0) {
            configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
        } else {
            1f
        }
    }

    ImageCropPickerHost(
        trigger = mainPickToken.takeIf { it > 0 },
        aspect = windowAspect,
        // A wallpaper is a full-screen picture: never re-encode it lossily.
        lossless = true,
        onDismissRequest = { mainPickToken = 0 },
        onPicked = { cropped ->
            mainPickToken = 0
            persist(cropped)
        },
    )

    ImageCropPickerHost(
        trigger = pagePickToken.takeIf { it > 0 },
        aspect = windowAspect,
        lossless = true,
        onDismissRequest = {
            pagePickToken = 0
            pendingPage = NO_PAGE
        },
        onPicked = { cropped ->
            val page = pendingPage
            pagePickToken = 0
            pendingPage = NO_PAGE
            if (page != NO_PAGE) persistPage(page, cropped)
        },
    )

    val requestImport = rememberThemeImportRequest { result ->
            scope.launch {
                snackbarHost.showSnackbar(
                    resources.getString(
                        if (result.isSuccess) R.string.wallpaper_import_success else R.string.wallpaper_import_failed
                    ) + (result.exceptionOrNull()?.message?.let { ": $it" } ?: "")
                )
            }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(requestImport)
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(persistVideo)
    }
    if (showExportDialog) {
        ThemeMetadataEditor(
            initial = ThemeMetadata.current(context).let { if (it.name.isBlank()) it.copy(name = "FinaSU") else it },
            onDismiss = { showExportDialog = false },
            onExport = { metadata ->
                showExportDialog = false
                scope.launch {
                    val fileName = "${metadata.name.replace(Regex("[^\\p{L}\\p{N}_ -]"), "_")}.fpt"
                    val result = ThemeExportService.exportToDownloads(context, metadata, fileName)
                    snackbarHost.showSnackbar(
                        resources.getString(
                            if (result.isSuccess) R.string.wallpaper_export_success else R.string.wallpaper_export_failed
                        ) + if (result.isSuccess) {
                            ": ${ThemeExportService.DOWNLOAD_DIR}/$fileName"
                        } else {
                            result.exceptionOrNull()?.message?.let { ": $it" } ?: ""
                        }
                    )
                }
            },
        )
    }

    SurfaceImagePickerHost(
        request = pendingSurface,
        onDismissRequest = { pendingSurface = null },
        onPicked = { id, uri ->
            pendingSurface = null
            persistSurface(id, uri)
        },
    )

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.wallpaper_reset_theme)) },
            text = { Text(stringResource(R.string.wallpaper_reset_theme_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    resetTheme()
                }) {
                    Text(stringResource(R.string.wallpaper_reset_theme))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val state = WallpaperUiState(
        enabled = WallpaperConfig.enabled,
        hasImage = !WallpaperConfig.uri.isNullOrEmpty(),
        uri = WallpaperConfig.uri,
        opacity = WallpaperConfig.opacity,
        blur = WallpaperConfig.blur,
        dim = WallpaperConfig.dim,
        dualDimEnabled = WallpaperConfig.dualDimEnabled,
        dayDim = WallpaperConfig.dayDim,
        nightDim = WallpaperConfig.nightDim,
        useWallpaperColor = WallpaperConfig.useWallpaperColor,
        multiBackgroundEnabled = WallpaperConfig.multiBackgroundEnabled,
        homeBackgroundSelected = !WallpaperConfig.homeBackgroundUri.isNullOrEmpty(),
        pluginBackgroundSelected = !WallpaperConfig.pluginBackgroundUri.isNullOrEmpty(),
        superuserBackgroundSelected = !WallpaperConfig.superuserBackgroundUri.isNullOrEmpty(),
        moduleBackgroundSelected = !WallpaperConfig.moduleBackgroundUri.isNullOrEmpty(),
        settingsBackgroundSelected = !WallpaperConfig.settingsBackgroundUri.isNullOrEmpty(),
        // The Stats grid header draws the grid work card, so its settings belong to this page too.
        statsTopLayout = remember { SettingsRepositoryImpl().statsTopLayout },
        videoEnabled = VisualMediaConfig.videoEnabled,
        videoFilename = VisualMediaConfig.videoFilename,
        videoVolume = VisualMediaConfig.videoVolume,
        isSaving = isSaving,
    )

    val actions = WallpaperScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onToggleEnabled = {
            WallpaperConfig.updateEnabled(it)
            WallpaperConfig.save(context, durable = true)
        },
        onPickImage = { mainPickToken += 1 },
        onClear = {
            WallpaperManager.clear(context)
            scope.launch { snackbarHost.showSnackbar(resources.getString(R.string.wallpaper_removed)) }
        },
        onSetOpacity = {
            WallpaperConfig.updateOpacity(snapToPercent(it))
        },
        onSetBlur = {
            WallpaperConfig.updateBlur(it.roundToInt().toFloat())
        },
        onSetDim = {
            WallpaperConfig.updateDim(snapToPercent(it))
        },
        onToggleDualDim = {
            WallpaperConfig.updateDualDimEnabled(it)
            WallpaperConfig.save(context)
        },
        onSetDayDim = {
            WallpaperConfig.updateDayDim(snapToPercent(it))
        },
        onSetNightDim = {
            WallpaperConfig.updateNightDim(snapToPercent(it))
        },
        onToggleUseWallpaperColor = { enabled ->
            WallpaperConfig.updateUseWallpaperColor(enabled)
            WallpaperConfig.save(context)
            if (enabled) {
                scope.launch(Dispatchers.IO) { WallpaperManager.refreshDerivedIfMissing(context) }
            }
        },
        onToggleMultiBackground = { enabled ->
            WallpaperConfig.updateMultiBackgroundEnabled(enabled)
            WallpaperConfig.save(context)
        },
        onPickHomeBackground = {
            pendingPage = WallpaperConfig.PAGE_HOME
            pagePickToken += 1
        },
        onPickPluginBackground = {
            pendingPage = WallpaperConfig.PAGE_PLUGIN
            pagePickToken += 1
        },
        onPickSuperuserBackground = {
            pendingPage = WallpaperConfig.PAGE_SUPERUSER
            pagePickToken += 1
        },
        onPickModuleBackground = {
            pendingPage = WallpaperConfig.PAGE_MODULE
            pagePickToken += 1
        },
        onPickSettingsBackground = {
            pendingPage = WallpaperConfig.PAGE_SETTINGS
            pagePickToken += 1
        },
        onToggleVideo = { enabled ->
            VisualMediaConfig.videoEnabled = enabled
            VisualMediaConfig.save(context)
        },
        onPickVideo = { videoLauncher.launch("video/*") },
        onClearVideo = { scope.launch { VisualMediaConfig.clear(context, true) } },
        onSetVideoVolume = {
            VisualMediaConfig.videoVolume = it
        },
        onSetSurfaceToggle = { id, field, value ->
            SurfaceStore.update(id) { it.withToggle(field, value) }
            WallpaperConfig.save(context)
        },
        onSetSurfaceFlag = { id, flag, value ->
            SurfaceStore.update(id) { it.withFlag(flag, value) }
            WallpaperConfig.save(context)
        },
        onSetSurfaceScalar = { id, field, value ->
            SurfaceStore.update(id) { it.withScalar(field, value.coerceIn(0f, 1f)) }
        },
        onCommitWallpaper = { WallpaperConfig.save(context) },
        onCommitVideoVolume = { VisualMediaConfig.save(context) },
        onPickSurfaceImage = { id ->
            pendingSurface = id
        },
        onClearSurfaceImage = { id ->
            val removedRes = if (id == SurfaceRegistry.GRID_WORK_CARD) {
                R.string.wallpaper_work_card_removed
            } else {
                R.string.wallpaper_removed
            }
            scope.launch {
                WallpaperManager.clearSurfaceImage(context, id)
                snackbarHost.showSnackbar(resources.getString(removedRes))
            }
        },
        onExport = { showExportDialog = true },
        onImport = { importLauncher.launch("*/*") },
        onResetTheme = { showResetDialog = true },
    )

    WallpaperScreenMaterial(state, actions, snackbarHost)
}

@Composable
fun WallpaperScreenMaterial(
    state: WallpaperUiState,
    actions: WallpaperScreenActions,
    snackbarHostState: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                title = { Text(stringResource(R.string.wallpaper_title)) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = buildList {
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Wallpaper,
                            title = stringResource(R.string.wallpaper_enable),
                            summary = stringResource(R.string.wallpaper_enable_summary),
                            checked = state.enabled,
                            onCheckedChange = actions.onToggleEnabled,
                        )
                    }
                    // The master switch gates every mode, so the video/image choice and its pickers
                    // stay hidden until the background is on. Video takes the slot over entirely —
                    // it replaces the image, matching the render priority (video > image).
                    if (state.enabled) {
                        add {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Movie,
                                title = stringResource(R.string.media_video),
                                checked = state.videoEnabled,
                                onCheckedChange = actions.onToggleVideo,
                            )
                        }
                        if (state.videoEnabled) {
                            add { MediaFileRow(state.videoFilename) { actions.onPickVideo() } }
                            add {
                                MediaSlider(
                                    title = stringResource(R.string.media_volume),
                                    value = state.videoVolume,
                                    onValueChange = { actions.onSetVideoVolume(it) },
                                    onCommit = actions.onCommitVideoVolume,
                                )
                            }
                            if (state.videoFilename != null) {
                                add { ClearMediaRow { actions.onClearVideo() } }
                            }
                        }
                    }
                    if (state.enabled && !state.videoEnabled) {
                        add {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.GridView,
                                title = stringResource(R.string.wallpaper_multi_background_mode),
                                summary = stringResource(R.string.wallpaper_multi_background_mode_summary),
                                checked = state.multiBackgroundEnabled,
                                onCheckedChange = actions.onToggleMultiBackground,
                            )
                        }
                        if (state.multiBackgroundEnabled) {
                            add {
                                WallpaperPagePickItem(
                                    titleRes = R.string.wallpaper_select_home_background,
                                    selected = state.homeBackgroundSelected,
                                    enabled = !state.isSaving,
                                    onClick = actions.onPickHomeBackground,
                                )
                            }
                            add {
                                WallpaperPagePickItem(
                                    titleRes = R.string.wallpaper_select_plugin_background,
                                    selected = state.pluginBackgroundSelected,
                                    enabled = !state.isSaving,
                                    onClick = actions.onPickPluginBackground,
                                )
                            }
                            add {
                                WallpaperPagePickItem(
                                    titleRes = R.string.wallpaper_select_superuser_background,
                                    selected = state.superuserBackgroundSelected,
                                    enabled = !state.isSaving,
                                    onClick = actions.onPickSuperuserBackground,
                                )
                            }
                            add {
                                WallpaperPagePickItem(
                                    titleRes = R.string.wallpaper_select_module_background,
                                    selected = state.moduleBackgroundSelected,
                                    enabled = !state.isSaving,
                                    onClick = actions.onPickModuleBackground,
                                )
                            }
                            add {
                                WallpaperPagePickItem(
                                    titleRes = R.string.wallpaper_select_settings_background,
                                    selected = state.settingsBackgroundSelected,
                                    enabled = !state.isSaving,
                                    onClick = actions.onPickSettingsBackground,
                                )
                            }
                        } else {
                            add {
                                SegmentedListItem(
                                    onClick = actions.onPickImage,
                                    enabled = !state.isSaving,
                                    headlineContent = {
                                        Text(stringResource(if (state.hasImage) R.string.wallpaper_change else R.string.wallpaper_pick))
                                    },
                                    leadingContent = { Icon(Icons.Filled.Image, null) },
                                    trailingContent = {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                    },
                                )
                            }
                            if (state.hasImage) {
                                add {
                                    SegmentedListItem(
                                        onClick = actions.onClear,
                                        headlineContent = { Text(stringResource(R.string.wallpaper_clear)) },
                                        leadingContent = { Icon(Icons.Filled.Delete, null) },
                                    )
                                }
                            }
                        }
                    }
                },
            )

            // The appearance controls apply to every page, so they must stay reachable in multi mode
            // even though the single wallpaper is unused there. Video keeps the dim control (its
            // layer honors it) but drops opacity/blur, which only apply to a still image.
            val mediaVisible = state.videoEnabled || state.hasImage || state.multiBackgroundEnabled
            if (state.enabled && mediaVisible) {
                FolkWallpaperSurface(
                    role = WallpaperSurfaceRole.Group,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    shape = MaterialTheme.shapes.large,
                    fallbackColor = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.wallpaper_section_appearance),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (!state.videoEnabled) {
                            SliderSetting(
                                icon = Icons.Filled.Opacity,
                                title = stringResource(R.string.wallpaper_opacity),
                                summary = stringResource(R.string.wallpaper_opacity_summary),
                                value = state.opacity,
                                range = 0f..1f,
                                onValueChange = actions.onSetOpacity,
                                onCommit = actions.onCommitWallpaper,
                            )
                            SliderSetting(
                                icon = Icons.Filled.BlurOn,
                                title = stringResource(R.string.wallpaper_blur),
                                summary = stringResource(R.string.wallpaper_blur_summary),
                                value = state.blur,
                                range = 0f..50f,
                                onValueChange = actions.onSetBlur,
                                onCommit = actions.onCommitWallpaper,
                                format = { "${it.roundToInt()} dp" },
                            )
                        }
                        if (state.dualDimEnabled) {
                            SliderSetting(
                                icon = Icons.Filled.Contrast,
                                title = stringResource(R.string.wallpaper_day_dim),
                                value = state.dayDim,
                                range = 0f..1f,
                                onValueChange = actions.onSetDayDim,
                                onCommit = actions.onCommitWallpaper,
                            )
                            SliderSetting(
                                icon = Icons.Filled.Contrast,
                                title = stringResource(R.string.wallpaper_night_dim),
                                value = state.nightDim,
                                range = 0f..1f,
                                onValueChange = actions.onSetNightDim,
                                onCommit = actions.onCommitWallpaper,
                            )
                        } else {
                            SliderSetting(
                                icon = Icons.Filled.Brightness6,
                                title = stringResource(R.string.wallpaper_dim),
                                summary = stringResource(R.string.wallpaper_dim_summary),
                                value = state.dim,
                                range = 0f..1f,
                                onValueChange = actions.onSetDim,
                                onCommit = actions.onCommitWallpaper,
                            )
                        }
                    }
                }

                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    content = buildList {
                        // The accent colour comes from the image, so it means nothing while video
                        // owns the background.
                        if (!state.videoEnabled) {
                            add {
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Palette,
                                    title = stringResource(R.string.wallpaper_use_color),
                                    summary = stringResource(R.string.wallpaper_use_color_summary),
                                    checked = state.useWallpaperColor,
                                    onCheckedChange = actions.onToggleUseWallpaperColor,
                                )
                            }
                        }
                        add {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Contrast,
                                title = stringResource(R.string.wallpaper_dual_dim),
                                summary = stringResource(R.string.wallpaper_dual_dim_summary),
                                checked = state.dualDimEnabled,
                                onCheckedChange = actions.onToggleDualDim,
                            )
                        }
                    },
                )
            }

            SurfaceSettingsHost(state = state, actions = actions)

            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(R.string.wallpaper_section_interop),
                content = buildList {
                    add {
                        SegmentedListItem(
                            onClick = actions.onExport,
                            headlineContent = { Text(stringResource(R.string.wallpaper_export)) },
                            supportingContent = { Text(stringResource(R.string.wallpaper_export_summary)) },
                            leadingContent = { Icon(Icons.Filled.FileDownload, null) },
                        )
                    }
                    add {
                        SegmentedListItem(
                            onClick = actions.onImport,
                            headlineContent = { Text(stringResource(R.string.wallpaper_import)) },
                            supportingContent = { Text(stringResource(R.string.wallpaper_import_summary)) },
                            leadingContent = { Icon(Icons.Filled.FileUpload, null) },
                        )
                    }
                    add {
                        SegmentedListItem(
                            onClick = actions.onResetTheme,
                            headlineContent = { Text(stringResource(R.string.wallpaper_reset_theme)) },
                            supportingContent = { Text(stringResource(R.string.wallpaper_reset_theme_summary)) },
                            leadingContent = { Icon(Icons.Filled.RestartAlt, null) },
                        )
                    }
                },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * A wallpaper setting driven by a slider.
 *
 * The caller owns [value], so a drag updates the setting on every frame and the whole app previews
 * it live; [onCommit] only persists once the gesture has settled. [format] is what the row shows, so
 * the value is snapped to the same grid before it is stored.
 */
@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
    icon: ImageVector? = null,
    summary: String? = null,
    format: (Float) -> String = { "${(it * 100).roundToInt()}%" },
) {
    val steps = SliderTrackConfig.steps
    val sliderState = remember(range, steps) { SliderState(value = value, steps = steps, trackRange = range) }
    if (sliderState.value != value) sliderState.value = value
    SliderCommitEffect(value, onCommit)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = format(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            state = sliderState,
            onValueChange = onValueChange,
            onValueChangeFinished = { onCommit() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A row that picks the background image for one main page. */
@Composable
private fun WallpaperPagePickItem(
    titleRes: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = if (selected) {
            { Text(stringResource(R.string.wallpaper_background_selected)) }
        } else {
            null
        },
        leadingContent = { Icon(Icons.Filled.Image, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}
