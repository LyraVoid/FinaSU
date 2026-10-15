package me.weishu.kernelsu.ui.screen.plugin

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.LayoutVariant
import me.weishu.kernelsu.data.model.Plugin
import me.weishu.kernelsu.data.model.PluginConfigField
import me.weishu.kernelsu.modulebanner.ModuleCardConfig
import me.weishu.kernelsu.modulebanner.PluginBannerSlot
import me.weishu.kernelsu.ui.LocalLayoutVariant
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.bottombar.LocalFloatingBarReservedHeight
import me.weishu.kernelsu.ui.component.bottombar.LocalFloatingBarVisible
import me.weishu.kernelsu.ui.component.bottombar.LocalIsFloatingNavMode
import me.weishu.kernelsu.ui.component.card.CardBannerDialog
import me.weishu.kernelsu.ui.component.card.CardSubject
import me.weishu.kernelsu.ui.component.material.ExpressiveDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import me.weishu.kernelsu.ui.component.material.FolkFilledTonalButton
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.FolkSmallExtendedFloatingActionButton
import me.weishu.kernelsu.ui.component.material.FolkTextButton
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.rememberSearchAppBarScrollBehavior
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItemContainer
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.module.TwoColumnGrid
import me.weishu.kernelsu.ui.component.module.splicedLazyColumnGroup
import me.weishu.kernelsu.ui.screen.module.ExpandableDescriptionText
import me.weishu.kernelsu.ui.screen.module.ModuleButtonStyle
import me.weishu.kernelsu.ui.screen.module.rememberModuleCardVisual
import me.weishu.kernelsu.ui.theme.LocalModuleDescriptionMaxLines

@Composable
fun PluginPagerMaterial(
    uiState: PluginUiState,
    actions: PluginActions,
    bottomInnerPadding: Dp,
) {
    val snackBarHost = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    var searchText by rememberSaveable { mutableStateOf("") }

    val m3eLayout = LocalLayoutVariant.current == LayoutVariant.M3E
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val searchBarScrollBehavior = rememberSearchAppBarScrollBehavior(topAppBarScrollBehavior)
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

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackBarHost.showSnackbar(message)
        actions.onErrorShown()
    }

    val notice = uiState.notice?.let { stringResource(it) }
    LaunchedEffect(uiState.notice) {
        val message = notice ?: return@LaunchedEffect
        snackBarHost.showSnackbar(message)
        actions.onNoticeShown()
    }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                largeTitle = m3eLayout,
                topSearch = !m3eLayout,
                inlineSearch = m3eLayout,
                inlineSearchHint = stringResource(R.string.plugin_search_hint),
                scrollBehavior = if (m3eLayout) searchBarScrollBehavior else null,
                title = { Text(stringResource(R.string.plugin)) },
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                onClearClick = { searchText = "" },
                snackbarHostState = snackBarHost,
                navigationIcon = {
                    FolkIconButton(onClick = actions.onOpenStore) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = stringResource(R.string.plugin_store),
                        )
                    }
                },
                searchContent = { bottomPadding, closeSearch ->
                    val results = remember(searchText, uiState.plugins) {
                        uiState.plugins.matching(searchText)
                    }
                    PluginList(
                        bottomInnerPadding = bottomPadding,
                        modifier = Modifier.fillMaxSize(),
                        listState = searchListState,
                        displayPlugins = results,
                        pending = uiState.pending,
                        actions = actions,
                        closeSearch = closeSearch,
                    )
                },
            )
        },
        floatingActionButton = {
            val installLabel = stringResource(id = R.string.install)
            val packagePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    actions.onInstallZip(uri)
                }
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
                        packagePicker.launch("application/zip")
                    },
                    icon = { Icon(Icons.Filled.Add, installLabel) },
                    text = { Text(text = installLabel) },
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = {
            SnackBarHost(
                hostState = snackBarHost,
                modifier = Modifier.padding(bottom = bottomInnerPadding),
            )
        },
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
            val latestPlugins = rememberUpdatedState(uiState.plugins)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                listState,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestPlugins.value }
            PluginList(
                bottomInnerPadding = bottomInnerPadding,
                modifier = if (m3eLayout) {
                    Modifier.nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
                listState = listState,
                displayPlugins = if (m3eLayout && searchText.isNotBlank()) {
                    uiState.plugins.matching(searchText)
                } else {
                    uiState.plugins
                },
                pending = uiState.pending,
                actions = actions,
            )
        }
    }

    PluginLogDialog(uiState = uiState, actions = actions)
    PluginUninstallDialog(uiState = uiState, actions = actions)
    PluginConfigDialog(uiState = uiState, actions = actions)
}

@Composable
private fun PluginList(
    bottomInnerPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    displayPlugins: List<Plugin>,
    pending: Set<String>,
    actions: PluginActions,
    closeSearch: () -> Unit = {},
) {
    val spliced = ModuleCardConfig.splicedCardGroup
    var expandedPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var bannerDialogPlugin by remember { mutableStateOf<Plugin?>(null) }

    val itemCard: @Composable (Plugin) -> Unit = { plugin ->
        PluginItemCard(
            plugin = plugin,
            visual = rememberModuleCardVisual(plugin.id, PluginBannerSlot),
            pending = plugin.id in pending,
            expanded = expandedPluginId == plugin.id,
            onExpandToggle = {
                expandedPluginId = if (expandedPluginId == plugin.id) null else plugin.id
            },
            actions = actions,
            onLongPress = { bannerDialogPlugin = plugin },
            closeSearch = closeSearch,
        )
    }

    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    if (isWideScreen) {
        // Wide screens get two independent columns; cards keep their order and folding one does
        // not shift the other.
        TwoColumnGrid(
            items = displayPlugins,
            key = { plugin -> plugin.id },
            modifier = modifier,
            verticalSpacing = 16.dp,
            horizontalSpacing = 16.dp,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp + bottomInnerPadding + 56.dp + 16.dp,
            ),
        ) { plugin -> itemCard(plugin) }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(
                bottom = 16.dp + bottomInnerPadding + 56.dp + 16.dp,
            ),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (spliced) {
                splicedLazyColumnGroup(
                    items = displayPlugins,
                    key = { _, plugin -> plugin.id },
                    contentType = { _, _ -> "plugin" },
                ) { _, plugin -> itemCard(plugin) }
            } else {
                itemsIndexed(
                    items = displayPlugins,
                    key = { _, plugin -> plugin.id },
                    contentType = { _, _ -> "plugin" },
                ) { _, plugin ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        itemCard(plugin)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    bannerDialogPlugin?.let { plugin ->
        CardBannerDialog(
            subject = CardSubject(
                id = plugin.id,
                name = plugin.name,
                version = plugin.version,
                author = plugin.author,
                description = plugin.description,
            ),
            slot = PluginBannerSlot,
            onDismiss = { bannerDialogPlugin = null },
        )
    }
}

@Composable
private fun PluginLogDialog(
    uiState: PluginUiState,
    actions: PluginActions,
) {
    val pluginId = uiState.logPlugin ?: return
    val emptyLog = stringResource(R.string.plugin_log_empty)

    ExpressiveDialog(
        onDismissRequest = actions.onDismissLog,
        title = { Text(pluginId) },
        text = {
            Text(
                text = uiState.logText.ifBlank { emptyLog },
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            FolkTextButton(onClick = actions.onDismissLog) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun PluginUninstallDialog(
    uiState: PluginUiState,
    actions: PluginActions,
) {
    val plugin = uiState.confirmUninstall ?: return

    ExpressiveDialog(
        onDismissRequest = actions.onDismissUninstall,
        title = { Text(stringResource(R.string.uninstall)) },
        text = {
            Text(
                text = stringResource(R.string.plugin_uninstall_confirm).format(
                    plugin.name.ifBlank { plugin.id }
                ),
            )
        },
        confirmButton = {
            FolkTextButton(
                onClick = actions.onConfirmUninstall,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.uninstall))
            }
        },
        dismissButton = {
            FolkTextButton(onClick = actions.onDismissUninstall) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun PluginConfigDialog(
    uiState: PluginUiState,
    actions: PluginActions,
) {
    val plugin = uiState.configPlugin ?: return
    val fields = plugin.config
    if (fields.isEmpty()) return

    ExpressiveDialog(
        onDismissRequest = actions.onDismissConfig,
        title = { Text(plugin.name.ifBlank { plugin.id }) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val rows: List<@Composable () -> Unit> = fields.map { field ->
                    {
                        PluginConfigRow(
                            field = field,
                            value = uiState.configValues[field.key] ?: field.default.orEmpty(),
                            enabled = plugin.id !in uiState.pending,
                            onValueChange = { value ->
                                actions.onChangeConfigValue(field.key, value)
                            },
                            onReset = { actions.onResetConfigValue(field.key) },
                        )
                    }
                }
                SegmentedColumn(content = rows)
            }
        },
        confirmButton = {
            FolkTextButton(
                enabled = uiState.configValues != uiState.savedConfigValues ||
                    uiState.configDeleted.isNotEmpty(),
                onClick = actions.onSaveConfig,
            ) {
                Text(stringResource(R.string.plugin_save))
            }
        },
        dismissButton = {
            FolkTextButton(onClick = actions.onDismissConfig) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * One configuration row. A boolean is a switch, a declared list of options is a
 * menu, and any other value is edited in place.
 */
@Composable
private fun PluginConfigRow(
    field: PluginConfigField,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit,
) {
    val title = field.label.ifBlank { field.key }
    val summary = field.key.takeIf { field.label.isNotBlank() }
    val default = field.default.orEmpty()
    val resettable = field.default != null && value != default

    when {
        field.type.equals("bool", ignoreCase = true) -> SegmentedSwitchItem(
            title = title,
            summary = summary,
            checked = value.equals("true", ignoreCase = true),
            enabled = enabled,
            onCheckedChange = { onValueChange(it.toString()) },
        )

        field.options.isNotEmpty() -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                SegmentedListItem(
                    onClick = { expanded = true }.takeIf { enabled },
                    enabled = enabled,
                    headlineContent = { Text(title) },
                    supportingContent = summary?.let { { Text(it) } },
                    trailingContent = {
                        PluginConfigValue(value = value) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                    },
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    field.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        else -> SegmentedItemContainer {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConfigTextField(
                        value = value,
                        enabled = enabled,
                        numeric = field.type.equals("number", ignoreCase = true),
                        onValueChange = onValueChange,
                    )
                }
                if (resettable) {
                    val resetLabel = stringResource(R.string.plugin_config_reset)
                    IconButton(
                        modifier = Modifier.size(36.dp),
                        enabled = enabled,
                        onClick = onReset,
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = resetLabel,
                        )
                    }
                }
            }
        }
    }
}

/** An editable value with no visible box, so it keeps the grouped list look. */
@Composable
private fun ConfigTextField(
    value: String,
    enabled: Boolean,
    numeric: Boolean,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text(
                text = stringResource(R.string.plugin_config_unset),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun PluginConfigValue(value: String, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value.ifEmpty { stringResource(R.string.plugin_config_unset) },
            color = if (value.isEmpty()) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        trailing()
    }
}

private fun List<Plugin>.matching(query: String): List<Plugin> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { plugin ->
        plugin.id.contains(trimmed, ignoreCase = true) ||
            plugin.name.contains(trimmed, ignoreCase = true) ||
            plugin.description.contains(trimmed, ignoreCase = true)
    }
}
