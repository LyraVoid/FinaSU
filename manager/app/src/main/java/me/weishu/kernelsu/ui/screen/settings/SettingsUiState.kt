package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkMountStatus
import me.weishu.kernelsu.data.model.FolkZygiskStatus
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.data.model.NomountStatus
import me.weishu.kernelsu.data.model.SupportLinksStyle
import me.weishu.kernelsu.data.model.SuiStatus
import me.weishu.kernelsu.data.themestore.ThemeStoreMode

@Immutable
data class SettingsUiState(
    val checkUpdate: Boolean = true,
    val checkModuleUpdate: Boolean = true,
    val themeMode: Int = 0,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.SPEC_2025.name,
    val enablePredictiveBack: Boolean = false,
    val enableNavigationBadge: Boolean = true,
    val pageScale: Float = 1.0f,
    val moduleDescriptionMaxLines: Int = 4,
    val enableWebDebugging: Boolean = false,
    val homeLayoutStyle: String = HomeLayoutStyle.DEFAULT,
    val supportLinksStyle: String = SupportLinksStyle.DEFAULT,
    val themeStoreMode: String = ThemeStoreMode.UNSET,

    // Su Compat
    val suCompatStatus: String = "",
    val suCompatMode: Int = 0, // 0: enable default, 1: disable until reboot, 2: disable always
    val isSuEnabled: Boolean = false,

    // Kernel Umount
    val kernelUmountStatus: String = "",
    val isKernelUmountEnabled: Boolean = false,

    // SELinux Hide
    val selinuxHideStatus: String = "",
    val isSelinuxHideEnabled: Boolean = false,

    // SELinux Mode
    val selinuxMode: String = "",
    val isSELinuxModeWriting: Boolean = false,

    // SU Log
    val sulogStatus: String = "",
    val avcSpoofStatus: String = "",
    val isAvcSpoofEnabled: Boolean = false,
    val isAvcSpoofWriting: Boolean = false,
    val isSulogEnabled: Boolean = false,

    // Folk Mount
    val folkMountStatus: FolkMountStatus? = null,
    val isFolkMountLoading: Boolean = false,
    val isFolkMountWriting: Boolean = false,
    val folkMountReadError: Boolean = false,
    // NoMount backend
    val nomountStatus: NomountStatus? = null,
    val isNomountLoading: Boolean = true,
    val nomountReadError: Boolean = false,
    val folkZygiskStatus: FolkZygiskStatus? = null,
    val isFolkZygiskLoading: Boolean = true,
    val folkZygiskReadError: Boolean = false,
    val isFolkZygiskWriting: Boolean = false,
    val folkZygiskRebootPending: Boolean = false,
    // Sui extension
    val suiStatus: SuiStatus? = null,
    val isSuiLoading: Boolean = true,
    val suiReadError: Boolean = false,
    val isSuiWriting: Boolean = false,
    val suiManagementAvailable: Boolean? = null,

    // Umount Modules
    val isDefaultUmountModules: Boolean = false,

    // ADB Root
    val adbRootStatus: String = "",
    val isAdbRootEnabled: Boolean = false,
    val shellSuAdbOnlyStatus: String = "",
    val isShellSuAdbOnlyEnabled: Boolean = false,

    val isLkmMode: Boolean = false,
    val isLateLoadMode: Boolean = false,

    // Auto Jailbreak
    val autoJailbreak: Boolean = false,

    // Soft Reboot
    val useSoftReboot: Boolean = false,

    // Dynamic Manager
    val allowAnyDynamicManager: Boolean = false,
    val isDynamicManagerAvailable: Boolean = true
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onSetCheckModuleUpdate: (Boolean) -> Unit,
    val onOpenTheme: () -> Unit,
    val onOpenLanguage: () -> Unit,
    val onOpenWallpaper: () -> Unit,
    val onOpenGallery: () -> Unit,
    val onOpenFont: () -> Unit,
    val onOpenThemeStore: () -> Unit,
    val onOpenProfileTemplate: () -> Unit,
    val onSetSuCompatMode: (Int) -> Unit,
    val onSetKernelUmountEnabled: (Boolean) -> Unit,
    val onSetSelinuxHideEnabled: (Boolean) -> Unit,
    val onSetSELinuxMode: (Boolean) -> Unit,
    val onSetSulogEnabled: (Boolean) -> Unit,
    val onSetAvcSpoofEnabled: (Boolean) -> Unit,
    val onSetFolkMountMode: (FolkMountMode) -> Unit,
    val onOpenNomount: () -> Unit,
    val onSetFolkZygiskEnabled: (Boolean) -> Unit,
    val onSetSuiEnabled: (Boolean) -> Unit,
    val onRepairSui: () -> Unit,
    val onManageSuiPermissions: () -> Unit,
    val onSetAdbRootEnabled: (Boolean) -> Unit,
    val onSetShellSuAdbOnlyEnabled: (Boolean) -> Unit,
    val onSetDefaultUmountModules: (Boolean) -> Unit,
    val onSetEnableWebDebugging: (Boolean) -> Unit,
    val onSetHomeLayoutStyle: (String) -> Unit,
    val onSetSupportLinksStyle: (String) -> Unit,
    val onSetThemeStoreMode: (String) -> Unit,
    val onSetEnableNavigationBadge: (Boolean) -> Unit,
    val onSetEnablePredictiveBack: (Boolean) -> Unit,
    val onSetPageScale: (Float) -> Unit,
    val onSetModuleDescriptionMaxLines: (Int) -> Unit,
    val onSetAutoJailbreak: (Boolean) -> Unit,
    val onSetUseSoftReboot: (Boolean) -> Unit,
    val onSetAllowAnyDynamicManager: (Boolean) -> Unit,
    val onOpenCategory: (String) -> Unit,
    val onOpenAbout: () -> Unit,
)
