package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.susfs.SusfsDataSource
import me.weishu.kernelsu.data.susfs.SusfsRepository
import me.weishu.kernelsu.data.susfs.SusfsStatus
import me.weishu.kernelsu.data.susfs.SusfsApplyReport
import me.weishu.kernelsu.data.boot.KernelFacts
import me.weishu.kernelsu.data.boot.KernelFactsRepository
import me.weishu.kernelsu.data.boot.KernelFactsSource
import me.weishu.kernelsu.data.uts.UtsDataSource
import me.weishu.kernelsu.data.uts.UtsRepository
import me.weishu.kernelsu.ui.util.RootShell
import me.weishu.kernelsu.ui.util.RootShellStatus
import org.json.JSONObject

data class SusfsUiState(
    val status: SusfsStatus = SusfsStatus(),
    val config: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val report: SusfsApplyReport? = null,
    val savedNotice: Boolean = false,
    val pendingUnameApply: Boolean = false,
    val kernelFacts: KernelFacts? = null,
    val kernelFactsLoading: Boolean = false,
    val kernelFactsError: String? = null,
)

class SusfsViewModel(
    private val repository: SusfsDataSource = SusfsRepository(),
    private val utsRepository: UtsDataSource = UtsRepository(),
    private val kernelFactsSource: KernelFactsSource = KernelFactsRepository(),
    rootStatus: Flow<RootShellStatus> = RootShell.status,
    rootGeneration: Flow<Long> = RootShell.generation,
    private val shellGeneration: () -> Long = { RootShell.generation.value },
) : ViewModel() {
    private val mutable = MutableStateFlow(SusfsUiState())
    val state = mutable.asStateFlow()
    private var token = 0L

    init {
        viewModelScope.launch {
            combine(rootStatus, rootGeneration) { status, generation -> status to generation }
                .collect { (status, _) ->
                    if (status == RootShellStatus.Ready) refresh()
                    else {
                        token++
                        mutable.update {
                            it.copy(
                                status = SusfsStatus(protocol = "root_unavailable"),
                                config = null,
                                busy = false,
                                report = null,
                                savedNotice = false,
                                pendingUnameApply = false,
                                kernelFacts = null,
                                kernelFactsLoading = false,
                                kernelFactsError = null,
                            )
                        }
                    }
                }
        }
    }

    fun refresh() {
        if (mutable.value.busy) return
        val request = ++token
        val generation = shellGeneration()
        viewModelScope.launch {
            try {
                val status = repository.status()
                val config = if (status.error == null) repository.export() else null
                if (request == token && generation == shellGeneration()) {
                    mutable.update {
                        it.copy(
                            status = status,
                            config = config,
                            kernelFactsLoading = true,
                            kernelFactsError = null,
                        )
                    }
                } else {
                    return@launch
                }
                try {
                    val facts = kernelFactsSource.load()
                    if (request == token && generation == shellGeneration()) {
                        mutable.update {
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
                    if (request == token && generation == shellGeneration()) {
                        mutable.update {
                            it.copy(
                                kernelFactsLoading = false,
                                kernelFactsError = e.message ?: "Boot facts unavailable",
                            )
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == token && generation == shellGeneration()) mutable.update {
                    it.copy(
                        status = SusfsStatus(protocol = "root_unavailable", error = e.message),
                        config = null,
                        kernelFactsLoading = false,
                    )
                }
            }
        }
    }

    private fun operation(applying: Boolean = false, block: suspend () -> String) {
        if (mutable.value.busy) return
        val request = ++token
        val generation = shellGeneration()
        mutable.update { it.copy(busy = true, message = null, savedNotice = false, report = if (applying) null else it.report) }
        viewModelScope.launch {
            try {
                val result = block()
                val report = if (applying) SusfsApplyReport.parse(result) else null
                if (request == token && generation == shellGeneration()) {
                    mutable.update { it.copy(message = null, report = report ?: it.report, savedNotice = !applying) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == token && generation == shellGeneration()) {
                    mutable.update { it.copy(message = e.message ?: "Operation timed out; refresh status before retrying") }
                }
            }
            finally {
                if (request == token) {
                    mutable.update { it.copy(busy = false) }
                    refresh()
                }
            }
        }
    }

    fun save(text: String) {
        if (!mutable.value.status.detected || mutable.value.status.protocol != "compatible") return
        startSave { text }
    }

    fun apply() {
        if (!mutable.value.status.writable) return
        startApplyCheck()
    }

    fun change(edit: (JSONObject) -> Unit) {
        if (!mutable.value.status.detected || mutable.value.status.protocol != "compatible") return
        // Read fresh on-disk data rather than writing the screen's stale snapshot. This is not a
        // cross-process transaction: the daemon validates and atomically saves the complete file.
        startSave {
            val json = JSONObject(repository.export())
            edit(json)
            json.toString()
        }
    }

    fun confirmPendingUnameApply() {
        if (!mutable.value.pendingUnameApply || mutable.value.busy) return
        mutable.update { it.copy(pendingUnameApply = false) }
        operation(applying = true) { applySusfsWithUtsFallback(repository, utsRepository) }
    }

    fun dismissPendingUnameApply() {
        mutable.update { it.copy(pendingUnameApply = false) }
    }

    fun message(text: String) { mutable.update { it.copy(message = text) } }

    private fun startApplyCheck() {
        if (mutable.value.busy || mutable.value.pendingUnameApply) return
        val request = ++token
        val generation = shellGeneration()
        mutable.update { it.copy(busy = true, message = null, savedNotice = false) }
        viewModelScope.launch {
            try {
                val text = repository.export()
                val confirmationRequired = requiresUtsConfirmation(text)
                if (request != token || generation != shellGeneration()) return@launch
                mutable.update {
                    it.copy(
                        busy = false,
                        pendingUnameApply = confirmationRequired,
                    )
                }
                if (!confirmationRequired) {
                    operation(applying = true) { repository.apply() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == token && generation == shellGeneration()) {
                    mutable.update {
                        it.copy(message = e.message ?: "Operation timed out; refresh status before retrying")
                    }
                }
            } finally {
                if (request == token) mutable.update { it.copy(busy = false) }
            }
        }
    }

    private fun startSave(produce: suspend () -> String) {
        if (mutable.value.busy || mutable.value.pendingUnameApply) return
        val request = ++token
        val generation = shellGeneration()
        mutable.update {
            it.copy(
                busy = true,
                message = null,
                savedNotice = false,
            )
        }
        viewModelScope.launch {
            try {
                val text = produce()
                if (request != token || generation != shellGeneration()) return@launch
                repository.save(text)
                if (request == token && generation == shellGeneration()) {
                    mutable.update { it.copy(message = null, savedNotice = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == token && generation == shellGeneration()) {
                    mutable.update {
                        it.copy(message = e.message ?: "Operation timed out; refresh status before retrying")
                    }
                }
            } finally {
                if (request == token) {
                    mutable.update { it.copy(busy = false) }
                    refresh()
                }
            }
        }
    }

    private suspend fun requiresUtsConfirmation(text: String): Boolean {
        if (!susfsUnameSpoofs(text)) return false
        return utsRepository.status().provider == "uts"
    }
}

internal fun susfsUnameSpoofs(text: String): Boolean {
    val settings = JSONObject(text).optJSONObject("settings") ?: return false
    val uname = settings.optJSONObject("uname") ?: return false
    return uname.optString("release") != "default" || uname.optString("version") != "default"
}

internal suspend fun applySusfsWithUtsFallback(
    repository: SusfsDataSource,
    utsRepository: UtsDataSource,
): String {
    utsRepository.disable()
    val result = try {
        repository.apply()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val status = runCatching { repository.status() }.getOrNull()
        when (susfsUnameOutcome(status, null)) {
            SusfsUnameOutcome.Applied -> throw IllegalStateException(
                "SUSFS apply reported an error after uname took effect; UTS View remains disabled",
                error,
            )
            SusfsUnameOutcome.Unknown -> throw IllegalStateException(
                "SUSFS uname result is unknown; UTS View remains disabled: ${error.message}",
                error,
            )
            SusfsUnameOutcome.ConfirmedNotApplied -> restoreUts(utsRepository, error)
        }
    }
    val report = runCatching { SusfsApplyReport.parse(result) }.getOrNull()
    val status = runCatching { repository.status() }.getOrNull()
    when (susfsUnameOutcome(status, report)) {
        SusfsUnameOutcome.Applied -> Unit
        SusfsUnameOutcome.Unknown -> throw IllegalStateException(
            "SUSFS uname result is unknown; UTS View remains disabled",
        )
        SusfsUnameOutcome.ConfirmedNotApplied -> {
            val detail = report?.failures?.get("uname") ?: "SUSFS uname was not applied"
            restoreUts(utsRepository, IllegalStateException(detail))
        }
    }
    return result
}

internal enum class SusfsUnameOutcome {
    Applied,
    ConfirmedNotApplied,
    Unknown,
}

internal fun susfsUnameOutcome(
    status: SusfsStatus?,
    report: SusfsApplyReport?,
): SusfsUnameOutcome {
    if (status?.unameApplied == true || report?.applied?.contains("uname") == true) {
        return SusfsUnameOutcome.Applied
    }
    if (report?.failures?.containsKey("uname") == true ||
        report?.deferred?.contains("uname") == true
    ) {
        return SusfsUnameOutcome.ConfirmedNotApplied
    }
    if (status == null || status.unameUncertain) {
        return SusfsUnameOutcome.Unknown
    }
    return SusfsUnameOutcome.ConfirmedNotApplied
}

private suspend fun restoreUts(utsRepository: UtsDataSource, cause: Throwable): Nothing {
    val detail = cause.message ?: "SUSFS uname was not applied"
    try {
        utsRepository.enable()
    } catch (error: Exception) {
        throw IllegalStateException(
            "$detail; UTS View could not be restored: ${error.message}",
            cause,
        )
    }
    throw IllegalStateException("$detail; UTS View was restored", cause)
}
