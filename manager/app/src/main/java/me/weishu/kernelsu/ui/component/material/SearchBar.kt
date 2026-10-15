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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

/** Height reserved for the inline search bar, used as the scroll range of its collapse animation. */
private val SearchBarExpandedHeight = 77.dp

/** Height of the inline search pill itself. */
private val SearchBarPillHeight = 45.dp

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
    inlineSearch: Boolean = false,
    inlineSearchHint: String = "",
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
    val inlineScrollBehavior = scrollBehavior as? SearchAppBarScrollBehavior
    val useInlineSearch = inlineSearch && largeTitle && inlineScrollBehavior != null
    val inlineSearchFraction = if (useInlineSearch) inlineScrollBehavior.searchBarExpandedFraction else 1f
    val isInlineSearchCollapsed = useInlineSearch && inlineSearchFraction <= 0.01f
    var requestInlineSearchFocus by remember { mutableStateOf(false) }
    val appBarActions: @Composable RowScope.() -> Unit = {
        actions?.invoke()
        if (topSearch) {
            FolkIconButton(
                onClick = { scope.launch { searchBarState.animateToExpanded() } },
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
            }
        }
        // Once the inline search bar has been tucked away, offer a small icon to bring it back.
        if (useInlineSearch) {
            AnimatedVisibility(visible = isInlineSearchCollapsed, enter = fadeIn(), exit = fadeOut()) {
                FolkIconButton(
                    onClick = {
                        inlineScrollBehavior.expandSearchBar()
                        requestInlineSearchFocus = true
                    },
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
                    navigationIcon = { if (navigationIcon != null) navigationIcon() },
                    actions = appBarActions,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior
                )
            } else {
                TopAppBar(
                    title = title,
                    colors = expressiveTopAppBarColors(),
                    navigationIcon = { if (navigationIcon != null) navigationIcon() },
                    actions = appBarActions,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
            }

            // Large title plus a large inline search bar: it is part of the app bar area, so it
            // folds away as the list scrolls and the app bar collapses.
            if (useInlineSearch) {
                AnimatedVisibility(
                    visible = !isInlineSearchCollapsed,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(inlineSearchFraction)
                        .collapseWithTopAppBar(inlineSearchFraction),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        InlineSearchPill(
                            state = textFieldState,
                            hint = inlineSearchHint,
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp),
                            requestFocus = requestInlineSearchFocus,
                            onFocusRequestHandled = { requestInlineSearchFocus = false },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            if (!topSearch) {
                SearchBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 13.dp),

                    state = searchBarState,
                    inputField = inputField,
                    colors = SearchBarDefaults.colors(
                        containerColor = wallpaperTokens?.raised?.fill
                            ?: MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                )
            }
        }
    }

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

/** Large, pill shaped search field living right below the large title. */
@Composable
private fun InlineSearchPill(
    state: TextFieldState,
    hint: String,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    onFocusRequestHandled: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusRequestHandled()
        }
    }

    Surface(
        modifier = modifier.height(SearchBarPillHeight),
        shape = CircleShape,
        color = colorScheme.surfaceContainerHighest,
        contentColor = colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                if (state.text.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BasicTextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                    cursorBrush = SolidColor(colorScheme.primary),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        }
    }
}

/**
 * Wraps the app bar scroll behavior so that an inline search bar can be tucked away while the
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

    /** 1f while the inline search bar is fully visible, 0f once it is completely tucked away. */
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

    /** Reveals the inline search bar. */
    fun expandSearchBar() {
        ignoreCurrentScroll = isUserScrollInProgress
        lastSearchBarScrollDelta = 0f
        searchBarHeightOffset = 0f
    }

    /** Tucks the inline search bar away. */
    fun collapseSearchBar() {
        ignoreCurrentScroll = isUserScrollInProgress
        lastSearchBarScrollDelta = 0f
        searchBarHeightOffset = -searchBarHeight
    }

    fun reset() {
        ignoreCurrentScroll = false
        lastSearchBarScrollDelta = 0f
        searchBarHeightOffset = 0f
    }

    private fun consumeSearchBarScroll(delta: Float): Float {
        val previousOffset = searchBarHeightOffset
        searchBarHeightOffset = (previousOffset + delta).coerceIn(-searchBarHeight, 0f)
        val consumed = searchBarHeightOffset - previousOffset
        lastSearchBarScrollDelta = consumed
        return consumed
    }

    private suspend fun animateSearchBarTo(targetOffset: Float, initialVelocity: Float = 0f) {
        Animatable(searchBarHeightOffset).animateTo(
            targetValue = targetOffset,
            animationSpec = snapAnimationSpec ?: spring(),
            initialVelocity = initialVelocity,
        ) {
            searchBarHeightOffset = value.coerceIn(-searchBarHeight, 0f)
        }
    }

    override val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                isUserScrollInProgress = true
            }
            // Scrolling up, or a programmatic scroll: the app bar takes everything.
            if (ignoreCurrentScroll || available.y >= 0f) {
                return topAppBarScrollBehavior.nestedScrollConnection.onPreScroll(available, source)
            }
            val consumedBySearchBar = consumeSearchBarScroll(available.y)
            return if (consumedBySearchBar != 0f) {
                available.copy(x = 0f)
            } else {
                topAppBarScrollBehavior.nestedScrollConnection.onPreScroll(available, source)
            }
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val topAppBarConsumed =
                topAppBarScrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
            if (available.y >= 0f || ignoreCurrentScroll) {
                return topAppBarConsumed
            }
            val remaining = available.y - topAppBarConsumed.y
            if (remaining == 0f) {
                return topAppBarConsumed
            }
            return topAppBarConsumed + Offset(0f, consumeSearchBarScroll(remaining))
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return if (!ignoreCurrentScroll && available.y < 0f && searchBarHeightOffset > -searchBarHeight) {
                animateSearchBarTo(-searchBarHeight, available.y)
                available.copy(x = 0f)
            } else {
                topAppBarScrollBehavior.nestedScrollConnection.onPreFling(available)
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            isUserScrollInProgress = false
            if (ignoreCurrentScroll && available.y != 0f) {
                ignoreCurrentScroll = false
            }
            if (searchBarHeightOffset > -searchBarHeight && searchBarHeightOffset < 0f) {
                val topAppBarVelocity =
                    topAppBarScrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
                val shouldExpand =
                    available.y > 0f || (available.y == 0f && lastSearchBarScrollDelta > 0f)
                animateSearchBarTo(if (shouldExpand) 0f else -searchBarHeight)
                return available.copy(x = 0f)
            }
            val topAppBarVelocity =
                topAppBarScrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
            val remaining = available.y - topAppBarVelocity.y
            if (remaining > 0f &&
                topAppBarScrollBehavior.state.collapsedFraction < 0.01f &&
                searchBarHeightOffset < 0f
            ) {
                animateSearchBarTo(0f)
            }
            return available.copy(x = 0f)
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
