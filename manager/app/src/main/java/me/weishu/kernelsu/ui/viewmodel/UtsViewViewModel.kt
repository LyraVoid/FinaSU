package me.weishu.kernelsu.ui.viewmodel

import android.system.Os
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.boot.KernelFacts
import me.weishu.kernelsu.data.boot.KernelFactsRepository
import me.weishu.kernelsu.data.boot.KernelFactsSource
import me.weishu.kernelsu.data.uts.UtsFields
import me.weishu.kernelsu.data.uts.UtsDataSource
import me.weishu.kernelsu.data.uts.UtsRepository
import me.weishu.kernelsu.data.uts.UtsTarget
import me.weishu.kernelsu.data.uts.UtsTargetApp
import me.weishu.kernelsu.data.uts.UtsViewMode
import me.weishu.kernelsu.data.uts.UtsViewStatus
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.RootShell

@Immutable
data class UtsViewUiState(
    val status: UtsViewStatus = UtsViewStatus(),
    val draft: UtsFields = UtsFields(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val hasLoaded: Boolean = false,
    val message: String? = null,
    val currentUname: UtsFields? = null,
    val apps: List<UtsTargetApp> = emptyList(),
    val appsLoading: Boolean = false,
    val appsLoaded: Boolean = false,
    val kernelFacts: KernelFacts? = null,
    val kernelFactsLoading: Boolean = false,
    val kernelFactsError: String? = null,
    val draftSourceSlot: String? = null,
    val draftSourceIdentity: String? = null,
)

class UtsViewViewModel(
    private val repository: UtsDataSource = UtsRepository(),
    private val kernelFactsSource: KernelFactsSource = KernelFactsRepository(),
    private val readUname: () -> UtsFields = { currentUnameSnapshot() },
    private val shellGeneration: () -> Long = { RootShell.generation.value },
    private val getString: (Int) -> String = { ksuApp.getString(it) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(UtsViewUiState())
    val uiState: StateFlow<UtsViewUiState> = _uiState.asStateFlow()
    private var requestToken = 0L

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.busy) return
        val request = ++requestToken
        val generation = shellGeneration()
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, kernelFactsLoading = true, kernelFactsError = null)
            }
            try {
                try {
                    val status = repository.status()
                    if (request != requestToken || generation != shellGeneration()) return@launch
                    val current = if (status.provider == "susfs") null else readUname()
                    _uiState.update {
                        it.copy(
                            status = status,
                            draft = if (it.hasLoaded) it.draft else status.fields,
                            hasLoaded = true,
                            currentUname = current,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (request == requestToken && generation == shellGeneration()) {
                        _uiState.update {
                            it.copy(
                                message = e.message ?: getString(R.string.uts_view_status_failed),
                                currentUname = null,
                            )
                        }
                    }
                }
                if (request == requestToken && generation == shellGeneration()) {
                    try {
                        val facts = kernelFactsSource.load()
                        if (request == requestToken && generation == shellGeneration()) {
                            _uiState.update {
                                it.copy(
                                    kernelFacts = facts,
                                    kernelFactsLoading = false,
                                    kernelFactsError = null,
                                )
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (request == requestToken && generation == shellGeneration()) {
                            _uiState.update {
                                it.copy(
                                    kernelFactsLoading = false,
                                    kernelFactsError =
                                        e.message ?: getString(R.string.uts_view_operation_failed),
                                )
                            }
                        }
                    }
                }
            } finally {
                if (request == requestToken) {
                    _uiState.update { it.copy(loading = false, kernelFactsLoading = false) }
                }
            }
        }
    }

    fun setField(index: Int, value: String) {
        _uiState.update {
            it.copy(
                draft = it.draft.withField(index, value),
                draftSourceSlot = null,
                draftSourceIdentity = null,
            )
        }
    }

    fun fillCurrentValues() {
        if (_uiState.value.status.provider == "susfs" || _uiState.value.status.active) {
            val message = if (_uiState.value.status.provider == "susfs") {
                R.string.uts_view_draft_hint
            } else {
                R.string.uts_view_fill_unavailable
            }
            _uiState.update { it.copy(message = getString(message)) }
            return
        }
        val uname = _uiState.value.currentUname ?: readUname()
        _uiState.update {
            it.copy(
                draft = it.draft.copy(
                    sysname = uname.sysname,
                    nodename = uname.nodename,
                    release = uname.release,
                    version = uname.version,
                    machine = uname.machine,
                    mask = (it.draft.mask and (1 shl 5)) or 0x1f,
                ),
                currentUname = uname,
                draftSourceSlot = null,
                draftSourceIdentity = null,
            )
        }
    }

    /** Selecting a slot only changes the unsaved template. */
    fun useBootSlot(slotId: String) {
        val slot = _uiState.value.kernelFacts?.slot(slotId) ?: return
        val release = slot.release?.value?.takeIf(String::isNotBlank) ?: return
        val version = slot.version?.value?.takeIf(String::isNotBlank) ?: return
        _uiState.update {
            it.copy(
                draft = it.draft.copy(
                    release = release,
                    version = version,
                    mask = it.draft.mask or (1 shl 2) or (1 shl 3),
                ),
                draftSourceSlot = slot.id,
                draftSourceIdentity = slot.sourceIdentity,
            )
        }
    }

    fun save() {
        operate(replaceDraft = true) {
            repository.save(
                _uiState.value.draft,
                _uiState.value.status.enabled,
                _uiState.value.status.mode,
                _uiState.value.status.targets,
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && _uiState.value.draft.fieldMask() == 0) {
            _uiState.update { it.copy(message = getString(R.string.uts_view_enter_field)) }
            return
        }
        if (enabled &&
            _uiState.value.status.mode == UtsViewMode.Selected &&
            _uiState.value.status.targets.isEmpty()
        ) {
            _uiState.update { it.copy(message = getString(R.string.uts_view_enter_targets)) }
            return
        }
        if (enabled && _uiState.value.draft != _uiState.value.status.fields) {
            return
        }
        operate {
            if (enabled) {
                repository.enable()
            } else {
                repository.disable()
            }
        }
    }

    fun saveAndEnable() {
        if (_uiState.value.draft.fieldMask() == 0) {
            _uiState.update { it.copy(message = getString(R.string.uts_view_enter_field)) }
            return
        }
        if (_uiState.value.status.mode == UtsViewMode.Selected &&
            _uiState.value.status.targets.isEmpty()
        ) {
            _uiState.update { it.copy(message = getString(R.string.uts_view_enter_targets)) }
            return
        }
        operate(replaceDraft = true) {
            repository.save(
                _uiState.value.draft,
                true,
                _uiState.value.status.mode,
                _uiState.value.status.targets,
            )
        }
    }

    fun clear() {
        operate(replaceDraft = true) { repository.clear() }
    }

    fun takeOver() {
        if (_uiState.value.draft.fieldMask() == 0) {
            _uiState.update { it.copy(message = getString(R.string.uts_view_enter_field)) }
            return
        }
        if (_uiState.value.status.mode == UtsViewMode.Selected &&
            _uiState.value.status.targets.isEmpty()
        ) {
            _uiState.update { it.copy(message = getString(R.string.uts_view_enter_targets)) }
            return
        }
        operate(replaceDraft = true) {
            repository.takeOver(
                _uiState.value.draft,
                _uiState.value.status.mode,
                _uiState.value.status.targets,
            )
        }
    }

    fun setGlobalMode(global: Boolean) {
        val status = _uiState.value.status
        if (!global && status.targets.isEmpty()) {
            _uiState.update { it.copy(message = getString(R.string.uts_view_enter_targets)) }
            return
        }
        val mode = if (global) UtsViewMode.Global else UtsViewMode.Selected
        if (mode == status.mode) return
        operate {
            repository.save(status.fields, status.enabled, mode, status.targets)
        }
    }

    fun setTargets(targets: List<UtsTarget>) {
        val status = _uiState.value.status
        val normalized = targets.distinctBy { it.uid }.sortedBy { it.uid }
        if (status.mode == UtsViewMode.Selected && status.enabled && normalized.isEmpty()) {
            _uiState.update { it.copy(message = getString(R.string.uts_view_enter_targets)) }
            return
        }
        operate {
            repository.save(status.fields, status.enabled, status.mode, normalized)
        }
    }

    fun loadApps() {
        if (_uiState.value.appsLoading) return
        _uiState.update { it.copy(appsLoading = true) }
        viewModelScope.launch {
            try {
                val apps = repository.loadApps()
                _uiState.update { it.copy(apps = apps, appsLoaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = e.message ?: getString(R.string.uts_view_apps_failed))
                }
            } finally {
                _uiState.update { it.copy(appsLoading = false) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun operate(replaceDraft: Boolean = false, block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        val request = ++requestToken
        val generation = shellGeneration()
        _uiState.update { it.copy(busy = true, loading = false, message = null) }
        viewModelScope.launch {
            var refreshAfterFailure = false
            try {
                block()
                val status = repository.status()
                if (request != requestToken || generation != shellGeneration()) return@launch
                val current = if (status.provider == "susfs") null else readUname()
                _uiState.update {
                    it.copy(
                        status = status,
                        draft = if (replaceDraft || !it.hasLoaded) status.fields else it.draft,
                        hasLoaded = true,
                        currentUname = current,
                        draftSourceSlot = if (replaceDraft) null else it.draftSourceSlot,
                        draftSourceIdentity = if (replaceDraft) null else it.draftSourceIdentity,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refreshAfterFailure = true
                if (request == requestToken) {
                    _uiState.update {
                        it.copy(
                            message = e.message ?: getString(R.string.uts_view_operation_failed),
                        )
                    }
                }
            } finally {
                if (request == requestToken) {
                    _uiState.update { it.copy(busy = false) }
                    if (refreshAfterFailure) refresh()
                }
            }
        }
    }
}

private fun currentUnameSnapshot(): UtsFields {
    val uname = Os.uname()
    return UtsFields(
        sysname = uname.sysname,
        nodename = uname.nodename,
        release = uname.release,
        version = uname.version,
        machine = uname.machine,
        mask = 0x1f,
    )
}
