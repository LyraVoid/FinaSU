package me.weishu.kernelsu.ui.screen.sui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemShapes
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SuiApp
import me.weishu.kernelsu.data.model.SuiMode
import me.weishu.kernelsu.data.sui.SuiAdbRootMode
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.viewmodel.SuiPermissionsUiState

@Immutable
data class SuiPermissionsActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onToggleShowOnlyShizukuApps: () -> Unit,
    val onToggleLegacyBinderCompat: () -> Unit,
    val onToggleKsuNoEscape: () -> Unit,
    val onSetAdbRootMode: (SuiAdbRootMode) -> Unit,
    val onSetDefaultMode: (SuiMode) -> Unit,
    val onOpenDefaultMode: () -> Unit,
    val onOpenApp: (SuiApp) -> Unit,
    val onEnableNotifications: () -> Unit,
)

/** The overflow menu holds Sui's own settings; the two choosers open as pages you can leave again. */
private enum class SuiMenuPage { MAIN, DEFAULT_PERMISSION, ADB_ROOT }

private const val MAIN_MENU_ITEM_COUNT = 5

/** The Sui permission list, built from the same material pieces as the superuser page. */
@Composable
fun SuiPermissionsMaterial(
    uiState: SuiPermissionsUiState,
    actions: SuiPermissionsActions,
    notificationsEnabled: Boolean,
) {
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage = when {
        uiState.error != null -> uiState.error
        uiState.loading -> stringResource(R.string.sui_permissions_loading)
        uiState.visible.isEmpty() -> stringResource(R.string.sui_permissions_empty)
        else -> null
    }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                largeTitle = true,
                topSearch = true,
                scrollBehavior = scrollBehavior,
                snackbarHostState = snackbarHostState,
                title = { Text(stringResource(R.string.sui_api_title)) },
                searchText = uiState.search,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    var page by remember { mutableStateOf(SuiMenuPage.MAIN) }

                    FolkIconButton(
                        onClick = {
                            page = SuiMenuPage.MAIN
                            showMenu = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.sui_permissions_filter),
                        )

                        DropdownMenuPopup(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            val hapticNow = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            }
                            when (page) {
                                SuiMenuPage.MAIN -> SuiMainMenu(
                                    uiState = uiState,
                                    actions = actions,
                                    haptic = hapticNow,
                                    onOpen = { page = it },
                                    onClose = { showMenu = false },
                                )

                                SuiMenuPage.DEFAULT_PERMISSION -> SuiChoiceMenu(
                                    titleRes = R.string.sui_batch_unconfigured,
                                    selectedFlags = uiState.defaultMode.flags,
                                    choices = listOf(
                                        R.string.sui_mode_root to SuiMode.ROOT.flags,
                                        R.string.sui_mode_shell to SuiMode.SHELL.flags,
                                        R.string.sui_mode_deny to SuiMode.DENY.flags,
                                        R.string.sui_mode_hide to SuiMode.HIDDEN.flags,
                                        R.string.sui_mode_ask to SuiMode.DEFAULT.flags,
                                    ),
                                    onBack = { page = SuiMenuPage.MAIN },
                                    onSelect = {
                                        actions.onSetDefaultMode(SuiMode.of(it))
                                        showMenu = false
                                    },
                                )

                                SuiMenuPage.ADB_ROOT -> SuiChoiceMenu(
                                    titleRes = R.string.sui_adb_root,
                                    selectedFlags = uiState.adbRootMode.flags,
                                    choices = listOf(
                                        R.string.sui_adb_root_off to SuiAdbRootMode.OFF.flags,
                                        R.string.sui_adb_root_once to SuiAdbRootMode.ONCE.flags,
                                        R.string.sui_adb_root_always to SuiAdbRootMode.ALWAYS.flags,
                                    ),
                                    onBack = { page = SuiMenuPage.MAIN },
                                    onSelect = {
                                        actions.onSetAdbRootMode(SuiAdbRootMode.of(it))
                                        showMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
                defaultContent = { bottomPadding, closeSearch ->
                    SuiAppSearchList(
                        apps = uiState.searchResults,
                        listState = searchListState,
                        bottomPadding = bottomPadding,
                        enabled = !uiState.busy,
                        onOpenApp = {
                            closeSearch()
                            actions.onOpenApp(it)
                        },
                    )
                },
                searchContent = { bottomPadding, closeSearch ->
                    SuiAppSearchList(
                        apps = uiState.searchResults,
                        listState = searchListState,
                        bottomPadding = bottomPadding,
                        enabled = !uiState.busy,
                        onOpenApp = {
                            closeSearch()
                            actions.onOpenApp(it)
                        },
                    )
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = uiState.refreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.refreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 0.dp,
                    bottom = 16.dp,
                ),
            ) {
                item(key = "sui_summary") {
                    SegmentedColumn {
                        item(key = "sui_default") {
                            SegmentedListItem(
                                leadingContent = { Icon(Icons.Filled.AdminPanelSettings, null) },
                                headlineContent = { Text(stringResource(R.string.sui_default_mode)) },
                                supportingContent = { Text(stringResource(R.string.sui_default_mode_summary)) },
                                trailingContent = {
                                    SuiModeTag(uiState.defaultMode, uiState.defaultMode.defaultLabelRes)
                                },
                                onClick = actions.onOpenDefaultMode,
                            )
                        }
                        item(key = "sui_hidden") {
                            SegmentedListItem(
                                leadingContent = { Icon(Icons.Filled.VisibilityOff, null) },
                                headlineContent = { Text(stringResource(R.string.sui_hidden_count)) },
                                supportingContent = {
                                    Text(
                                        uiState.hiddenCount?.toString()
                                            ?: stringResource(R.string.sui_management_unavailable),
                                    )
                                },
                            )
                        }
                        item(key = "sui_notifications", visible = !notificationsEnabled) {
                            SegmentedListItem(
                                leadingContent = { Icon(Icons.Filled.NotificationsOff, null) },
                                headlineContent = { Text(stringResource(R.string.sui_notifications_off)) },
                                supportingContent = {
                                    Text(stringResource(R.string.sui_notifications_off_summary))
                                },
                                onClick = actions.onEnableNotifications,
                            )
                        }
                    }
                }

                if (statusMessage != null) {
                    item(key = "sui_message") {
                        SegmentedColumn {
                            item {
                                SegmentedListItem(
                                    leadingContent = { Icon(Icons.Filled.Warning, null) },
                                    headlineContent = {
                                        Text(statusMessage, overflow = TextOverflow.Ellipsis)
                                    },
                                )
                            }
                        }
                    }
                }

                itemsIndexed(uiState.visible, key = { _, app -> app.uid }) { index, app ->
                    SegmentedItem(index = index, count = uiState.visible.size) {
                        SuiAppItem(
                            app = app,
                            enabled = !uiState.busy,
                            onClick = { actions.onOpenApp(app) },
                        )
                    }
                }
            }
        }
    }
}

/** Sui's own overflow menu, minus the entries that only open its project pages. */
@Composable
private fun SuiMainMenu(
    uiState: SuiPermissionsUiState,
    actions: SuiPermissionsActions,
    haptic: () -> Unit,
    onOpen: (SuiMenuPage) -> Unit,
    onClose: () -> Unit,
) {
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
        CheckableDropdownMenuItem(
            text = { Text(stringResource(R.string.sui_filter_shizuku_apps)) },
            checked = uiState.showOnlyShizukuApps,
            checkedLeadingIcon = { CheckIcon() },
            onCheckedChange = {
                haptic()
                actions.onToggleShowOnlyShizukuApps()
                onClose()
            },
            shapes = MenuDefaults.itemShape(index = 0, count = MAIN_MENU_ITEM_COUNT),
        )
        SuiSubmenuEntry(
            labelRes = R.string.sui_batch_unconfigured,
            shapes = MenuDefaults.itemShape(index = 1, count = MAIN_MENU_ITEM_COUNT),
            onClick = {
                haptic()
                onOpen(SuiMenuPage.DEFAULT_PERMISSION)
            },
        )
        CheckableDropdownMenuItem(
            text = { Text(stringResource(R.string.sui_legacy_compat)) },
            checked = uiState.legacyBinderCompat,
            checkedLeadingIcon = { CheckIcon() },
            onCheckedChange = {
                haptic()
                actions.onToggleLegacyBinderCompat()
                onClose()
            },
            shapes = MenuDefaults.itemShape(index = 2, count = MAIN_MENU_ITEM_COUNT),
        )
        CheckableDropdownMenuItem(
            text = { Text(stringResource(R.string.sui_ksu_no_escape)) },
            checked = uiState.ksuNoEscape,
            checkedLeadingIcon = { CheckIcon() },
            onCheckedChange = {
                haptic()
                actions.onToggleKsuNoEscape()
                onClose()
            },
            shapes = MenuDefaults.itemShape(index = 3, count = MAIN_MENU_ITEM_COUNT),
        )
        SuiSubmenuEntry(
            labelRes = R.string.sui_adb_root,
            shapes = MenuDefaults.itemShape(index = 4, count = MAIN_MENU_ITEM_COUNT),
            onClick = {
                haptic()
                onOpen(SuiMenuPage.ADB_ROOT)
            },
        )
    }
}

/** An entry that leads into another page of the menu, marked with a chevron. */
@Composable
private fun SuiSubmenuEntry(
    @StringRes labelRes: Int,
    shapes: MenuItemShapes,
    onClick: () -> Unit,
) {
    SelectableDropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        selected = false,
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        onClick = onClick,
        shapes = shapes,
    )
}

/** A page of choices, with the way back at the top so the menu is never a dead end. */
@Composable
private fun SuiChoiceMenu(
    @StringRes titleRes: Int,
    selectedFlags: Int,
    choices: List<Pair<Int, Int>>,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val itemCount = choices.size + 1
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
        SelectableDropdownMenuItem(
            text = { Text(stringResource(titleRes)) },
            selected = false,
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            },
            onClick = onBack,
            shapes = MenuDefaults.itemShape(index = 0, count = itemCount),
        )
        choices.forEachIndexed { index, (labelRes, flags) ->
            SelectableDropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                selected = flags == selectedFlags,
                selectedLeadingIcon = { CheckIcon() },
                onClick = { onSelect(flags) },
                shapes = MenuDefaults.itemShape(index = index + 1, count = itemCount),
            )
        }
    }
}

@Composable
private fun CheckIcon() {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
    )
}

@Composable
private fun SuiAppSearchList(
    apps: List<SuiApp>,
    listState: LazyListState,
    bottomPadding: Dp,
    enabled: Boolean,
    onOpenApp: (SuiApp) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            bottom = 16.dp + bottomPadding,
        ),
    ) {
        if (apps.isEmpty()) {
            item {
                SegmentedColumn {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.sui_permissions_empty)) },
                        )
                    }
                }
            }
        }
        itemsIndexed(apps, key = { _, app -> app.uid }) { index, app ->
            SegmentedItem(index = index, count = apps.size) {
                SuiAppItem(app = app, enabled = enabled, onClick = { onOpenApp(app) })
            }
        }
    }
}

@Composable
private fun SuiAppItem(
    app: SuiApp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val applicationInfo = app.applicationInfo
    SegmentedListItem(
        enabled = enabled,
        onClick = onClick,
        headlineContent = {
            Text(app.label, overflow = TextOverflow.Ellipsis, maxLines = 1)
        },
        supportingContent = {
            Text(app.packageName, overflow = TextOverflow.Ellipsis, maxLines = 1)
        },
        leadingContent = applicationInfo?.let {
            { AppIconImage(modifier = Modifier.size(48.dp), applicationInfo = it, label = app.label) }
        },
        trailingContent = { SuiModeTag(app.suiMode, app.suiMode.labelRes) },
    )
}

@Composable
private fun SuiModeTag(mode: SuiMode, @StringRes labelRes: Int) {
    val (background, content) = when (mode) {
        SuiMode.ROOT -> colorScheme.primary to colorScheme.onPrimary
        SuiMode.SHELL -> colorScheme.tertiary to colorScheme.onTertiary
        SuiMode.DENY -> colorScheme.errorContainer to colorScheme.onErrorContainer
        SuiMode.HIDDEN -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        SuiMode.DEFAULT -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
    }
    StatusTag(
        label = stringResource(labelRes),
        backgroundColor = background,
        contentColor = content,
    )
}

@get:StringRes
val SuiMode.labelRes: Int
    get() = when (this) {
        SuiMode.DEFAULT -> R.string.sui_mode_default
        SuiMode.ROOT -> R.string.sui_mode_root
        SuiMode.SHELL -> R.string.sui_mode_shell
        SuiMode.DENY -> R.string.sui_mode_deny
        SuiMode.HIDDEN -> R.string.sui_mode_hide
    }

/** Wording for the default permission, where "no rule" means Sui asks the user. */
@get:StringRes
val SuiMode.defaultLabelRes: Int
    get() = if (this == SuiMode.DEFAULT) R.string.sui_mode_ask else labelRes
