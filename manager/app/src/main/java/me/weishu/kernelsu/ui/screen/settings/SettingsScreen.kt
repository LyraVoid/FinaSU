package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.KernelSUApplication
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.ui.component.dialog.ConfirmResult
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.SettingsViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            viewModel.refresh()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    SettingPagerMaterial(uiState, rememberSettingsActions(navigator, viewModel), bottomInnerPadding)
}

/**
 * Shared builder for [SettingsScreenActions], used by both the settings hub and the per-category
 * screens so every entry point drives the same ViewModel setters and navigation targets.
 */
@Composable
fun rememberSettingsActions(
    navigator: Navigator,
    viewModel: SettingsViewModel,
): SettingsScreenActions {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    // Switching to NoMount changes who can see the injected files, so confirm it
    // once with the difference spelled out instead of changing it silently.
    val onSetFolkMountMode: (FolkMountMode) -> Unit = { mode ->
        if (mode == FolkMountMode.NOMOUNT) {
            scope.launch {
                val result = confirm.awaitConfirm(
                    title = resources.getString(R.string.settings_folk_mount_mode_nomount),
                    content = resources.getString(R.string.settings_folk_mount_nomount_confirm),
                    confirm = resources.getString(R.string.settings_folk_mount_nomount_confirm_ok),
                )
                if (result is ConfirmResult.Confirmed) viewModel.setFolkMountMode(mode)
            }
        } else {
            viewModel.setFolkMountMode(mode)
        }
    }
    return SettingsScreenActions(
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onSetCheckModuleUpdate = viewModel::setCheckModuleUpdate,
        onOpenTheme = { navigator.push(Route.ColorPalette) },
        onOpenLanguage = { navigator.push(Route.Language) },
        onOpenWallpaper = { navigator.push(Route.Wallpaper) },
        onOpenGallery = { navigator.push(Route.WallpaperGallery) },
        onOpenFont = { navigator.push(Route.Font) },
        onOpenThemeStore = { navigator.push(Route.ThemeStore) },
        onOpenProfileTemplate = { navigator.push(Route.AppProfileTemplate) },
        onSetSuCompatMode = viewModel::setSuCompatMode,
        onSetKernelUmountEnabled = viewModel::setKernelUmountEnabled,
        onSetSelinuxHideEnabled = viewModel::setSelinuxHideEnabled,
        onSetSELinuxMode = viewModel::setSELinuxMode,
        onSetSulogEnabled = viewModel::setSulogEnabled,
        onSetAvcSpoofEnabled = viewModel::setAvcSpoofEnabled,
        onSetFolkMountMode = onSetFolkMountMode,
        onOpenNomount = { navigator.push(Route.Nomount) },
        onSetFolkZygiskEnabled = viewModel::setFolkZygiskEnabled,
        onSetSuiEnabled = viewModel::setSuiEnabled,
        onRepairSui = viewModel::repairSui,
        onManageSuiPermissions = { navigator.push(Route.SuiPermissions) },
        onSetAdbRootEnabled = viewModel::setAdbRootEnabled,
        onSetShellSuAdbOnlyEnabled = viewModel::setShellSuAdbOnlyEnabled,
        onSetDefaultUmountModules = viewModel::setDefaultUmountModules,
        onSetEnableWebDebugging = viewModel::setEnableWebDebugging,
        onSetHomeLayoutStyle = viewModel::setHomeLayoutStyle,
        onSetSupportLinksStyle = viewModel::setSupportLinksStyle,
        onSetThemeStoreMode = viewModel::setThemeStoreMode,
        onSetLayoutVariant = viewModel::setLayoutVariant,
        onSetEnableNavigationBadge = viewModel::setEnableNavigationBadge,
        onSetEnablePredictiveBack = { enabled ->
            viewModel.setEnablePredictiveBack(enabled)
            KernelSUApplication.setEnableOnBackInvokedCallback(context.applicationInfo, enabled)
            activity?.recreate()
        },
        onSetPageScale = viewModel::setPageScale,
        onSetModuleDescriptionMaxLines = viewModel::setModuleDescriptionMaxLines,
        onSetAutoJailbreak = viewModel::setAutoJailbreak,
        onSetUseSoftReboot = viewModel::setUseSoftReboot,
        onSetAllowAnyDynamicManager = viewModel::setAllowAnyDynamicManager,
        onOpenCategory = { key -> navigator.push(Route.SettingsCategory(key)) },
        onOpenAbout = { navigator.push(Route.About) },
    )
}
