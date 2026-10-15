package me.weishu.kernelsu.ui.screen.module

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalConfiguration
import me.weishu.kernelsu.ui.component.bottombar.LocalFloatingBarReservedHeight
import me.weishu.kernelsu.ui.component.bottombar.LocalFloatingBarVisible
import me.weishu.kernelsu.ui.component.bottombar.LocalIsFloatingNavMode
import me.weishu.kernelsu.ui.component.security.LocalAppAuthenticator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.LayoutVariant
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.data.model.ModuleUpdateInfo
import me.weishu.kernelsu.data.security.SecurityTarget
import me.weishu.kernelsu.data.repository.isSoftRebootPreferred
import me.weishu.kernelsu.modulebanner.ModuleCardConfig
import me.weishu.kernelsu.ui.LocalLayoutVariant
import me.weishu.kernelsu.ui.component.ObserveAsEvents
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.card.CardBannerDialog
import me.weishu.kernelsu.ui.component.card.CardSubject
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.FolkButton
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.FolkOutlinedButton
import me.weishu.kernelsu.ui.component.material.FolkSmallExtendedFloatingActionButton
import me.weishu.kernelsu.ui.component.material.FolkTextButton
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.rememberSearchAppBarScrollBehavior
import me.weishu.kernelsu.ui.component.module.splicedLazyColumnGroup
import me.weishu.kernelsu.ui.component.module.TwoColumnGrid
import me.weishu.kernelsu.ui.util.reboot

@SuppressLint("StringFormatInvalid")
@Composable
fun ModulePagerMaterial(
    uiState: ModuleUiState,
    confirmDialogState: ModuleConfirmDialogState?,
    moduleEvent: Flow<ModuleEffect>,
    actions: ModuleActions,
    bottomInnerPadding: Dp,
) {
    val snackBarHost = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    val context = LocalContext.current
    val resource = LocalResources.current


    val pullToRefreshState = rememberPullToRefreshState()

    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()

    val m3eLayout = LocalLayoutVariant.current == LayoutVariant.M3E
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val searchBarScrollBehavior = rememberSearchAppBarScrollBehavior(topAppBarScrollBehavior)
    val refreshTick = remember { mutableIntStateOf(0) }
    val threshold = with(LocalDensity.current) { 100.dp.toPx() }
    val fabExpanded by remember {
        var lastIndex = 0
        var lastOffset = 0
        var scrollDelta = 0f
        var expanded = true
        derivedStateOf {
            val currentIndex = listState.firstVisibleItemIndex
            val currentOffset = listState.firstVisibleItemScrollOffset
            val delta = if (currentIndex == lastIndex) {
                (currentOffset - lastOffset).toFloat()
            } else if (currentIndex > lastIndex) {
                100f
            } else {
                -100f
            }
            scrollDelta = (scrollDelta + delta).coerceIn(-threshold, threshold)
            lastIndex = currentIndex
            lastOffset = currentOffset
            if (currentIndex == 0) {
                expanded = true
                scrollDelta = 0f
            } else if (expanded && scrollDelta >= threshold) {
                expanded = false
                scrollDelta = 0f
            } else if (!expanded && scrollDelta <= -threshold) {
                expanded = true
                scrollDelta = 0f
            }
            expanded
        }
    }

    val shortcutState = rememberModuleShortcutState(context)
    val showShortcutDialog = remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var bannerDialogModule by remember { mutableStateOf<Module?>(null) }
    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            when (val request = confirmDialogState?.request) {
                is ModuleConfirmRequest.Uninstall -> actions.onUninstallModule(request.module)
                is ModuleConfirmRequest.Update -> actions.onConfirmUpdate(request)
                null -> Unit
            }
        },
        onDismiss = actions.onDismissConfirmRequest,
    )

    fun openShortcutDialogForType(type: ShortcutType) {
        shortcutState.selectType(type)
        showShortcutDialog.value = true
    }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        shortcutState.updateIconUri(uri?.toString())
    }

    fun onModuleAddShortcut(module: Module, type: ShortcutType) {
        shortcutState.bindModule(module)
        openShortcutDialogForType(type)
    }

    LaunchedEffect(confirmDialogState) {
        confirmDialogState?.let {
            confirmDialog.showConfirm(
                title = it.title,
                content = it.content,
                markdown = it.markdown,
                html = it.html,
                confirm = it.confirm,
                dismiss = it.dismiss,
            )
        }
    }

    val scope = rememberCoroutineScope()
    val authenticator = LocalAppAuthenticator.current
    val snackbarJob = remember { mutableStateOf<Job?>(null) }
    ObserveAsEvents(moduleEvent) { event ->
        when (event) {
            is ModuleEffect.Toast -> {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }

            is ModuleEffect.SnackBar -> {
                // Cancel the previous reboot snackbar so a new one replaces it instead of queueing
                snackbarJob.value?.cancel()
                snackBarHost.currentSnackbarData?.dismiss()
                // Soft reboot keeps the jailbreak and still applies module changes
                val softReboot = isSoftRebootPreferred()
                snackbarJob.value = scope.launch {
                    val result = snackBarHost.showSnackbar(
                        message = event.message,
                        actionLabel = resource.getString(if (softReboot) R.string.reboot_soft else R.string.reboot),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        if (authenticator.require(SecurityTarget.REBOOT)) {
                            reboot(if (softReboot) "soft_reboot" else "")
                        }
                    }
                }
            }
        }
    }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                largeTitle = m3eLayout,
                topSearch = !m3eLayout,
                inlineSearch = m3eLayout,
                inlineSearchHint = stringResource(R.string.module_search_hint),
                scrollBehavior = if (m3eLayout) searchBarScrollBehavior else null,
                title = { Text(stringResource(R.string.module)) },
                searchText = uiState.searchStatus.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                snackbarHostState = snackBarHost,
                navigationIcon = {
                    FolkIconButton(
                        onClick = { actions.onOpenRepo() }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = stringResource(id = R.string.module_repos)
                        )
                    }
                },
                actions = {
                    FolkIconButton(
                        onClick = { showSortSheet = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(id = R.string.module_sort)
                        )
                    }
                },
                searchContent = { bottomPadding, closeSearch ->
                    val latestSearchResults = rememberUpdatedState(uiState.searchResults)
                    ScrollToTopOnChange(
                        searchListState,
                        uiState.searchStatus.searchText,
                    ) { latestSearchResults.value }
                    ModuleList(
                        bottomInnerPadding = bottomPadding,
                        modifier = Modifier.fillMaxSize(),
                        listState = searchListState,
                        displayModules = uiState.searchResults,
                        updateInfoMap = uiState.updateInfo,
                        actions = actions,
                        onModuleAddShortcut = { module, type -> onModuleAddShortcut(module, type) },
                        onModuleLongPress = { bannerDialogModule = it },
                        closeSearch = closeSearch,
                    )
                }
            )
        },
        floatingActionButton = {
            if (uiState.installButtonVisible) {
                val moduleInstall = stringResource(id = R.string.module_install)
                val selectZipLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { activityResult ->
                    if (activityResult.resultCode != RESULT_OK) {
                        return@rememberLauncherForActivityResult
                    }
                    val data = activityResult.data ?: return@rememberLauncherForActivityResult
                    val clipData = data.clipData

                    val uris = mutableListOf<Uri>()
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                        }
                    } else {
                        data.data?.let { uris.add(it) }
                    }

                    actions.onOpenFlash(uris)
                }

                val floatingNavMode = LocalIsFloatingNavMode.current
                val floatingBarVisible = LocalFloatingBarVisible.current
                val floatingBarReserved = LocalFloatingBarReservedHeight.current
                val isLandscape =
                    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val fabDrop by animateDpAsState(
                    targetValue = if (floatingNavMode && (isLandscape || !floatingBarVisible)) {
                        floatingBarReserved
                    } else {
                        0.dp
                    },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "fabDrop",
                )
                Box(modifier = Modifier.offset(y = fabDrop)) {
                    FolkSmallExtendedFloatingActionButton(
                        modifier = Modifier.padding(bottom = bottomInnerPadding),
                        expanded = fabExpanded,
                        onClick = {
                            // Select the zip files to install
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            }
                            selectZipLauncher.launch(intent)
                        },
                        icon = { Icon(Icons.Filled.Add, moduleInstall) },
                        text = { Text(text = moduleInstall) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = {
            SnackBarHost(hostState = snackBarHost, modifier = Modifier.let {
                if (!uiState.installButtonVisible) it.padding(
                    bottom =
                        bottomInnerPadding
                ) else it
            })
        }
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            if (uiState.magiskInstalled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.module_magisk_conflict),
                        textAlign = TextAlign.Center,
                    )
                }
                return@PullToRefreshBox
            }
            val latestModuleList = rememberUpdatedState(uiState.moduleList)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                listState,
                uiState.sortGroups,
                uiState.sortEnabledFirst,
                uiState.customOrder,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestModuleList.value }

            val m3eSearchQuery = uiState.searchStatus.searchText
            val displayModules =
                if (m3eLayout && m3eSearchQuery.isNotBlank()) uiState.searchResults else uiState.moduleList

            ModuleList(
                bottomInnerPadding = bottomInnerPadding,
                modifier = if (m3eLayout) {
                    Modifier.nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
                listState = listState,
                displayModules = displayModules,
                updateInfoMap = uiState.updateInfo,
                actions = actions,
                onModuleAddShortcut = { module, type -> onModuleAddShortcut(module, type) },
                onModuleLongPress = { bannerDialogModule = it },
            )
        }
    }

    ModuleSortSheet(
        show = showSortSheet,
        groups = uiState.sortGroups,
        enabledFirst = uiState.sortEnabledFirst,
        customOrder = uiState.customOrder,
        displayModules = uiState.moduleList,
        onDismiss = { showSortSheet = false },
        onGroupChange = actions.onSetSortGroup,
        onEnabledFirstChange = actions.onSetSortEnabledFirst,
        onCustomOrderChange = actions.onSetCustomOrder,
        onResetCustomOrder = actions.onResetCustomOrder,
    )

    ModuleShortcutSheet(
        show = showShortcutDialog.value,
        shortcutState = shortcutState,
        onDismiss = { showShortcutDialog.value = false },
        onPickShortcutIcon = { pickShortcutIconLauncher.launch("image/*") },
        onDeleteShortcut = {
            shortcutState.deleteShortcut(context)
            showShortcutDialog.value = false
        },
        onConfirmShortcut = {
            shortcutState.createShortcut(context)
            showShortcutDialog.value = false
        },
    )

    bannerDialogModule?.let { module ->
        CardBannerDialog(
            subject = CardSubject(
                id = module.id,
                name = module.name,
                version = module.version,
                author = module.author,
                description = module.description,
            ),
            onDismiss = { bannerDialogModule = null },
        )
    }
}

@Composable
private fun ModuleList(
    bottomInnerPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    displayModules: List<Module>,
    updateInfoMap: Map<String, ModuleUpdateInfo>,
    actions: ModuleActions,
    onModuleAddShortcut: (Module, ShortcutType) -> Unit,
    onModuleLongPress: (Module) -> Unit,
    closeSearch: () -> Unit? = {},
) {
    val loadingDialog = rememberLoadingDialog()
    val spliced = ModuleCardConfig.splicedCardGroup
    var expandedModuleId by rememberSaveable { mutableStateOf<String?>(null) }

    val itemCard: @Composable (Module) -> Unit = { module ->
        val scope = rememberCoroutineScope()
        val moduleUpdateInfo = updateInfoMap[module.id] ?: ModuleUpdateInfo.Empty

        ModuleItemCard(
            module = module,
            visual = rememberModuleCardVisual(module.id),
            updateUrl = moduleUpdateInfo.downloadUrl,
            buttonStyle = ModuleButtonStyle.fromKey(ModuleCardConfig.buttonStyle),
            showMoreInfo = ModuleCardConfig.showMoreInfo,
            foldSystemModule = ModuleCardConfig.foldSystemModule,
            expanded = expandedModuleId == module.id,
            onExpandToggle = {
                expandedModuleId = if (expandedModuleId == module.id) null else module.id
            },
            onUninstallClicked = {
                if (module.remove) {
                    actions.onUndoUninstallModule(module)
                } else {
                    actions.onRequestUninstallConfirmation(module)
                }
            },
            onCheckChanged = {
                actions.onToggleModule(module)
            },
            onUpdate = {
                scope.launch {
                    loadingDialog.withLoading {
                        actions.onRequestUpdateConfirmation(module, moduleUpdateInfo)
                    }
                }
            },
            onAddShortcut = { type -> onModuleAddShortcut(module, type) },
            onOpenWebUi = {
                if (module.hasWebUi) {
                    actions.onOpenWebUi(module)
                }
            },
            onExecuteAction = { actions.onExecuteModuleAction(module) },
            onLongPress = { onModuleLongPress(module) },
            closeSearch = { closeSearch() }
        )
    }

    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600

    if (isWideScreen) {
        // Wide screens get two independent columns; cards keep their order and folding one does
        // not shift the other.
        TwoColumnGrid(
            items = displayModules,
            key = { module -> module.id },
            modifier = modifier,
            verticalSpacing = 16.dp,
            horizontalSpacing = 16.dp,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp + bottomInnerPadding + 56.dp + 16.dp,
            ),
        ) { module -> itemCard(module) }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp + bottomInnerPadding + 56.dp + 16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        if (spliced) {
            splicedLazyColumnGroup(
                items = displayModules,
                key = { _, module -> module.id },
                contentType = { _, _ -> "module" },
            ) { _, module -> itemCard(module) }
        } else {
            itemsIndexed(
                items = displayModules,
                key = { _, module -> module.id },
                contentType = { _, _ -> "module" },
            ) { _, module ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    itemCard(module)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ModuleShortcutSheet(
    show: Boolean,
    shortcutState: ModuleShortcutState,
    onDismiss: () -> Unit,
    onPickShortcutIcon: () -> Unit,
    onDeleteShortcut: () -> Unit,
    onConfirmShortcut: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val resources = LocalResources.current

    fun copyShortcutUrl() {
        val url = shortcutState.buildShortcutUrl() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KernelSU deep link", url))
        Toast.makeText(context, resources.getString(R.string.module_shortcut_scheme_copied), Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.module_shortcut_title),
                style = MaterialTheme.typography.titleLarge
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 13.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(25.dp))
            ) {
                val preview = shortcutState.previewIcon
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        modifier = Modifier.size(100.dp),
                        contentDescription = null,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.White)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = FixedScale(1.5f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolkTextButton(
                    onClick = onPickShortcutIcon,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.module_shortcut_icon_pick)
                    )
                }
                AnimatedVisibility(
                    visible = shortcutState.iconUri != shortcutState.defaultShortcutIconUri,
                    enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                    exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    FolkIconButton(
                        onClick = shortcutState::resetIconToDefault,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = shortcutState.name,
                onValueChange = shortcutState::updateName,
                label = { Text(stringResource(id = R.string.module_shortcut_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp)
            )
            if (shortcutState.hasExistingShortcut) {
                FolkTextButton(
                    onClick = onDeleteShortcut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                ) {
                    Text(stringResource(id = R.string.module_shortcut_delete))
                }
            }
            FolkTextButton(
                onClick = ::copyShortcutUrl,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            ) {
                Text(stringResource(id = R.string.module_shortcut_copy_scheme))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FolkOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = android.R.string.cancel))
                }
                FolkButton(
                    onClick = onConfirmShortcut,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (shortcutState.hasExistingShortcut) {
                            stringResource(id = R.string.module_update)
                        } else {
                            stringResource(id = android.R.string.ok)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CombinedClickableButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .semantics { role = Role.Button }
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        border = border,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                Modifier
                    .defaultMinSize(
                        minWidth = ButtonDefaults.MinWidth,
                        minHeight = ButtonDefaults.MinHeight,
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}
