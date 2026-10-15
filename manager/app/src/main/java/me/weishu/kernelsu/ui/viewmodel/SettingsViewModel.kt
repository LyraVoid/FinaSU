package me.weishu.kernelsu.ui.viewmodel

import android.system.OsConstants
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.SuiStatus
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.data.model.SupportLinksStyle
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.screen.settings.SettingsUiState
import me.weishu.kernelsu.ui.theme.ColorMode

class SettingsViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var refreshGeneration = 0

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isFolkZygiskWriting) return
        val generation = ++refreshGeneration
        _uiState.update { it.copy(isFolkMountLoading = true, isFolkZygiskLoading = true) }
        viewModelScope.launch {
            val checkUpdate = repo.checkUpdate
            val checkModuleUpdate = repo.checkModuleUpdate
            val themeMode = repo.themeMode
            val keyColor = repo.keyColor
            val enablePredictiveBack = repo.enablePredictiveBack
            val enableNavigationBadge = repo.enableNavigationBadge
            val pageScale = repo.pageScale
            val moduleDescriptionMaxLines = repo.moduleDescriptionMaxLines
            val enableWebDebugging = repo.enableWebDebugging
            val homeLayoutStyle = repo.homeLayoutStyle
            val supportLinksStyle = repo.supportLinksStyle
            val themeStoreMode = repo.themeStoreMode
            val colorStyle = repo.colorStyle
            val colorSpec = repo.colorSpec
            val isLkmMode = repo.isLkmMode()

            // Async loading for natives/features
            val suCompatStatus = repo.getSuCompatStatus()
            val suCompatPersistValue = repo.getSuCompatPersistValue()
            val isSuEnabled = repo.isSuEnabled()

            val suCompatMode = when {
                suCompatStatus == "forced" -> if (isSuEnabled) 0 else 2
                suCompatPersistValue == 0L -> 2
                !isSuEnabled -> 1
                else -> 0
            }

            val kernelUmountStatus = repo.getKernelUmountStatus()
            val isKernelUmountEnabled = repo.isKernelUmountEnabled()
            val selinuxHideStatus = repo.getSelinuxHideStatus()
            val isSelinuxHideEnabled = repo.isSelinuxHideEnabled()
            val selinuxMode = repo.getSELinuxMode()
            val sulogStatus = repo.getSulogStatus()
            val avcSpoofStatus = repo.getAvcSpoofStatus()
            val isAvcSpoofEnabled = repo.isAvcSpoofEnabled()
            val isSulogEnabled = if (sulogStatus == "forced") repo.getSulogValue()?.let { it != 0L } == true
                else repo.getSulogPersistValue() == 1L
            val adbRootStatus = repo.getAdbRootStatus()
            val isAdbRootEnabled = if (adbRootStatus == "forced") repo.getAdbRootValue()?.let { it != 0L } == true
                else repo.getAdbRootPersistValue() == 1L
            val isDefaultUmountModules = repo.isDefaultUmountModules()
            val shellSuAdbOnlyStatus = repo.getShellSuAdbOnlyStatus()
            val isShellSuAdbOnlyEnabled = if (shellSuAdbOnlyStatus == "forced") {
                repo.getShellSuAdbOnlyValue()?.let { it != 0L } == true
            } else {
                repo.getShellSuAdbOnlyPersistValue() == 1L
            }
            val autoJailbreak = repo.autoJailbreak
            val useSoftReboot = repo.useSoftReboot
            val allowAnyDynamicManager = repo.allowAnyDynamicManager
            val isDynamicManagerAvailable = Natives.isDynamicManagerEnabled()
            val isLateLoadMode = Natives.isLateLoadMode

            val folkMountResult = repo.getFolkMountStatus()
            val nomountResult = repo.getNomountStatus()
            val folkZygiskResult = repo.getFolkZygiskStatus()
            val suiResult = repo.getSuiStatus()

            if (generation != refreshGeneration) return@launch
            val suiManagement = if (suiResult.getOrNull()?.state == SuiStatus.STATE_ENABLED) {
                refreshSuiManagement()
            } else {
                null
            }
            _uiState.update {
                it.copy(
                    checkUpdate = checkUpdate,
                    checkModuleUpdate = checkModuleUpdate,
                    themeMode = themeMode,
                    keyColor = keyColor,
                    enablePredictiveBack = enablePredictiveBack,
                    enableNavigationBadge = enableNavigationBadge,
                    pageScale = pageScale,
                    moduleDescriptionMaxLines = moduleDescriptionMaxLines,
                    enableWebDebugging = enableWebDebugging,
                    homeLayoutStyle = homeLayoutStyle,
                    supportLinksStyle = supportLinksStyle,
                    themeStoreMode = themeStoreMode,
                    colorStyle = colorStyle,
                    colorSpec = colorSpec,
                    suCompatStatus = suCompatStatus,
                    suCompatMode = suCompatMode,
                    isSuEnabled = isSuEnabled,
                    adbRootStatus = adbRootStatus,
                    isAdbRootEnabled = isAdbRootEnabled,
                    shellSuAdbOnlyStatus = shellSuAdbOnlyStatus,
                    isShellSuAdbOnlyEnabled = isShellSuAdbOnlyEnabled,
                    kernelUmountStatus = kernelUmountStatus,
                    isKernelUmountEnabled = isKernelUmountEnabled,
                    selinuxHideStatus = selinuxHideStatus,
                    isSelinuxHideEnabled = isSelinuxHideEnabled,
                    selinuxMode = selinuxMode,
                    sulogStatus = sulogStatus,
                    avcSpoofStatus = avcSpoofStatus,
                    isAvcSpoofEnabled = isAvcSpoofEnabled,
                    isSulogEnabled = isSulogEnabled,
                    isDefaultUmountModules = isDefaultUmountModules,
                    isLkmMode = isLkmMode,
                    autoJailbreak = autoJailbreak,
                    useSoftReboot = useSoftReboot,
                    allowAnyDynamicManager = allowAnyDynamicManager,
                    isDynamicManagerAvailable = isDynamicManagerAvailable,
                    isLateLoadMode = isLateLoadMode,
                    folkMountStatus = folkMountResult.getOrNull(),
                    folkMountReadError = folkMountResult.isFailure,
                    isFolkMountLoading = false,
                    nomountStatus = nomountResult.getOrNull(),
                    nomountReadError = nomountResult.isFailure,
                    isNomountLoading = false,
                    folkZygiskStatus = folkZygiskResult.getOrNull(),
                    folkZygiskReadError = folkZygiskResult.isFailure,
                    isFolkZygiskLoading = false,
                    suiStatus = suiResult.getOrNull(),
                    suiReadError = suiResult.isFailure,
                    isSuiLoading = false,
                    suiManagementAvailable = suiManagement,
                )
            }
        }
    }

    fun setFolkZygiskEnabled(enabled: Boolean) {
        if (_uiState.value.isFolkZygiskWriting || _uiState.value.isFolkZygiskLoading) return
        ++refreshGeneration
        _uiState.update { it.copy(isFolkZygiskWriting = true) }
        viewModelScope.launch {
            val result = repo.setFolkZygiskEnabled(enabled)
            val status = repo.getFolkZygiskStatus()
            _uiState.update {
                it.copy(isFolkZygiskWriting = false, folkZygiskStatus = status.getOrNull(),
                    folkZygiskReadError = status.isFailure,
                    folkZygiskRebootPending = it.folkZygiskRebootPending || result.isSuccess)
            }
            Toast.makeText(ksuApp, if (result.isSuccess) R.string.folkzygisk_reboot_required
                else R.string.folkzygisk_write_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun setSuiEnabled(enabled: Boolean) {
        if (_uiState.value.isSuiWriting || _uiState.value.isSuiLoading) return
        ++refreshGeneration
        _uiState.update { it.copy(isSuiWriting = true) }
        viewModelScope.launch {
            val result = repo.setSuiEnabled(enabled)
            val status = repo.getSuiStatus()
            _uiState.update {
                it.copy(
                    isSuiWriting = false,
                    suiStatus = status.getOrNull(),
                    suiReadError = status.isFailure,
                )
            }
            val message = when {
                result.isFailure -> R.string.sui_write_failed
                enabled -> R.string.sui_reboot_required
                else -> R.string.sui_disable_done
            }
            Toast.makeText(ksuApp, message, Toast.LENGTH_LONG).show()
        }
    }

    fun probeSuiManagement() {
        viewModelScope.launch {
            val message = repo.suiProbeManagement().getOrElse { it.message ?: "probe failed" }
            Log.i("SuiManagement", message)
            Toast.makeText(ksuApp, message, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun refreshSuiManagement(): Boolean {
        val result = repo.suiProbeManagement()
        Log.i("SuiManagement", result.getOrElse { "unavailable: ${it.message}" })
        return result.isSuccess
    }

    fun repairSui() {
        if (_uiState.value.isSuiWriting) return
        ++refreshGeneration
        _uiState.update { it.copy(isSuiWriting = true) }
        viewModelScope.launch {
            val result = repo.repairSui()
            val status = repo.getSuiStatus()
            _uiState.update {
                it.copy(
                    isSuiWriting = false,
                    suiStatus = status.getOrNull(),
                    suiReadError = status.isFailure,
                )
            }
            Toast.makeText(
                ksuApp,
                if (result.isSuccess) R.string.sui_repair_done else R.string.sui_repair_failed,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun setCheckUpdate(enabled: Boolean) {
        repo.checkUpdate = enabled
        _uiState.update { it.copy(checkUpdate = enabled) }
    }

    fun setCheckModuleUpdate(enabled: Boolean) {
        repo.checkModuleUpdate = enabled
        _uiState.update { it.copy(checkModuleUpdate = enabled) }
    }

    fun setThemeMode(mode: Int) {
        repo.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setColorMode(mode: ColorMode) {
        repo.themeMode = mode.value
        _uiState.update { it.copy(themeMode = mode.value) }
    }

    fun setKeyColor(color: Int) {
        repo.keyColor = color
        repo.useSystemDynamicColor = color == 0
        _uiState.update { it.copy(keyColor = color) }
    }

    fun setColorStyle(style: String) {
        repo.colorStyle = style
        repo.colorGenerationMode = "custom"
        _uiState.update { it.copy(colorStyle = style) }
    }

    fun setColorSpec(spec: String) {
        repo.colorSpec = spec
        repo.colorGenerationMode = "custom"
        _uiState.update { it.copy(colorSpec = spec) }
    }

    fun setEnablePredictiveBack(enabled: Boolean) {
        repo.enablePredictiveBack = enabled
        _uiState.update { it.copy(enablePredictiveBack = enabled) }
    }

    fun setEnableNavigationBadge(enabled: Boolean) {
        repo.enableNavigationBadge = enabled
        _uiState.update { it.copy(enableNavigationBadge = enabled) }
    }

    fun setPageScale(scale: Float) {
        repo.pageScale = scale
        _uiState.update { it.copy(pageScale = scale) }
    }

    fun setModuleDescriptionMaxLines(lines: Int) {
        repo.moduleDescriptionMaxLines = lines
        _uiState.update { it.copy(moduleDescriptionMaxLines = lines) }
    }

    fun setEnableWebDebugging(enabled: Boolean) {
        repo.enableWebDebugging = enabled
        _uiState.update { it.copy(enableWebDebugging = enabled) }
    }

    fun setHomeLayoutStyle(style: String) {
        val normalized = HomeLayoutStyle.fromValue(style)
        repo.homeLayoutStyle = normalized
        _uiState.update { it.copy(homeLayoutStyle = normalized) }
    }

    fun setSupportLinksStyle(style: String) {
        val normalized = SupportLinksStyle.fromValue(style)
        repo.supportLinksStyle = normalized
        _uiState.update { it.copy(supportLinksStyle = normalized) }
    }

    fun setThemeStoreMode(mode: String) {
        repo.themeStoreMode = mode
        _uiState.update { it.copy(themeStoreMode = mode) }
    }

    fun setSuCompatMode(mode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            when (mode) {
                0 -> if (repo.setSuEnabled(true)) {
                    repo.execKsudFeatureSave()
                    repo.setSuCompatModePref(0)
                    _uiState.update { it.copy(suCompatMode = 0, isSuEnabled = true) }
                }

                1 -> if (repo.setSuEnabled(true)) {
                    repo.execKsudFeatureSave()
                    if (repo.setSuEnabled(false)) {
                        // "Disable until reboot" implies it should be enabled on next boot.
                        // We set the preference to 0 (Enabled) to match the persistent state.
                        repo.setSuCompatModePref(0)
                        _uiState.update { it.copy(suCompatMode = 1, isSuEnabled = false) }
                    }
                }

                2 -> if (repo.setSuEnabled(false)) {
                    repo.execKsudFeatureSave()
                    repo.setSuCompatModePref(2)
                    _uiState.update { it.copy(suCompatMode = 2, isSuEnabled = false) }
                }
            }
        }
    }

    fun setKernelUmountEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.setKernelUmountEnabled(enabled)) {
                repo.execKsudFeatureSave()
                _uiState.update { it.copy(isKernelUmountEnabled = enabled) }
            }
        }
    }

    fun setSelinuxHideEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = repo.setSelinuxHideEnabled(enabled)
            repo.execKsudFeatureSave()
            _uiState.update { it.copy(isSelinuxHideEnabled = enabled) }
            when (status) {
                0 -> {}
                -OsConstants.EAGAIN -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ksuApp, R.string.settings_selinux_hide_reboot_required,
                            Toast.LENGTH_LONG).show()
                    }
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ksuApp, ksuApp.getString(R.string.settings_selinux_hide_failed, status),
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun setSELinuxMode(enforcing: Boolean) {
        if (_uiState.value.isSELinuxModeWriting) return
        _uiState.update { it.copy(isSELinuxModeWriting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val success = repo.setSELinuxMode(enforcing)
            val mode = repo.getSELinuxMode()
            _uiState.update { it.copy(selinuxMode = mode, isSELinuxModeWriting = false) }
            if (!success) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ksuApp, R.string.settings_selinux_mode_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun setAutoJailbreak(enabled: Boolean) {
        repo.autoJailbreak = enabled
        _uiState.update { it.copy(autoJailbreak = enabled) }
    }

    fun setUseSoftReboot(enabled: Boolean) {
        repo.useSoftReboot = enabled
        _uiState.update { it.copy(useSoftReboot = enabled) }
    }

    fun setAllowAnyDynamicManager(enabled: Boolean) {
        repo.allowAnyDynamicManager = enabled
        _uiState.update { it.copy(allowAnyDynamicManager = enabled) }
    }

    fun setAvcSpoofEnabled(enabled: Boolean) {
        if (_uiState.value.isAvcSpoofWriting) return
        _uiState.update { it.copy(isAvcSpoofWriting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (repo.setAvcSpoofEnabled(enabled)) {
                    val saved = repo.execKsudFeatureSave()
                    _uiState.update { it.copy(isAvcSpoofEnabled = repo.isAvcSpoofEnabled()) }
                    if (!saved) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ksuApp, R.string.settings_avc_spoof_save_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ksuApp, R.string.settings_avc_spoof_failed, Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                _uiState.update { it.copy(isAvcSpoofWriting = false) }
            }
        }
    }

    fun setSulogEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.setSulogEnabled(enabled)) {
                repo.execKsudFeatureSave()
                _uiState.update { it.copy(isSulogEnabled = enabled) }
            }
        }
    }

    fun setFolkMountMode(mode: FolkMountMode) {
        val state = _uiState.value
        // Ignore taps while unavailable, while loading, or while a write is in flight.
        val status = state.folkMountStatus
            ?: return
        if (state.folkMountReadError || state.isFolkMountLoading || state.isFolkMountWriting) return
        // No-op when the selection did not change.
        if (status.configuredMode == mode) return

        _uiState.update { it.copy(isFolkMountWriting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.setFolkMountMode(mode).isSuccess) {
                // Re-read on success; on a failed re-read keep the old status.
                val refreshed = repo.getFolkMountStatus()
                _uiState.update {
                    it.copy(
                        folkMountStatus = refreshed.getOrNull() ?: it.folkMountStatus,
                        folkMountReadError = refreshed.isFailure,
                        isFolkMountWriting = false,
                    )
                }
            } else {
                // Keep the previous selection and surface the failure.
                _uiState.update { it.copy(isFolkMountWriting = false) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        ksuApp,
                        R.string.settings_folk_mount_write_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun setAdbRootEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.setAdbRootEnabled(enabled)) {
                repo.execKsudFeatureSave()
                _uiState.update { it.copy(isAdbRootEnabled = enabled) }
            }
        }
    }

    fun setShellSuAdbOnlyEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.setShellSuAdbOnlyEnabled(enabled)) {
                repo.execKsudFeatureSave()
                _uiState.update { it.copy(isShellSuAdbOnlyEnabled = enabled) }
            }
        }
    }

    fun setDefaultUmountModules(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.setDefaultUmountModules(enabled)) {
                _uiState.update { it.copy(isDefaultUmountModules = enabled) }
            }
        }
    }
}
