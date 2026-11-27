package me.weishu.kernelsu.ui.screen.nomount

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.Collator
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.LayoutVariant
import me.weishu.kernelsu.ui.LocalLayoutVariant
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.EmptyState
import me.weishu.kernelsu.ui.component.dialog.ConfirmResult
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.FolkSmallExtendedFloatingActionButton
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.rememberSearchAppBarScrollBehavior
import me.weishu.kernelsu.ui.component.material.rememberListFabExpanded
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.AppSortConfig
import me.weishu.kernelsu.ui.viewmodel.AppSortType
import me.weishu.kernelsu.ui.viewmodel.ExclusionApp
import me.weishu.kernelsu.ui.viewmodel.NomountViewModel

@Composable
fun NomountExclusionsScreen() {
    val viewModel = viewModel<NomountViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val profilesTitle = stringResource(R.string.nomount_follow_profiles)
    val profilesSummary = stringResource(R.string.nomount_follow_profiles_summary)
    val m3eLayout = LocalLayoutVariant.current == LayoutVariant.M3E
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val searchScrollBehavior = rememberSearchAppBarScrollBehavior(topAppBarScrollBehavior)

    var query by rememberSaveable { mutableStateOf("") }
    var sortValue by rememberSaveable { mutableStateOf(0) }
    var showSort by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    var importingProfiles by remember { mutableStateOf(false) }
    val savedSelection = remember(state.status?.uids) { state.status?.uids.orEmpty().toSet() }
    var draft by rememberSaveable { mutableStateOf<IntArray?>(null) }
    val selected = remember(draft, savedSelection) { draft?.toSet() ?: savedSelection }
    val canEdit = !state.isLoading && !state.readError && state.status != null &&
        !state.isWriting && !importingProfiles
    val sort = AppSortConfig.fromInt(sortValue)

    LaunchedEffect(Unit) { viewModel.loadApps() }
    LaunchedEffect(state.exclusionsSaved) {
        if (state.exclusionsSaved) {
            if (!state.readError) draft = null
            viewModel.consumeExclusionsSaved()
        }
    }
    BackHandler(enabled = state.isWriting || state.exclusionsSaved) {}

    val sortedApps = remember(state.apps, sort, savedSelection) { sortExclusionApps(state.apps, sort, savedSelection) }
    val visibleApps = remember(sortedApps, state.showSystemApps, selectedOnly, selected, query) {
        filterExclusionApps(sortedApps, query, state.showSystemApps, selectedOnly, selected)
    }
    val toggleApp: (Int, Boolean) -> Unit = { uid, checked ->
        val current = draft?.toSet() ?: savedSelection
        draft = (if (checked) current + uid else current - uid).toIntArray()
    }
    val visibleUids = remember(visibleApps) { visibleApps.mapTo(mutableSetOf()) { it.uid } }
    val selectAll: () -> Unit = { draft = (selected + visibleUids).toIntArray() }
    val invert: () -> Unit = { draft = invertVisibleExclusions(selected, visibleUids).toIntArray() }
    val clearVisible: () -> Unit = { draft = (selected - visibleUids).toIntArray() }
    val listState = rememberLazyListState()
    val fabExpanded by rememberListFabExpanded(listState)
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(query, sortValue, selectedOnly, state.showSystemApps) {
        if (listState.firstVisibleItemIndex != 0) listState.scrollToItem(0)
    }

    val showSave = canEdit && selected != savedSelection
    val topBarActions: @Composable () -> Unit = {
        FolkIconButton(onClick = { showSort = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.menu_sort))
            DropdownMenuPopup(expanded = showSort, onDismissRequest = { showSort = false }) {
                val sortEntries = listOf(
                    AppSortType.NAME to R.string.sort_by_name,
                    AppSortType.PACKAGE_NAME to R.string.sort_by_package_name,
                    AppSortType.INSTALL_TIME to R.string.sort_by_install_time,
                    AppSortType.UPDATE_TIME to R.string.sort_by_update_time,
                )
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                    sortEntries.forEachIndexed { index, (type, label) ->
                        SelectableDropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            selected = sort.sortType == type,
                            selectedLeadingIcon = { MenuCheck() },
                            onClick = {
                                sortValue = sort.withType(type).toInt()
                                showSort = false
                            },
                            shapes = MenuDefaults.itemShape(index, sortEntries.size),
                        )
                    }
                }
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                    listOf(false to R.string.sort_asc, true to R.string.sort_desc)
                        .forEachIndexed { index, (reversed, label) ->
                            SelectableDropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                selected = sort.reversed == reversed,
                                selectedLeadingIcon = { MenuCheck() },
                                onClick = {
                                    sortValue = sort.copy(reversed = reversed).toInt()
                                    showSort = false
                                },
                                shapes = MenuDefaults.itemShape(index, 2),
                            )
                        }
                }
            }
        }

        FolkIconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.settings))
            DropdownMenuPopup(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 3)) {
                    CheckableDropdownMenuItem(
                        text = { Text(stringResource(R.string.nomount_show_system_apps)) },
                        checked = state.showSystemApps,
                        checkedLeadingIcon = { MenuCheck() },
                        onCheckedChange = viewModel::setShowSystemApps,
                        shapes = MenuDefaults.itemShape(0, 2),
                    )
                    CheckableDropdownMenuItem(
                        text = { Text(stringResource(R.string.nomount_selected_only)) },
                        checked = selectedOnly,
                        checkedLeadingIcon = { MenuCheck() },
                        onCheckedChange = { selectedOnly = it },
                        shapes = MenuDefaults.itemShape(1, 2),
                    )
                }
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 3)) {
                    SelectableDropdownMenuItem(
                        text = { Text(stringResource(R.string.nomount_select_all)) },
                        leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
                        selected = false,
                        enabled = canEdit && visibleUids.any { it !in selected },
                        shapes = MenuDefaults.itemShape(0, 3),
                        onClick = {
                            showMenu = false
                            selectAll()
                        },
                    )
                    SelectableDropdownMenuItem(
                        text = { Text(stringResource(R.string.nomount_invert_selection)) },
                        leadingIcon = { Icon(Icons.Filled.SwapVert, null) },
                        selected = false,
                        enabled = canEdit && visibleUids.isNotEmpty(),
                        shapes = MenuDefaults.itemShape(1, 3),
                        onClick = {
                            showMenu = false
                            invert()
                        },
                    )
                    SelectableDropdownMenuItem(
                        text = { Text(stringResource(R.string.nomount_clear_selection)) },
                        leadingIcon = { Icon(Icons.Filled.Deselect, null) },
                        selected = false,
                        enabled = canEdit && visibleUids.any { it in selected },
                        shapes = MenuDefaults.itemShape(2, 3),
                        onClick = {
                            showMenu = false
                            clearVisible()
                        },
                    )
                }
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 2, count = 3)) {
                    SelectableDropdownMenuItem(
                        text = { Text(stringResource(R.string.nomount_refresh)) },
                        selected = false,
                        enabled = !state.appsLoading && !state.isLoading,
                        shapes = MenuDefaults.itemShape(0, 2),
                        onClick = {
                            showMenu = false
                            viewModel.refreshAll()
                        },
                    )
                    SelectableDropdownMenuItem(
                        text = { Text(profilesTitle) },
                        selected = false,
                        enabled = canEdit && !state.appsLoading,
                        shapes = MenuDefaults.itemShape(1, 2),
                        onClick = {
                            showMenu = false
                            scope.launch {
                                val result = confirm.awaitConfirm(title = profilesTitle, content = profilesSummary)
                                if (result is ConfirmResult.Confirmed) {
                                    importingProfiles = true
                                    try {
                                        val uids = viewModel.profileExcludedUids(state.showSystemApps)
                                        draft = ((draft?.toSet() ?: savedSelection) + uids).toIntArray()
                                    } finally {
                                        importingProfiles = false
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    val appContent: @Composable (Dp, LazyListState) -> Unit = { bottomPadding, scrollState ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.actionError) {
                Text(
                    stringResource(R.string.nomount_action_failed_summary),
                    Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when {
                state.readError -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.nomount_read_error),
                    modifier = Modifier.fillMaxSize(),
                )
                state.isLoading || (state.appsLoading && state.apps.isEmpty()) -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                visibleApps.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(
                        when {
                            selectedOnly && selected.isEmpty() -> R.string.nomount_no_selected_apps
                            query.isNotBlank() || selectedOnly -> R.string.nomount_no_matching_apps
                            else -> R.string.nomount_no_apps
                        },
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> ExclusionAppsList(visibleApps, selected, canEdit, scrollState, bottomPadding, toggleApp)
            }
        }
    }
    val searchListState = rememberLazyListState()
    LaunchedEffect(query) { searchListState.scrollToItem(0) }
    ExpressiveScaffold(
        topBar = {
            val title = @Composable { Text(stringResource(R.string.nomount_exclusions_title)) }
            val navigationIcon = @Composable {
                TopBarBackButton(
                    onClick = { if (!state.isWriting && !state.exclusionsSaved) navigator.pop() },
                )
            }
            SearchAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = topBarActions,
                searchText = query,
                onSearchTextChange = { query = it },
                onClearClick = { query = "" },
                snackbarHostState = remember { SnackbarHostState() },
                searchPlaceholder = stringResource(R.string.nomount_search_apps),
                largeTitle = m3eLayout,
                topSearch = !m3eLayout,
                collapsibleSearch = m3eLayout,
                scrollBehavior = if (m3eLayout) searchScrollBehavior else null,
                defaultContent = { padding, _ -> appContent(padding, searchListState) },
                searchContent = { padding, _ -> appContent(padding, searchListState) },
            )
        },
        floatingActionButton = {
            if (showSave) {
                FolkSmallExtendedFloatingActionButton(
                    onClick = { viewModel.saveExclusions(selected.toList()) },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.Done, if (fabExpanded) null else stringResource(R.string.nomount_save)) },
                    text = { Text(stringResource(R.string.nomount_save)) },
                )
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .then(
                    if (m3eLayout) Modifier.nestedScroll(searchScrollBehavior.nestedScrollConnection) else Modifier,
                ),
        ) {
            PullToRefreshBox(
                modifier = Modifier.fillMaxWidth().weight(1f),
                isRefreshing = state.appsLoading && state.apps.isNotEmpty(),
                onRefresh = viewModel::refreshAll,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.appsLoading && state.apps.isNotEmpty(),
                        state = pullToRefreshState,
                    )
                },
            ) {
                appContent(if (showSave) 72.dp else 0.dp, listState)
            }
        }
    }
}

internal fun sortExclusionApps(apps: List<ExclusionApp>, sort: AppSortConfig, saved: Set<Int>): List<ExclusionApp> {
    val primary: Comparator<ExclusionApp> = when (sort.sortType) {
        AppSortType.NAME -> compareBy(Collator.getInstance(), ExclusionApp::label)
        AppSortType.PACKAGE_NAME -> compareBy(ExclusionApp::packageName)
        AppSortType.INSTALL_TIME -> compareBy { it.packageInfo?.firstInstallTime ?: 0L }
        AppSortType.UPDATE_TIME -> compareBy { it.packageInfo?.lastUpdateTime ?: 0L }
    }
    val sorted = primary.thenBy(ExclusionApp::packageName).thenBy(ExclusionApp::uid)
    val secondary = if (sort.reversed) sorted.reversed() else sorted
    return apps.sortedWith(Comparator { a, b ->
        val rank = (a.uid in saved).compareTo(b.uid in saved)
        if (rank != 0) -rank else secondary.compare(a, b)
    })
}

internal fun filterExclusionApps(
    apps: List<ExclusionApp>,
    query: String,
    showSystemApps: Boolean,
    selectedOnly: Boolean,
    selected: Set<Int>,
): List<ExclusionApp> {
    val term = query.trim()
    return apps.filter { app ->
        val scopeOk = if (selectedOnly) app.uid in selected else showSystemApps || !app.isSystem
        scopeOk && (term.isEmpty() || app.matches(term))
    }
}

private fun ExclusionApp.matches(term: String): Boolean =
    label.contains(term, ignoreCase = true) || packageName.contains(term, ignoreCase = true)

internal fun invertVisibleExclusions(selected: Set<Int>, visible: Set<Int>): Set<Int> =
    (selected - visible) + (visible - selected)

@Composable
private fun MenuCheck() {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(MenuDefaults.LeadingIconSize))
}

@Composable
private fun ExclusionAppsList(
    apps: List<ExclusionApp>,
    selected: Set<Int>,
    canEdit: Boolean,
    listState: LazyListState,
    bottomPadding: Dp,
    onToggle: (Int, Boolean) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
    ) {
        itemsIndexed(apps, key = { _, app -> app.uid }) { index, app ->
            SegmentedItem(index = index, count = apps.size) {
                SegmentedListItem(
                    checked = app.uid in selected,
                    enabled = canEdit,
                    onCheckedChange = { onToggle(app.uid, it) },
                    headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            if (app.appCount > 1) {
                                stringResource(
                                    R.string.nomount_app_uid_grouped,
                                    app.packageName,
                                    app.uid,
                                    app.appCount,
                                )
                            } else {
                                stringResource(R.string.nomount_app_uid, app.packageName, app.uid)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        app.packageInfo?.let {
                            AppIconImage(packageInfo = it, label = app.label, modifier = Modifier.size(48.dp))
                        }
                    },
                    trailingContent = {
                        Checkbox(checked = app.uid in selected, onCheckedChange = null, enabled = canEdit)
                    },
                )
            }
        }
    }
}
