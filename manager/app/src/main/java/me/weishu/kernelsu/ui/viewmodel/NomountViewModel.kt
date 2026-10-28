package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkMountStatus
import me.weishu.kernelsu.data.model.NomountStatus
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.util.execKsudResult

/** One row of the exclusion picker. */
@Immutable
data class ExclusionApp(
    val uid: Int,
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val excluded: Boolean,
    /** Source package, so the picker can show the app icon without another lookup. */
    val packageInfo: android.content.pm.PackageInfo?,
)

/**
 * NoMount detail screen state.
 *
 * A failed read is kept as a flag instead of a fallback status: the screen must
 * never show a plausible-looking driver state it did not actually receive.
 */
@Immutable
data class NomountUiState(
    val isLoading: Boolean = true,
    val isWriting: Boolean = false,
    val status: NomountStatus? = null,
    val folkMountStatus: FolkMountStatus? = null,
    val readError: Boolean = false,
    val actionError: Boolean = false,
    val apps: List<ExclusionApp> = emptyList(),
    val appsLoading: Boolean = true,
    val showSystemApps: Boolean = false,
)

class NomountViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NomountUiState())
    val uiState: StateFlow<NomountUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isWriting) return
        _uiState.update { it.copy(isLoading = true, actionError = false) }
        viewModelScope.launch {
            refreshStatuses()
        }
    }

    fun enableNomount() {
        if (_uiState.value.isWriting) return
        _uiState.update { it.copy(isWriting = true, actionError = false) }
        viewModelScope.launch {
            val result = repo.setFolkMountMode(FolkMountMode.NOMOUNT)
            refreshStatuses(actionError = result.isFailure)
        }
    }

    private suspend fun refreshStatuses(actionError: Boolean? = null) {
        val statusResult = repo.getNomountStatus()
        val mountResult = repo.getFolkMountStatus()
        _uiState.update {
            it.copy(
                isLoading = false,
                isWriting = false,
                status = statusResult.getOrNull() ?: it.status,
                folkMountStatus = mountResult.getOrNull() ?: it.folkMountStatus,
                readError = statusResult.isFailure,
                actionError = actionError ?: it.actionError,
            )
        }
    }

    fun injectRules() = runAction { repo.injectNomountRules() }

    fun clearRules() = runAction { repo.clearNomountRules() }

    /** Drop every rule and every exclusion. */
    fun clearEverything() = runAction { repo.clearNomountEverything() }

    fun setIsolated(enabled: Boolean) = runAction { repo.setNomountIsolated(enabled) }

    /** Add a module's rules without waiting for a reboot. */
    fun hotLoad(id: String) = runAction { repo.hotLoadNomountModule(id) }

    /** Drop a module's rules without waiting for a reboot. */
    fun hotUnload(id: String) = runAction { repo.hotUnloadNomountModule(id) }

    /** Keep a module running but stop injecting its files (and re-apply at once). */
    fun setSkip(id: String, skip: Boolean) =
        runAction {
            repo.setModuleSkip(id, skip).also { result ->
                if (result.isSuccess) repo.injectNomountRules()
            }
        }

    fun setShowSystemApps(show: Boolean) {
        _uiState.update { it.copy(showSystemApps = show) }
    }

    /** Load the installed apps so the exclusion picker can offer them. */
    fun loadApps() {
        if (_uiState.value.apps.isNotEmpty() || !_uiState.value.appsLoading) return
        viewModelScope.launch {
            val apps = NomountAppList.load()
            _uiState.update { it.copy(apps = apps, appsLoading = false) }
        }
    }

    /** Persist the exclusions chosen in the picker. */
    fun saveExclusions(uids: List<Int>) = runAction { repo.setNomountUids(uids) }

    /** Drop every exclusion. */
    fun clearExclusions() = runAction { repo.setNomountUids(emptyList()) }

    /**
     * UIDs whose app profile hides modules from them.
     *
     * The kernel answers that per UID and the default profile hides modules from
     * every non-root app, so this usually selects almost everything; the picker
     * shows the count before the user saves.
     */
    suspend fun profileExcludedUids(includeSystem: Boolean): List<Int> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
        _uiState.value.apps
            .filter { app ->
                (includeSystem || !app.isSystem) &&
                    runCatching { me.weishu.kernelsu.Natives.uidShouldUmount(app.uid) }
                        .getOrDefault(false)
            }
            .map { app -> app.uid }
    }

    private fun runAction(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isWriting) return
        _uiState.update { it.copy(isWriting = true, actionError = false) }
        viewModelScope.launch {
            val result = block()
            // Re-read instead of assuming the action produced the state we asked
            // for: the kernel may have refused part of it.
            val refreshed = repo.getNomountStatus()
            _uiState.update {
                it.copy(
                    isWriting = false,
                    actionError = result.isFailure,
                    status = refreshed.getOrNull() ?: it.status,
                    readError = refreshed.isFailure,
                )
            }
        }
    }
}

internal fun shouldShowNomountSetup(
    status: NomountStatus?,
    mount: FolkMountStatus?,
): Boolean = mount != null &&
    status != null &&
    status.deferredTo == null &&
    (mount.configuredMode != FolkMountMode.NOMOUNT || mount.shouldWrite(FolkMountMode.NOMOUNT))

/**
 * Installed apps for the picker, loaded through the manager's own app list.
 *
 * The exclusion list is a list of UIDs, so a shared UID (for example a shared
 * system user id) selects every app that reports it.
 */
private object NomountAppList {
    suspend fun load(): List<ExclusionApp> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val result = execKsudResult("nomount status --json")
        val excluded = runCatching {
            org.json.JSONObject(result.out.firstOrNull { it.isNotBlank() }.orEmpty())
                .optJSONArray("uids")
                ?.let { array -> List(array.length()) { index -> array.optInt(index, 0) } }
                .orEmpty()
        }.getOrDefault(emptyList())
        SuperUserViewModel().let { vm ->
            if (SuperUserViewModel.apps.isEmpty()) vm.fetchAppList()
        }
        SuperUserViewModel.apps.map { app ->
            ExclusionApp(
                uid = app.uid,
                label = app.label,
                packageName = app.packageName,
                isSystem = ((app.packageInfo.applicationInfo?.flags ?: 0)
                    and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                excluded = excluded.contains(app.uid),
                packageInfo = app.packageInfo,
            )
        }.distinctBy { it.uid }
    }
}
