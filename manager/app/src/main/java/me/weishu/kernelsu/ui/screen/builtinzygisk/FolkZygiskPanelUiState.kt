package me.weishu.kernelsu.ui.screen.builtinzygisk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkZygiskStatus
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.data.repository.requireKsud
import me.weishu.kernelsu.ui.util.execKsudResult

data class FolkZygiskPanelUiState(
    val status: FolkZygiskStatus? = null,
    val checked: Long? = null,
    val busy: Boolean = false,
    val readError: Boolean = false,
    val message: Int? = null,
) {
    val panel = status?.takeUnless { readError }?.let { mapStatus(it, it.runtime) }
}

internal object FolkZygiskStore {
    private val repo = SettingsRepositoryImpl()
    private val lock = Mutex()
    private val mutable = MutableStateFlow(FolkZygiskPanelUiState())
    val state = mutable.asStateFlow()
    private var pendingGeneration: Long? = null

    suspend fun refresh(action: PanelAction? = null) {
        if (!lock.tryLock()) return
        val old = mutable.value
        try {
            if (action != null && when (action) {
                    PanelAction.Enable, PanelAction.Disable -> old.panel?.canToggle != true
                    else -> action !in old.panel?.actions.orEmpty()
                }) return
            mutable.value = old.copy(busy = true, message = null)
            val write = action?.let {
                val command = when (it) {
                    PanelAction.Enable -> "zygisk enable"
                    PanelAction.Disable -> "zygisk disable"
                    PanelAction.Install -> "zygisk install-engine"
                    PanelAction.Recover -> "zygisk recover"
                    PanelAction.Reset -> "zygisk reset-failures"
                }
                runCatching { requireKsud(execKsudResult(command), "FolkZygisk") }
            }
            if (write?.isSuccess == true && action in setOf(PanelAction.Enable, PanelAction.Disable, PanelAction.Recover)) {
                pendingGeneration = old.status?.startupGeneration
            }
            val read = repo.getFolkZygiskStatus()
            val fresh = read.getOrNull()?.let { it.copy(pendingRestart = pendingGeneration == it.startupGeneration) }
            mutable.value = FolkZygiskPanelUiState(
                status = fresh ?: old.status, checked = System.currentTimeMillis(), readError = read.isFailure,
                message = when {
                    write == null -> null
                    write.isFailure -> R.string.folkzygisk_write_failed
                    read.isFailure -> R.string.folkzygisk_read_error
                    fresh?.pendingRestart == true || fresh?.rebootRequired == true -> R.string.folkzygisk_reboot_required
                    else -> R.string.fz_saved
                },
            )
        } finally {
            mutable.value = mutable.value.copy(busy = false)
            lock.unlock()
        }
    }
}
