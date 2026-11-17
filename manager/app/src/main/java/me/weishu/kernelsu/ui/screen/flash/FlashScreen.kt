package me.weishu.kernelsu.ui.screen.flash

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.isSoftRebootPreferred
import me.weishu.kernelsu.data.security.SecurityTarget
import me.weishu.kernelsu.ui.component.security.LocalAppAuthenticator
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.getSlotSuffix
import me.weishu.kernelsu.ui.util.reboot

@Composable
fun FlashScreen(flashIt: FlashIt, onBackEnabledChange: (Boolean) -> Unit) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder() }
    var showRebootAction by rememberSaveable { mutableStateOf(false) }
    var flashingStatus by rememberSaveable { mutableStateOf(FlashingStatus.FLASHING) }
    var actionInProgress by rememberSaveable { mutableStateOf(false) }
    val anyKernel = flashIt as? FlashIt.FlashAnyKernel
    val currentSlotSuffix by produceState(initialValue = "", anyKernel) {
        value = if (anyKernel == null) "" else getSlotSuffix(false)
    }
    val needJailbreakWarning = flashIt is FlashIt.FlashBoot && Natives.isLateLoadMode
    // Soft reboot keeps the jailbreak and still applies modules
    val softReboot = flashIt is FlashIt.FlashModules && isSoftRebootPreferred()
    var flashingEnabled by rememberSaveable { mutableStateOf(!needJailbreakWarning) }
    val target = if (flashIt is FlashIt.FlashModules) SecurityTarget.MODULE_INSTALL
    else SecurityTarget.MODULE_FLASH
    val authenticator = LocalAppAuthenticator.current
    var authorized by remember(flashIt, authenticator) { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val backEnabled = !flashingEnabled || !authorized || flashingStatus != FlashingStatus.FLASHING
    val backBlockedMessage = stringResource(R.string.flash_back_blocked)

    fun showMessage(message: String) {
        scope.launch {
            snackbarHost.showSnackbar(message)
        }
    }

    LaunchedEffect(flashIt, authenticator, flashingEnabled, authorized) {
        if (!flashingEnabled || authorized) return@LaunchedEffect
        if (authenticator.require(target)) authorized = true else navigator.pop()
    }

    SideEffect { onBackEnabledChange(backEnabled) }
    // A running flash holds the page: answer the gesture with a reason, so a decline does not read
    // as a gesture that never arrived.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !backEnabled,
        onBackCompleted = { showMessage(backBlockedMessage) },
    )

    FlashEffect(
        flashIt = flashIt,
        text = text,
        logContent = logContent,
        onTextUpdate = { text = it },
        onShowRebootChange = { showRebootAction = it },
        onFlashingStatusChange = { flashingStatus = it },
        showStderr = anyKernel != null,
        enabled = flashingEnabled && authorized,
    )

    val targetSlotSuffix = when {
        anyKernel?.inactiveSlot != true -> currentSlotSuffix
        currentSlotSuffix == "_a" -> "_b"
        else -> "_a"
    }
    val state = FlashUiState(
        text = text,
        showRebootAction = showRebootAction,
        flashingStatus = flashingStatus,
        showJailbreakWarning = needJailbreakWarning && !flashingEnabled,
        rebootLabelRes = if (softReboot) R.string.reboot_soft else R.string.reboot,
        actionInProgress = actionInProgress,
    )
    val logFilePrefix = if (anyKernel != null) {
        val target = if (anyKernel.inactiveSlot) "inactive" else "current"
        val suffix = targetSlotSuffix.removePrefix("_").ifBlank { "unknown" }
        "AnyKernel3_${target}_$suffix"
    } else {
        "KernelSU_install_log"
    }
    val actions = FlashScreenActions(
        onBack = dropUnlessResumed { if (backEnabled) navigator.pop() },
        onSaveLog = saveLog(logContent, scope, logFilePrefix) { showMessage(it) },
        onReboot = {
            actionInProgress = true
            scope.launch {
                if (!authenticator.require(SecurityTarget.REBOOT)) return@launch
                withContext(Dispatchers.IO) {
                    reboot(if (softReboot) "soft_reboot" else "")
                }
            }
        },
        onClose = dropUnlessResumed { navigator.pop() },
        onConfirmJailbreakWarning = { flashingEnabled = true },
        onDismissJailbreakWarning = dropUnlessResumed { navigator.pop() },
    )

    FlashScreenMaterial(state, actions, snackbarHost)
}
