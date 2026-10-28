package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.umount.UmountPath
import me.weishu.kernelsu.data.umount.UmountPathSource
import me.weishu.kernelsu.data.umount.UmountPathsData
import me.weishu.kernelsu.data.umount.UmountPathsRepository
import me.weishu.kernelsu.data.umount.UmountPathsRepositoryImpl

enum class UmountPathsNotice {
    ADDED,
    DELETED,
    CLEARED,
}

@Immutable
data class UmountPathsUiState(
    val isLoading: Boolean = true,
    val isWriting: Boolean = false,
    val hasLoaded: Boolean = false,
    val kernelSupported: Boolean = true,
    val configReadable: Boolean = true,
    val readError: Boolean = false,
    val actionError: Boolean = false,
    val notice: UmountPathsNotice? = null,
    val paths: List<UmountPath> = emptyList(),
) {
    val canWrite: Boolean get() = hasLoaded && configReadable && !isWriting
    val custom: List<UmountPath> get() = paths.filter { it.source == UmountPathSource.CUSTOM }
    val kernelEntries: List<UmountPath> get() = paths.filter { it.source == UmountPathSource.KERNEL }
    val unknown: List<UmountPath> get() = paths.filter { it.source == UmountPathSource.UNKNOWN }
}

class UmountPathsViewModel(
    private val repository: UmountPathsRepository = UmountPathsRepositoryImpl(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(UmountPathsUiState())
    val uiState: StateFlow<UmountPathsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isWriting) return
        _uiState.update { it.copy(isLoading = true, actionError = false) }
        viewModelScope.launch {
            updateFromLoad()
        }
    }

    fun add(path: String) = runAction(
        notice = UmountPathsNotice.ADDED,
        block = { repository.add(path) },
    )

    fun delete(path: String) = runAction(
        notice = UmountPathsNotice.DELETED,
        block = { repository.delete(path) },
    )

    fun clear() = runAction(
        notice = UmountPathsNotice.CLEARED,
        block = { repository.clear() },
    )

    private suspend fun updateFromLoad() {
        val config = repository.loadConfig()
        val kernel = repository.loadKernel()
        _uiState.update { previous ->
            val snapshot = UmountPathsData.merge(
                config = config.getOrNull(),
                kernel = kernel.getOrNull(),
                previous = previous.paths.takeIf { previous.hasLoaded },
            )
            previous.copy(
                isLoading = false,
                hasLoaded = true,
                kernelSupported = snapshot.kernelSupported,
                configReadable = snapshot.configReadable,
                readError = snapshot.readError,
                paths = snapshot.paths,
            )
        }
    }

    private fun runAction(
        notice: UmountPathsNotice,
        block: suspend () -> Result<Unit>,
    ) {
        if (!_uiState.value.canWrite) return
        _uiState.update {
            it.copy(isWriting = true, actionError = false, notice = null)
        }
        viewModelScope.launch {
            val result = block()
            val config = repository.loadConfig()
            val kernel = repository.loadKernel()
            _uiState.update { previous ->
                val snapshot = UmountPathsData.merge(
                    config = config.getOrNull(),
                    kernel = kernel.getOrNull(),
                    previous = previous.paths,
                )
                previous.copy(
                    isWriting = false,
                    hasLoaded = previous.hasLoaded || result.isSuccess,
                    kernelSupported = snapshot.kernelSupported,
                    configReadable = snapshot.configReadable,
                    readError = snapshot.readError,
                    actionError = result.isFailure,
                    notice = notice.takeIf { result.isSuccess },
                    paths = snapshot.paths,
                )
            }
        }
    }
}
