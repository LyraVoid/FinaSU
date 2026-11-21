package me.weishu.kernelsu.ui.screen.install

import android.app.Activity
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.getKernelVersion
import me.weishu.kernelsu.ui.component.choosekmidialog.ChooseKmiDialog
import me.weishu.kernelsu.ui.component.dialog.DownloadDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.util.LkmSelection
import me.weishu.kernelsu.ui.util.getAvailablePartitions
import me.weishu.kernelsu.ui.util.getCurrentKmi
import me.weishu.kernelsu.ui.util.getDefaultPartition
import me.weishu.kernelsu.ui.util.getSlotSuffix
import me.weishu.kernelsu.ui.util.isAbDevice
import me.weishu.kernelsu.ui.util.probeRemoteBootPartitions
import me.weishu.kernelsu.ui.util.rootAvailable

@Composable
fun InstallScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var probeJob by remember { mutableStateOf<Job?>(null) }
    val loadingDialog = rememberLoadingDialog()

    var installMode by rememberSaveable { mutableStateOf(InstallMode.Lkm) }
    var lkmInstallMethod by rememberSaveable { mutableStateOf<InstallMethod?>(null) }
    var anyKernelInstallMethod by rememberSaveable { mutableStateOf<InstallMethod?>(null) }
    var downloadDialogShown by rememberSaveable { mutableStateOf(false) }
    var remotePartitions by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var remotePartitionSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var lkmSelection by rememberSaveable { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var partitionSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var hasCustomSelected by rememberSaveable { mutableStateOf(false) }
    val showChooseKmiDialog = rememberSaveable { mutableStateOf(false) }
    var advancedOptionsShown by rememberSaveable { mutableStateOf(false) }
    var allowShell by rememberSaveable { mutableStateOf(false) }
    var enableAdb by rememberSaveable { mutableStateOf(false) }
    var forceBackup by rememberSaveable { mutableStateOf(false) }
    var anyKernelSlotIndex by rememberSaveable { mutableIntStateOf(0) }
    val settings = remember { SettingsRepositoryImpl() }
    var backupBeforeFlash by rememberSaveable { mutableStateOf(settings.backupBeforeFlash) }

    val currentKmi by produceState(initialValue = "") { value = getCurrentKmi() }
    val partitions by produceState(initialValue = emptyList()) { value = getAvailablePartitions() }
    val defaultPartition by produceState(initialValue = "") { value = getDefaultPartition() }
    val rootAvailable by produceState(initialValue = false) { value = rootAvailable() }
    val isAbDevice by produceState(initialValue = false) { value = isAbDevice() }
    val isGkiDevice by produceState(initialValue = false) { value = getKernelVersion().isGKI() }

    val selectFileTip = stringResource(id = R.string.select_file_tip, defaultPartition)
    val selectFileTipNoGki = stringResource(id = R.string.select_file_tip_nogki)
    val downloadFileMsg = stringResource(id = R.string.download_dialog_msg)
    val anyKernelSummary = stringResource(R.string.install_anykernel_summary)
    val allInstallMethodOptions = remember(rootAvailable, isAbDevice, isGkiDevice, selectFileTip, selectFileTipNoGki, downloadFileMsg, anyKernelSummary) {
        buildList {
            add(InstallMethod.SelectFile(summary = if (isGkiDevice) selectFileTip else selectFileTipNoGki))
            add(InstallMethod.DownloadFile(summary = downloadFileMsg))
            if (rootAvailable) add(InstallMethod.AnyKernel(summary = anyKernelSummary))
            if (rootAvailable && isGkiDevice) {
                add(InstallMethod.DirectInstall)
                if (isAbDevice) add(InstallMethod.DirectInstallToInactiveSlot)
            }
        }
    }
    val installMethodOptions = allInstallMethodOptions.filter {
        (it is InstallMethod.AnyKernel) == (installMode == InstallMode.AnyKernel)
    }
    val anyKernelAvailable = allInstallMethodOptions.any { it is InstallMethod.AnyKernel }
    val installMethod = when (installMode) {
        InstallMode.Lkm -> lkmInstallMethod
        InstallMode.AnyKernel -> anyKernelInstallMethod
    }
    val anyKernelMethod = anyKernelInstallMethod as? InstallMethod.AnyKernel

    fun setInstallMethod(method: InstallMethod) {
        if (method is InstallMethod.AnyKernel) {
            anyKernelInstallMethod = method
        } else {
            lkmInstallMethod = method
        }
    }

    val isOta = installMethod is InstallMethod.DirectInstallToInactiveSlot
    val slotSuffix by produceState(initialValue = "", isOta) { value = getSlotSuffix(isOta) }
    val defaultIndex = remember(partitions, defaultPartition) {
        partitions.indexOf(defaultPartition).coerceAtLeast(0)
    }

    LaunchedEffect(partitions, defaultIndex, hasCustomSelected) {
        if (partitions.isEmpty()) return@LaunchedEffect
        if (!hasCustomSelected) {
            partitionSelectionIndex = defaultIndex.coerceIn(0, partitions.lastIndex)
        } else if (partitionSelectionIndex > partitions.lastIndex) {
            partitionSelectionIndex = partitions.lastIndex
        }
    }

    val displayPartitions = remember(partitions, defaultPartition) {
        partitions.map { name -> if (defaultPartition == name) "$name (default)" else name }
    }
    val remoteDisplayPartitions = remember(remotePartitions, defaultPartition) {
        remotePartitions.map { name -> if (defaultPartition == name) "$name (default)" else name }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHost.showSnackbar(message)
        }
    }

    val onInstall: () -> Unit = {
        installMethod?.let { method ->
            navigator.push(
                Route.Flash(
                    when (method) {
                        is InstallMethod.AnyKernel -> FlashIt.FlashAnyKernel(
                            uri = method.uri ?: return@let,
                            displayName = method.displayName,
                            sizeBytes = method.sizeBytes,
                            inactiveSlot = method.inactiveSlot,
                        )
                        is InstallMethod.DownloadFile -> FlashIt.DownloadBoot(
                            url = method.url ?: return@let,
                            partition = method.partition ?: return@let,
                            lkm = lkmSelection,
                            allowShell = allowShell,
                            enableAdb = enableAdb,
                            backup = forceBackup,
                        )
                        else -> FlashIt.FlashBoot(
                            boot = if (method is InstallMethod.SelectFile) method.uri else null,
                            lkm = lkmSelection,
                            ota = method is InstallMethod.DirectInstallToInactiveSlot,
                            partition = partitions.getOrNull(partitionSelectionIndex),
                            allowShell = allowShell,
                            enableAdb = enableAdb,
                            backup = method is InstallMethod.SelectFile && forceBackup,
                        )
                    }
                )
            )
        }
    }

    val anyKernelConfirm = rememberConfirmDialog(onConfirm = onInstall, onDismiss = null)
    val anyKernelTitle = stringResource(R.string.install_anykernel)
    val selectAnyKernelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                val file = readAnyKernelFileInfo(context, it)
                anyKernelSlotIndex = 0
                anyKernelInstallMethod = InstallMethod.AnyKernel(
                    uri = it,
                    summary = anyKernelSummary,
                    displayName = file.displayName,
                    sizeBytes = file.sizeBytes,
                )
            }
        }
    }

    ChooseKmiDialog(
        show = showChooseKmiDialog.value,
        onDismissRequest = { showChooseKmiDialog.value = false },
        onSelected = { kmi ->
            kmi?.let {
                lkmSelection = LkmSelection.KmiString(it)
                onInstall()
            }
        }
    )

    DownloadDialog(
        show = downloadDialogShown,
        onConfirm = { url ->
            downloadDialogShown = false
            probeJob?.cancel()
            probeJob = scope.launch {
                try {
                    loadingDialog.showLoading()
                    val result = probeRemoteBootPartitions(url)
                    if (result.partitions.isEmpty()) {
                        showMessage(resources.getString(R.string.download_no_boot_partition))
                    } else {
                        val defaultIdx = result.partitions.indexOf(defaultPartition).coerceAtLeast(0)
                        remotePartitions = result.partitions
                        remotePartitionSelectionIndex = defaultIdx
                        lkmInstallMethod = InstallMethod.DownloadFile(
                            url = url,
                            partition = result.partitions[defaultIdx],
                            summary = downloadFileMsg,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage(
                        resources.getString(R.string.download_probe_failed, e.message ?: "")
                    )
                } finally {
                    loadingDialog.hide()
                }
            }
        },
        onDismiss = { downloadDialogShown = false }
    )

    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                if (isKoFile(context, uri)) {
                    lkmSelection = LkmSelection.LkmUri(uri)
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    showMessage(resources.getString(R.string.install_only_support_ko_file))
                }
            }
        }
    }
    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                lkmInstallMethod = InstallMethod.SelectFile(uri, summary = if (isGkiDevice) selectFileTip else selectFileTipNoGki)
            }
        }
    }

    val state = InstallUiState(
        installMode = installMode,
        installMethod = installMethod,
        anyKernelAvailable = anyKernelAvailable,
        lkmSelection = lkmSelection,
        partitionSelectionIndex = partitionSelectionIndex,
        displayPartitions = displayPartitions,
        remoteDisplayPartitions = remoteDisplayPartitions,
        remotePartitionSelectionIndex = remotePartitionSelectionIndex,
        currentKmi = currentKmi,
        slotSuffix = slotSuffix,
        installMethodOptions = installMethodOptions,
        canSelectPartition = installMethod is InstallMethod.DirectInstall ||
            installMethod is InstallMethod.DirectInstallToInactiveSlot ||
            installMethod is InstallMethod.DownloadFile,
        advancedOptionsShown = advancedOptionsShown,
        allowShell = allowShell,
        enableAdb = enableAdb,
        forceBackup = forceBackup,
        canForceBackup = installMethod is InstallMethod.SelectFile,
        backupBeforeFlash = backupBeforeFlash,
        anyKernelFileName = anyKernelMethod?.displayName,
        anyKernelSizeLabel = anyKernelMethod?.sizeBytes?.let {
            Formatter.formatShortFileSize(context, it)
        } ?: resources.getString(R.string.install_anykernel_size_unknown),
        anyKernelSlotOptions = if (isAbDevice) {
            listOf(
                if (slotSuffix.isBlank()) {
                    resources.getString(R.string.install_anykernel_current_slot_no_suffix)
                } else {
                    resources.getString(R.string.install_anykernel_current_slot, slotSuffix)
                },
                resources.getString(
                    R.string.install_anykernel_inactive_slot,
                    when (slotSuffix) {
                        "_a" -> "_b"
                        else -> "_a"
                    },
                ),
            )
        } else {
            emptyList()
        },
        anyKernelSlotIndex = anyKernelSlotIndex,
        showAnyKernelSlotSelector = isAbDevice && anyKernelMethod != null,
    )
    val actions = InstallScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSelectMode = { installMode = it },
        onSelectMethod = { setInstallMethod(it) },
        onDownloadFile = { downloadDialogShown = true },
        onSelectBootImage = {
            selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/octet-stream" })
        },
        onSelectAnyKernel = { selectAnyKernelLauncher.launch("application/zip") },
        onSelectAnyKernelSlot = { index ->
            anyKernelSlotIndex = index.coerceIn(0, 1)
            val method = anyKernelMethod
            if (method is InstallMethod.AnyKernel) {
                anyKernelInstallMethod = method.copy(inactiveSlot = anyKernelSlotIndex == 1)
            }
        },
        onUploadLkm = {
            selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/octet-stream" })
        },
        onClearLkm = { lkmSelection = LkmSelection.KmiNone },
        onSelectPartition = { index ->
            hasCustomSelected = true
            val method = lkmInstallMethod
            if (method is InstallMethod.DownloadFile) {
                remotePartitionSelectionIndex = index
                lkmInstallMethod = method.copy(partition = remotePartitions.getOrNull(index))
            } else {
                partitionSelectionIndex = index
            }
        },
        onNext = {
            val anyKernelMethod = installMethod as? InstallMethod.AnyKernel
            if (anyKernelMethod != null) {
                val method = anyKernelMethod
                val fileName = method.displayName
                    ?: resources.getString(R.string.install_anykernel_file_unknown)
                val size = method.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
                    ?: resources.getString(R.string.install_anykernel_size_unknown)
                val targetSlot = when {
                    !method.inactiveSlot && slotSuffix.isBlank() ->
                        resources.getString(R.string.install_anykernel_current_slot_no_suffix)
                    !method.inactiveSlot ->
                        resources.getString(R.string.install_anykernel_current_slot, slotSuffix)
                    else -> resources.getString(
                        R.string.install_anykernel_inactive_slot,
                        if (slotSuffix == "_a") "_b" else "_a",
                    )
                }
                val details = resources.getString(
                    R.string.install_anykernel_confirm_details,
                    fileName,
                    size,
                    targetSlot,
                )
                val note = resources.getString(R.string.install_anykernel_confirm_note)
                anyKernelConfirm.showConfirm(anyKernelTitle, "$details\n\n$note")
            } else {
                val isLkmSelected = lkmSelection != LkmSelection.KmiNone
                val isKmiUnknown = currentKmi.isBlank()
                val isKmiUnresolved = when (installMethod) {
                    // The download flow extracts the KMI itself; no manual
                    // selection needed.
                    is InstallMethod.DownloadFile -> false
                    is InstallMethod.SelectFile -> true
                    else -> isKmiUnknown
                }
                if (!isLkmSelected && isKmiUnresolved) {
                    showChooseKmiDialog.value = true
                } else {
                    onInstall()
                }
            }
        },
        onAdvancedOptionsClicked = {
            advancedOptionsShown = !advancedOptionsShown
        },
        onSelectAllowShell = {
            allowShell = it
        },
        onSelectEnableAdb = {
            enableAdb = it
        },
        onSelectForceBackup = {
            forceBackup = it
        },
        onSelectBackupBeforeFlash = {
            backupBeforeFlash = it
            settings.backupBeforeFlash = it
        },
    )

    InstallScreenMaterial(state, actions, snackbarHost)
}
