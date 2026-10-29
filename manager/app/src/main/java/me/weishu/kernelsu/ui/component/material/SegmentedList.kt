package me.weishu.kernelsu.ui.component.material

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.navigation3.navOrigin
import me.weishu.kernelsu.ui.theme.FolkMotion
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole

val LocalListItemShapes = compositionLocalOf<ListItemShapes?> { null }
private val SegmentedOuterRadius = 16.dp
private val SegmentedInnerRadius = 4.dp
private const val SegmentedSpringStiffness = 800f
private const val SegmentedSpringDamping = 0.9f

/**
 * True while a [SegmentedColumn] is drawing one continuous group material behind its rows. In
 * wallpaper mode a single fill is required: the [ListItemDefaults.SegmentedGap] between rows would
 * otherwise leak the wallpaper through every seam (they were invisible only because the old rows
 * sat on an opaque same-colored surface).
 */
private val LocalWallpaperGroupActive = staticCompositionLocalOf { false }

@Composable
private fun WallpaperGroupBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFolkWallpaperTokens.current
    if (tokens == null) {
        content()
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = tokens.group.fill,
                shape = RoundedCornerShape(SegmentedOuterRadius),
            )
    ) {
        CompositionLocalProvider(
            LocalWallpaperGroupActive provides true,
            LocalWallpaperMaterialRole provides WallpaperSurfaceRole.Group,
            LocalWallpaperSupportingColor provides tokens.group.supportingColor,
        ) {
            content()
        }
    }
}

@DslMarker
annotation class SegmentedColumnDsl

@Composable
private fun defaultSegmentedColors(): ListItemColors {
    // In wallpaper mode each row carries the group material itself, so the list reads as one
    // translucent group instead of sitting on a solid surface.
    val material = LocalFolkWallpaperTokens.current?.group
    if (material != null) {
        val inGroup = LocalWallpaperGroupActive.current
        return ListItemDefaults.segmentedColors(
            containerColor = if (inGroup) Color.Transparent else material.fill,
            contentColor = material.contentColor,
            disabledContainerColor = if (inGroup) Color.Transparent else material.fill,
            supportingContentColor = material.supportingColor,
        )
    }
    return ListItemDefaults.segmentedColors(
        containerColor = colorScheme.surfaceBright,
        disabledContainerColor = colorScheme.surfaceBright,
        supportingContentColor = colorScheme.onSurfaceVariant
    )
}

/**
 * The colours a row with a selection control draws with.
 *
 * In wallpaper mode the whole group is one material, so a selected row must not repaint a second
 * fill over it; the row's own control (checkbox or radio) already shows the state. Folding every
 * selected colour back onto its resting counterpart keeps the row flush with the group. Outside
 * wallpaper mode the stock M3 selected treatment is kept, so non-wallpaper rendering is unchanged.
 */
@Composable
private fun selectionRowColors(colors: ListItemColors): ListItemColors {
    if (LocalFolkWallpaperTokens.current == null) return colors
    return colors.copy(
        selectedContainerColor = colors.containerColor,
        selectedContentColor = colors.contentColor,
        selectedLeadingContentColor = colors.leadingContentColor,
        selectedTrailingContentColor = colors.trailingContentColor,
        selectedOverlineContentColor = colors.overlineContentColor,
        selectedSupportingContentColor = colors.supportingContentColor,
    )
}

@Composable
private fun defaultSingleSegmentedShape(index: Int, count: Int): ListItemShapes {
    val base = ListItemDefaults.segmentedShapes(index, count)
    if (LocalFolkWallpaperTokens.current != null) {
        // Wallpaper mode tiles standalone rows into one continuous group, so only the two ends stay
        // rounded; square inner corners let adjacent rows meet without a notch.
        val shape = RoundedCornerShape(
            topStart = if (index == 0) SegmentedOuterRadius else 0.dp,
            topEnd = if (index == 0) SegmentedOuterRadius else 0.dp,
            bottomStart = if (index == count - 1) SegmentedOuterRadius else 0.dp,
            bottomEnd = if (index == count - 1) SegmentedOuterRadius else 0.dp,
        )
        return base.copy(shape = shape)
    }
    return if (count == 1) {
        base.copy(shape = MaterialTheme.shapes.large)
    } else {
        base
    }
}

@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    visibleLen: Int = 0,
    content: List<@Composable () -> Unit>,
) {
    if (content.isEmpty()) return

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        WallpaperGroupBackground {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                content.forEachIndexed { index, itemContent ->
                    CompositionLocalProvider(
                        LocalListItemShapes provides defaultSingleSegmentedShape(
                            index = index,
                            count = if (visibleLen > 0) visibleLen else content.size
                        ),
                    ) {
                        itemContent()
                    }
                }
            }
        }
    }
}

@SegmentedColumnDsl
class SegmentedColumnScope {
    internal data class Entry(
        val key: Any?,
        val visible: Boolean,
        val content: @Composable () -> Unit,
    )

    internal val entries = mutableListOf<Entry>()

    fun item(
        key: Any? = null,
        visible: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        entries.add(Entry(key ?: entries.size, visible, content))
    }
}

@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    content: SegmentedColumnScope.() -> Unit,
) {
    val entries = SegmentedColumnScope().apply(content).entries
    if (entries.isEmpty()) return

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        val dpSpring = spring<Dp>(SegmentedSpringDamping, SegmentedSpringStiffness)

        val progresses = entries.mapIndexed { index, entry ->
            key(entry.key ?: index) {
                animateFloatAsState(
                    targetValue = if (entry.visible) 1f else 0f,
                    animationSpec = FolkMotion.smoothSpring(),
                    label = "SegmentedProgress"
                )
            }
        }

        // Read through updated state so the gesture handler below never works from a stale frame.
        val visibilityState = rememberUpdatedState(entries.map { it.visible })
        val progressesState = rememberUpdatedState(progresses)

        val firstVisible = entries.indexOfFirst { it.visible }
        val lastVisible = entries.indexOfLast { it.visible }

        WallpaperGroupBackground {
            Layout(
                content = {
                    entries.forEachIndexed { index, entry ->
                        key(entry.key ?: index) {
                            val isFirst = if (firstVisible == -1) index == 0 else index == firstVisible
                            val isLast = if (lastVisible == -1) index == entries.lastIndex else index == lastVisible

                            val topRadius by animateDpAsState(
                                if (isFirst) SegmentedOuterRadius else SegmentedInnerRadius,
                                dpSpring, label = "SegmentedTopRadius"
                            )
                            val bottomRadius by animateDpAsState(
                                if (isLast) SegmentedOuterRadius else SegmentedInnerRadius,
                                dpSpring, label = "SegmentedBottomRadius"
                            )
                            val gap by animateDpAsState(
                                if (isFirst) 0.dp else ListItemDefaults.SegmentedGap,
                                dpSpring, label = "SegmentedGap"
                            )

                            val shape = RoundedCornerShape(
                                topStart = topRadius, topEnd = topRadius,
                                bottomStart = bottomRadius, bottomEnd = bottomRadius
                            )

                            Box(
                                modifier = Modifier
                                    .zIndex(if (entry.visible) (entries.size - index).toFloat() else -index.toFloat())
                                    .pointerInput(entry.key ?: index) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(
                                                requireUnconsumed = false,
                                                pass = PointerEventPass.Initial,
                                            )
                                            val visibleNow = visibilityState.value.getOrNull(index) ?: false
                                            val progressNow = progressesState.value.getOrNull(index)?.value ?: 0f
                                            // Only a hidden row or one that is still opening can slide out from
                                            // under the finger; a settled row keeps the press it received, so a
                                            // press during the reveal animation is no longer silently dropped.
                                            val opening = progressNow != if (visibleNow) 1f else 0f
                                            if (!visibleNow || opening) {
                                                down.consume()
                                                do {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    event.changes.forEach { it.consume() }
                                                } while (event.changes.any { it.pressed })
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        val progress = progresses[index].value.coerceAtLeast(0f)
                                        clip = true
                                        this.shape = object : Shape {
                                            override fun createOutline(
                                                size: Size,
                                                layoutDirection: LayoutDirection,
                                                density: Density,
                                            ): Outline = Outline.Rectangle(Rect(0f, 0f, size.width, size.height * progress))
                                        }
                                        alpha = (progress * 1.5f).coerceIn(0f, 1f)
                                    }
                            ) {
                                CompositionLocalProvider(
                                    LocalListItemShapes provides ListItemDefaults.segmentedShapes(0, 1).copy(shape = shape)
                                ) {
                                    Column(modifier = Modifier.padding(top = gap.coerceAtLeast(0.dp))) {
                                        entry.content()
                                    }
                                }
                            }
                        }
                    }
                }
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints) }
                val positions = IntArray(placeables.size)
                var y = 0f
                placeables.forEachIndexed { index, placeable ->
                    positions[index] = y.roundToInt()
                    y += placeable.height * progresses[index].value.coerceAtLeast(0f)
                }
                layout(constraints.maxWidth, y.roundToInt().coerceAtLeast(0)) {
                    placeables.forEachIndexed { index, placeable ->
                        // Unplaced rows keep their state but cannot intercept touches on visible rows.
                        if (progresses[index].value > 0f) {
                            placeable.placeRelative(0, positions[index])
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedItem(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    val wallpaper = LocalFolkWallpaperTokens.current != null
    CompositionLocalProvider(
        LocalListItemShapes provides defaultSingleSegmentedShape(index, count),
    ) {
        if (!wallpaper) {
            content()
        } else {
            // The parent list spaces standalone rows by SegmentedGap, so report a height reduced by
            // that gap and let the row overflow into it. With square inner corners from
            // defaultSingleSegmentedShape this joins the rows into one continuous group.
            val gapPx = with(LocalDensity.current) { ListItemDefaults.SegmentedGap.roundToPx() }
            Box(
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, (placeable.height - gapPx).coerceAtLeast(0)) {
                        placeable.placeRelative(0, 0)
                    }
                }
            ) {
                content()
            }
        }
    }
}

@Composable
fun SegmentedItemContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1)
    if (LocalFolkWallpaperTokens.current != null) {
        FolkWallpaperSurface(
            role = WallpaperSurfaceRole.Group,
            shape = shapes.shape,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceBright,
        shape = shapes.shape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * M3's [SegmentedListItem] hard-codes its ripple and has no `indication` parameter to turn it off,
 * so a press reads through [folkPressScale] instead. The same [MutableInteractionSource] drives the
 * scale and the row's own clickable, and a transparent ripple configuration stands in for
 * `indication = null` on the row.
 */
@Composable
private fun SegmentedRow(
    modifier: Modifier,
    clickable: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    content: @Composable (Modifier, MutableInteractionSource) -> Unit,
) {
    val pressedSource = interactionSource ?: remember { MutableInteractionSource() }
    val rowModifier = if (clickable) modifier.folkPressScale(pressedSource, enabled) else modifier
    val rippleConfiguration = if (clickable) {
        RippleConfiguration(color = Color.Transparent)
    } else {
        LocalRippleConfiguration.current
    }
    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
        content(rowModifier, pressedSource)
    }
}

@Composable
fun SegmentedListItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    interactionSource: MutableInteractionSource? = null,
    headlineContent: @Composable () -> Unit,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val tokens = LocalFolkWallpaperTokens.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val originModifier = if (onClick != null) {
        modifier.navOrigin(
            cornerRadius = 12.dp,
            color = tokens?.group?.fill
                ?: colors.containerColor.takeIf { it.alpha > 0f }
                ?: MaterialTheme.colorScheme.surfaceContainer,
            interactionSource = source,
        )
    } else {
        modifier
    }
    SegmentedRow(
        modifier = originModifier,
        clickable = onClick != null,
        enabled = enabled,
        interactionSource = source,
    ) { rowModifier, source ->
        SegmentedListItem(
            onClick = onClick ?: {},
            onLongClick = onLongClick,
            enabled = enabled,
            colors = colors,
            interactionSource = source,
            shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
            modifier = rowModifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            verticalAlignment = Alignment.CenterVertically,
            content = headlineContent
        )
    }
}

@Composable
fun SegmentedListItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    interactionSource: MutableInteractionSource? = null,
    headlineContent: @Composable () -> Unit,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    SegmentedRow(
        modifier = modifier,
        clickable = true,
        enabled = enabled,
        interactionSource = interactionSource,
    ) { rowModifier, source ->
        SegmentedListItem(
            checked = checked,
            onCheckedChange = onCheckedChange,
            shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
            modifier = rowModifier,
            enabled = enabled,
            colors = selectionRowColors(colors),
            interactionSource = source,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            verticalAlignment = Alignment.CenterVertically,
            onLongClick = onLongClick,
            content = headlineContent
        )
    }
}

@Composable
fun SegmentedListItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    interactionSource: MutableInteractionSource? = null,
    headlineContent: @Composable () -> Unit,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val tokens = LocalFolkWallpaperTokens.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    SegmentedRow(
        modifier = modifier.navOrigin(
            cornerRadius = 12.dp,
            color = tokens?.group?.fill
                ?: colors.containerColor.takeIf { it.alpha > 0f }
                ?: MaterialTheme.colorScheme.surfaceContainer,
            interactionSource = source,
        ),
        clickable = true,
        enabled = enabled,
        interactionSource = source,
    ) { rowModifier, source ->
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
            modifier = rowModifier,
            enabled = enabled,
            colors = selectionRowColors(colors),
            interactionSource = source,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            verticalAlignment = Alignment.CenterVertically,
            onLongClick = onLongClick,
            content = headlineContent
        )
    }
}

// Three touch slops reject long slides while allowing slow horizontal drift during an ordinary tap.
// Distance alone decides which gestures are drags; a speed limit also rejected genuine quick taps.
private const val SlideTravelMultiplier = 3f

@Composable
fun SegmentedSwitchItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    checked: Boolean,
    enabled: Boolean = true,
    requireStationaryTap: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val dragged = remember { mutableStateOf(false) }
    // Watching without consuming lets vertical drags scroll even when a row rejects the click.
    val guard = if (requireStationaryTap) {
        Modifier.pointerInput(enabled, title) {
            val slideSlop = viewConfiguration.touchSlop * SlideTravelMultiplier
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var pull = 0f
                var vertical = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.position - down.position
                    pull = maxOf(pull, delta.getDistance())
                    vertical = maxOf(vertical, abs(delta.y))
                    if (pull > slideSlop || vertical > viewConfiguration.touchSlop) {
                        dragged.value = true
                    }
                    if (!change.pressed) break
                }
                // The Final pass follows click handling, so a rejected gesture cannot swallow the next tap.
                dragged.value = false
            }
        }
    } else {
        Modifier
    }

    SegmentedListItem(
        onClick = {
            if (!(requireStationaryTap && dragged.value)) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onCheckedChange(!checked)
            }
        },
        modifier = guard,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        headlineContent = { Text(title) },
        leadingContent = icon?.let { { Icon(it, title) } },
        trailingContent = {
            ExpressiveSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

/**
 * Rounds [value] to the whole percent a percent slider's label shows, so the stored setting and the
 * label can never drift apart and a drag never writes float noise.
 */
fun snapToPercent(value: Float): Float = (value * 100f).roundToInt() / 100f

/**
 * Commits a [value] that has stopped changing, and flushes whatever is still pending on the way out.
 *
 * A surrounding scroll container can take a gesture over mid-drag, in which case the slider never
 * reports that it finished. A short debounce still commits the value the user settled on, and
 * leaving the composition writes a change that never settled at all — the video/image rows that
 * gate a slider are toggled off or the page is popped mid-gesture. A value that never moved, which
 * includes the one the row opens with, is never written.
 */
@Composable
fun SliderCommitEffect(value: Float, commit: () -> Unit) {
    // Both are re-read at call time: the effects below outlive the composition they were started in.
    val currentCommit by rememberUpdatedState(commit)
    val currentValue by rememberUpdatedState(value)
    var committed by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value == committed) return@LaunchedEffect
        delay(400)
        currentCommit()
        committed = value
    }
    DisposableEffect(Unit) {
        onDispose { if (currentValue != committed) currentCommit() }
    }
}

/**
 * A settings row with a slider.
 *
 * Pass [onValueChange] when the caller owns the value (FolkPatch's shape): the thumb, the label and
 * the stored value then come from one place and can never disagree, so a gesture that never reports
 * its end cannot leave a stale value behind. Callers that only want a commit-on-release value leave
 * it unset and keep the local slider state.
 */
@Composable
fun SegmentedSliderItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    colors: ListItemColors = defaultSegmentedColors(),
    enabled: Boolean = true,
    discreteSteps: Int = 19,
    onValueChange: ((Float) -> Unit)? = null,
    onValueChangeFinished: (Float) -> Unit,
) {
    val hoisted = onValueChange != null
    val steps = if (SliderTrackConfig.isDiscrete) discreteSteps else 0
    val sliderState = rememberSliderState(value = value, steps = steps, trackRange = valueRange)

    SegmentedListItem(
        enabled = enabled,
        colors = colors,
        headlineContent = { Text(title) },
        leadingContent = icon?.let { { Icon(it, title) } },
        trailingContent = { Text(valueText(if (hoisted) value else sliderState.value)) },
        supportingContent = {
            Column {
                summary?.let { Text(it) }
                if (hoisted) {
                    // The caller owns both the thumb and label; no saved slider value may override it.
                    val state = remember(valueRange, steps) { SliderState(value = value, steps = steps, trackRange = valueRange) }
                    if (state.value != value) state.value = value
                    // A release normally reports itself; this covers the gesture the scroll container took over.
                    SliderCommitEffect(value) { onValueChangeFinished(state.value) }
                    Slider(
                        state = state,
                        onValueChange = onValueChange,
                        onValueChangeFinished = { onValueChangeFinished(state.value) },
                        enabled = enabled,
                        // Only a drag may move the value: a stationary press is cancelled here, before the
                        // slider's own tap handling can jump the thumb to an accidental touch.
                        modifier = Modifier.fillMaxWidth().pointerInput(title) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var dragged = false
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                                    if (!change.pressed && !dragged) change.consume()
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    )
                } else {
                    Slider(
                        state = sliderState,
                        onValueChangeFinished = { onValueChangeFinished(sliderState.value) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

@Composable
fun SegmentedDropdownItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    items: List<String>,
    colors: ListItemColors = defaultSegmentedColors(),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var anchorOffset by remember { mutableStateOf(IntOffset.Zero) }

    val hasItems = items.isNotEmpty()
    val safeIndex = if (hasItems) {
        selectedIndex.coerceIn(0, items.lastIndex)
    } else {
        -1
    }

    Box(modifier = Modifier.trackPressPosition { anchorOffset = it.round() }) {
        SegmentedListItem(
            onClick = if (enabled) {
                {
                    onClick?.invoke()
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    expanded = true
                }
            } else null,
            enabled = enabled,
            colors = colors,
            leadingContent = icon?.let { { Icon(it, title) } },
            headlineContent = { Text(text = title) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = {
                Text(
                    text = if (hasItems && safeIndex >= 0) items[safeIndex] else "",
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(0.3f),
                    color = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant
                )
            }
        )
        OffsetAnchoredExpressiveMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchorOffset = anchorOffset,
        ) {
            items.forEachIndexed { index, text ->
                SelectableDropdownMenuItem(
                    text = { Text(text) },
                    selected = index == safeIndex,
                    onClick = {
                        if (index in items.indices) {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onItemSelected(index)
                        }
                        expanded = false
                    },
                    shapes = MenuDefaults.itemShape(index = index, count = items.size),
                    selectedLeadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun SegmentedRadioItem(
    title: String,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val rowColors = selectionRowColors(colors)

    SegmentedListItem(
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        enabled = enabled,
        colors = rowColors,
        headlineContent = { Text(title) },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

@Composable
fun SegmentedCheckboxItem(
    title: String,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val rowColors = selectionRowColors(colors)

    SegmentedListItem(
        checked = checked,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(it)
        },
        enabled = enabled,
        colors = rowColors,
        interactionSource = interactionSource,
        headlineContent = { Text(title) },
        leadingContent = {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
                modifier = Modifier.size(24.dp)
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

@Composable
fun SegmentedTextField(
    modifier: Modifier = Modifier,
    label: String = "",
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    colors: ListItemColors = defaultSegmentedColors(),
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    cursorBrush: Brush = SolidColor(colorScheme.primary),
    placeholder: @Composable (() -> Unit)? = { Text("-") },
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    isError: Boolean = false
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    SegmentedListItem(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester),
        colors = colors,
        onClick = { focusRequester.requestFocus() },
        leadingContent = leadingContent,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        headlineContent = {
            Column {
                if (label.isNotEmpty()) {
                    Text(text = label, color = if (isError) colorScheme.error else colors.contentColor)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = textStyle.copy(
                        colors.supportingContentColor,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    ),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    visualTransformation = visualTransformation,
                    onTextLayout = onTextLayout,
                    interactionSource = interactionSource,
                    cursorBrush = cursorBrush,
                    decorationBox = { innerTextField ->
                        if (value.isEmpty() && placeholder != null) {
                            Box(contentAlignment = Alignment.CenterStart) {
                                CompositionLocalProvider(
                                    LocalContentColor provides colors.supportingContentColor
                                ) {
                                    ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                                        placeholder()
                                    }
                                }
                            }
                        }
                        innerTextField()
                    }
                )
            }
        }
    )
}
