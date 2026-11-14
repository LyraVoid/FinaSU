package me.weishu.kernelsu.ui.component.material

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.animation.FolkAnimatedSlot
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

/** Height reserved for the large search bar, used as the scroll range of its collapse animation. */
private val SearchBarExpandedHeight = 69.dp

@Composable
fun SearchAppBar(
    title: @Composable () -> Unit,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClearClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    largeTitle: Boolean = true,
    topSearch: Boolean = false,
    searchBeforeActions: Boolean = false,
    collapsibleSearch: Boolean = false,
    searchPlaceholder: String = "",
    defaultContent: @Composable BoxScope.(bottomPadding: Dp, closeSearch: () -> Unit) -> Unit = { _, _ -> },
    searchContent: @Composable BoxScope.(bottomPadding: Dp, closeSearch: () -> Unit) -> Unit = { _, _ -> }
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scaledDensity = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    val scope = rememberCoroutineScope()
    val searchBarState = rememberContainedSearchBarState()
    val textFieldState = rememberTextFieldState()
    val currentQuery = textFieldState.text.toString()
    val latestSearchText by rememberUpdatedState(searchText)
    val isSearchExpanded = searchBarState.currentValue != SearchBarValue.Collapsed || searchBarState.targetValue != SearchBarValue.Collapsed
    // A collapsible search bar only makes sense below a large title, and it needs the wrapper
    // scroll behavior to know how far it has been tucked away.
    val searchScrollBehavior = (scrollBehavior as? SearchAppBarScrollBehavior)
        ?.takeIf { collapsibleSearch && largeTitle }
    val searchBarFraction = searchScrollBehavior?.searchBarExpandedFraction ?: 1f
    val isSearchBarCollapsed = searchBarFraction <= 0.01f
    val isSearchBarCollapsing = searchScrollBehavior != null && searchBarFraction < 0.99f

    // Folding the search bar away also gives up the focus and the keyboard, otherwise the text
    // field would keep them while the bar is scrolled out of sight.
    LaunchedEffect(isSearchBarCollapsing) {
        if (isSearchBarCollapsing) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    val searchIcon: @Composable () -> Unit = {
        if (topSearch) {
            FolkIconButton(
                onClick = { scope.launch { searchBarState.animateToExpanded() } },
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
            }
        }
    }
    val appBarActions: @Composable RowScope.() -> Unit = {
        if (searchBeforeActions) searchIcon()
        actions?.invoke()
        if (!searchBeforeActions) searchIcon()
        // Once the large search bar has been tucked away, offer a small icon to bring it back.
        if (searchScrollBehavior != null) {
            FolkAnimatedSlot(visible = isSearchBarCollapsed) {
                FolkIconButton(
                    onClick = { scope.launch { searchScrollBehavior.expandSearchBar() } },
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            }
        }
    }
    var previousSearchBarValue by remember { mutableStateOf(searchBarState.currentValue) }
    var shouldClearOnCollapse by remember { mutableStateOf(true) }
    val clearSearchText: () -> Unit = {
        textFieldState.setTextAndPlaceCursorAtEnd("")
        onClearClick()
    }
    val collapseAndClear: () -> Unit = {
        shouldClearOnCollapse = false
        clearSearchText()
        scope.launch { searchBarState.animateToCollapsed() }
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(searchText) {
        val current = textFieldState.text.toString()
        if (current != searchText) {
            textFieldState.setTextAndPlaceCursorAtEnd(searchText)
        }
    }

    LaunchedEffect(textFieldState, latestSearchText) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { value ->
                if (value != latestSearchText) {
                    onSearchTextChange(value)
                }
            }
    }

    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.currentValue }
            .distinctUntilChanged()
            .collect { value ->
                val collapsedFromExpanded =
                    previousSearchBarValue != SearchBarValue.Collapsed && value == SearchBarValue.Collapsed
                previousSearchBarValue = value
                if (collapsedFromExpanded) {
                    if (shouldClearOnCollapse) {
                        clearSearchText()
                    }
                    shouldClearOnCollapse = true
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isSearchExpanded,
        onBackCompleted = {
            collapseAndClear()
        }
    )

    val inputField: @Composable () -> Unit = {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                placeholder = {
                    if (searchPlaceholder.isNotEmpty()) {
                        Text(searchPlaceholder)
                    }
                },
                leadingIcon = {
                    if (isSearchExpanded) {
                        FolkIconButton(
                            modifier = Modifier.padding(end = 8.dp),
                            onClick = { collapseAndClear() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                        }
                    } else {
                        Icon(Icons.Filled.Search, null)
                    }
                },
                trailingIcon = {
                    if (isSearchExpanded && currentQuery.isNotEmpty()) {
                        FolkIconButton(
                            onClick = { clearSearchText() },
                            content = { Icon(Icons.Filled.Close, null) }
                        )
                    }
                },
                interactionSource = interactionSource
            )
        }
    }

    // The scaffold's chrome zone already draws the reading platform, so in wallpaper mode this
    // container must stay transparent instead of painting an opaque band over the wallpaper.
    val wallpaperTokens = LocalFolkWallpaperTokens.current
    Surface(
        color = if (wallpaperTokens != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (largeTitle) {
                LargeFlexibleTopAppBar(
                    title = title,
                    colors = expressiveTopAppBarColors(),
                    navigationIcon = {
                        PreviewTopBarNavigation {
                            if (navigationIcon != null) navigationIcon()
                        }
                    },
                    actions = appBarActions,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior
                )
            } else {
                TopAppBar(
                    title = title,
                    colors = expressiveTopAppBarColors(),
                    navigationIcon = {
                        PreviewTopBarNavigation {
                            if (navigationIcon != null) navigationIcon()
                        }
                    },
                    actions = appBarActions,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
            }

            // A large title comes with a large search bar right below it. In the M3E layout the
            // bar belongs to the app bar area, so it folds away as the list scrolls and the app
            // bar collapses.
            val largeSearchBar: @Composable () -> Unit = {
                SearchBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 13.dp),

                    state = searchBarState,
                    inputField = inputField,
                    colors = SearchBarDefaults.colors(
                        containerColor = wallpaperTokens?.raisedOverChrome()
                            ?: MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                )
            }
            if (searchScrollBehavior != null) {
                AnimatedVisibility(
                    visible = !isSearchBarCollapsed,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(searchBarFraction)
                        .collapseWithTopAppBar(searchBarFraction),
                ) {
                    largeSearchBar()
                }
            } else if (!topSearch) {
                largeSearchBar()
            }
        }
    }

    // Tapping the large search bar opens its full screen search, with the recent items while the
    // query is empty and the results once something has been typed.
    ExpandedFullScreenContainedSearchBar(
        state = searchBarState,
        inputField = inputField,
        windowInsets = { SearchBarDefaults.fullScreenWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) },
        content = {
            val bottomPadding = SearchBarDefaults.fullScreenWindowInsets.asPaddingValues().calculateBottomPadding()
            Box(modifier = Modifier.fillMaxSize()) {
                if (currentQuery.isNotEmpty()) {
                    searchContent(bottomPadding, collapseAndClear)
                } else {
                    defaultContent(bottomPadding, collapseAndClear)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 16.dp)
                ) {
                    SnackBarHost(hostState = snackbarHostState)
                }
            }
        }
    )
}

/**
 * Wraps the app bar scroll behavior so that a large search bar can be tucked away while the
 * content scrolls down and brought back when it scrolls up, while the app bar itself keeps
 * following [topAppBarScrollBehavior].
 */
@Stable
class SearchAppBarScrollBehavior internal constructor(
    private val topAppBarScrollBehavior: TopAppBarScrollBehavior,
    private val searchBarHeight: Float,
) : TopAppBarScrollBehavior {
    private var searchBarHeightOffset by mutableFloatStateOf(0f)
    private var lastSearchBarScrollDelta = 0f
    private var isUserScrollInProgress = false
    private var ignoreCurrentScroll = false

    /** 1f while the large search bar is fully visible, 0f once it is completely tucked away. */
    val searchBarExpandedFraction: Float
        get() = (1f + searchBarHeightOffset / searchBarHeight).coerceIn(0f, 1f)

    override val state: TopAppBarState
        get() = topAppBarScrollBehavior.state

    override val isPinned: Boolean
        get() = topAppBarScrollBehavior.isPinned

    override val snapAnimationSpec: AnimationSpec<Float>?
        get() = topAppBarScrollBehavior.snapAnimationSpec

    override val flingAnimationSpec: DecayAnimationSpec<Float>?
        get() = topAppBarScrollBehavior.flingAnimationSpec

    /** Reveals the large search bar. */
    suspend fun expandSearchBar() {
        ignoreCurrentScroll = isUserScrollInProgress
        lastSearchBarScrollDelta = 0f
        animateSearchBarTo(0f)
    }

    /** Tucks the large search bar away. */
    fun collapseSearchBar() {
        searchBarHeightOffset = -searchBarHeight
        lastSearchBarScrollDelta = 0f
        isUserScrollInProgress = false
        ignoreCurrentScroll = false
    }

    fun reset() {
        searchBarHeightOffset = 0f
        lastSearchBarScrollDelta = 0f
        isUserScrollInProgress = false
        ignoreCurrentScroll = false
    }

    private fun consumeSearchBarScroll(delta: Float): Float {
        val previousOffset = searchBarHeightOffset
        searchBarHeightOffset = (previousOffset + delta).coerceIn(-searchBarHeight, 0f)
        val consumed = searchBarHeightOffset - previousOffset
        if (consumed != 0f) {
            lastSearchBarScrollDelta = consumed
        }
        return consumed
    }

    private suspend fun animateSearchBarTo(targetOffset: Float, initialVelocity: Float = 0f) {
        if (searchBarHeightOffset == targetOffset) return
        Animatable(searchBarHeightOffset).animateTo(
            targetValue = targetOffset,
            animationSpec = snapAnimationSpec ?: spring(),
            initialVelocity = initialVelocity,
        ) {
            searchBarHeightOffset = value.coerceIn(-searchBarHeight, 0f)
        }
        lastSearchBarScrollDelta = 0f
    }

    override val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                isUserScrollInProgress = true
            }
            // Scrolling back up, or a programmatic scroll: the app bar takes everything.
            if (ignoreCurrentScroll || available.y >= 0f) {
                return topAppBarScrollBehavior.nestedScrollConnection.onPreScroll(available, source)
            }
            // Scrolling down: the search bar folds away before the large title starts collapsing.
            val previousOffset = searchBarHeightOffset
            consumeSearchBarScroll(available.y)
            if (previousOffset != searchBarHeightOffset) {
                return available.copy(x = 0f)
            }
            return topAppBarScrollBehavior.nestedScrollConnection.onPreScroll(available, source)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val topAppBarConsumed =
                topAppBarScrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
            if (ignoreCurrentScroll || available.y <= 0f) {
                return topAppBarConsumed
            }
            // Scrolling back up: the app bar re-expands first, then the search bar slides back in
            // with whatever scroll is left over. Without this the large search bar stays tucked
            // away for good once it has been collapsed.
            val remaining = available.y - topAppBarConsumed.y
            return topAppBarConsumed + Offset(0f, consumeSearchBarScroll(remaining))
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (ignoreCurrentScroll) {
                return topAppBarScrollBehavior.nestedScrollConnection.onPreFling(available)
            }
            if (available.y < 0f && searchBarHeightOffset > -searchBarHeight) {
                animateSearchBarTo(-searchBarHeight, available.y)
                return available.copy(x = 0f)
            }
            return topAppBarScrollBehavior.nestedScrollConnection.onPreFling(available)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            isUserScrollInProgress = false
            if (ignoreCurrentScroll) {
                ignoreCurrentScroll = false
                return topAppBarScrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
            }
            if (searchBarHeightOffset > -searchBarHeight && searchBarHeightOffset < 0f) {
                val shouldExpand =
                    available.y > 0f || (available.y == 0f && lastSearchBarScrollDelta > 0f)
                animateSearchBarTo(if (shouldExpand) 0f else -searchBarHeight, available.y)
                return available.copy(x = 0f)
            }
            val topAppBarVelocity =
                topAppBarScrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
            val remaining = available.y - topAppBarVelocity.y
            if (remaining > 0f &&
                topAppBarScrollBehavior.state.collapsedFraction < 0.01f &&
                searchBarHeightOffset < 0f
            ) {
                animateSearchBarTo(0f, remaining)
                return topAppBarVelocity
            }
            return topAppBarVelocity
        }
    }
}

@Composable
fun rememberSearchAppBarScrollBehavior(
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
): SearchAppBarScrollBehavior {
    val density = LocalDensity.current
    return remember(topAppBarScrollBehavior, density) {
        SearchAppBarScrollBehavior(
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            searchBarHeight = with(density) { SearchBarExpandedHeight.toPx() },
        )
    }
}

/** Clips the content to [expandedFraction] of its height, keeping the bottom edge anchored. */
private fun Modifier.collapseWithTopAppBar(expandedFraction: Float): Modifier =
    this
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0))
            val visibleHeight = (placeable.height * expandedFraction.coerceIn(0f, 1f)).roundToInt()
            layout(placeable.width, visibleHeight) {
                placeable.placeRelative(x = 0, y = visibleHeight - placeable.height)
            }
        }
