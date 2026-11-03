package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FloatingBarDrawer(
    destinations: List<BottomBarDestination>,
    selectedIndex: Int,
    badge: (BottomBarDestination) -> NavBadge?,
    onSelect: (BottomBarDestination) -> Unit,
    selectionColor: Color? = null,
    selectionContentColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    if (destinations.isEmpty()) return
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val selectedContainer = selectionColor ?: MaterialTheme.colorScheme.secondaryContainer
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }
    var previousIndex by remember { mutableStateOf(selectedIndex) }
    val travel = remember { Animatable(1f) }
    LaunchedEffect(selectedIndex) {
        if (previousIndex == selectedIndex) return@LaunchedEffect
        travel.snapTo(0f)
        travel.animateTo(1f, spatialSpec)
        previousIndex = selectedIndex
    }
    BoxWithConstraints(modifier = modifier) {
        val barWidth = (56.dp * destinations.size + 88.dp).coerceAtMost(maxWidth)
        val extraWidth = (barWidth - 16.dp - 4.dp * (destinations.size - 1) - 48.dp * destinations.size)
            .coerceAtLeast(0.dp)
        val openWeight = 1f + extraWidth / 48.dp
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { originInRoot = it.positionInRoot() }
                    .drawBehind {
                        val from = itemBounds[previousIndex] ?: return@drawBehind
                        val to = itemBounds[selectedIndex] ?: return@drawBehind
                        val pill = lerp(from, to, travel.value).translate(-originInRoot.x, -originInRoot.y)
                        drawRoundRect(
                            color = selectedContainer,
                            topLeft = pill.topLeft,
                            size = pill.size,
                            cornerRadius = CornerRadius(pill.height / 2f),
                        )
                    }
            )
            Row(
                modifier = Modifier.width(barWidth).height(64.dp).padding(8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, destination ->
                    key(destination) {
                        val selected = index == selectedIndex
                        val expansion by animateFloatAsState(if (selected) 1f else 0f, spatialSpec, label = "drawerExpansion")
                        val iconScale by animateFloatAsState(if (selected) 1f else 0.9f, spatialSpec, label = "drawerIconScale")
                        val tint = when {
                            !selected -> MaterialTheme.colorScheme.onSurfaceVariant
                            selectionContentColor != null -> selectionContentColor
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        val navBadge = badge(destination)
                        Row(
                            modifier = Modifier
                                .weight(1f + (openWeight - 1f) * expansion)
                                .height(44.dp)
                                .onGloballyPositioned { itemBounds[index] = it.boundsInRoot() }
                                .clip(CircleShape)
                                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (navBadge != null) {
                                BadgedBox(
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                    badge = { FloatingBadge(navBadge) },
                                ) {
                                    NavBarIcon(destination, selected, tint = tint, modifier = Modifier.size(24.dp))
                                }
                            } else {
                                NavBarIcon(
                                    destination = destination,
                                    isSelected = selected,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                        .size(24.dp),
                                    tint = tint,
                                )
                            }
                            if (expansion > 0f && extraWidth > 0.dp) {
                                Text(
                                    text = stringResource(destination.label),
                                    modifier = Modifier
                                        .padding(start = 8.dp * expansion)
                                        .graphicsLayer { alpha = expansion },
                                    color = tint,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
