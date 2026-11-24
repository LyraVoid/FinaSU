package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fletchmckee.liquid.LiquidState
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.MorphDigits
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

/** Vertical space the floating bar occupies from the screen bottom, excluding system insets. */
fun floatingBarReservedHeight(compact: Boolean, style: FloatingBarConfig.Style): Dp =
    (if (style == FloatingBarConfig.Style.Drawer) 64.dp else if (compact) 68.dp else 72.dp) + 44.dp

/**
 * Floating bottom navigation bar rendered above the page content.
 *
 * Two forms ([FloatingBarConfig.Style]): [Standard][FloatingBarStandard] capsule and the
 * expanding [Drawer][FloatingBarDrawer]. With [FloatingBarConfig.glass] the surface becomes a
 * frosted-glass panel (real-time blur on Android 13+) linked to [liquidState].
 */
@Composable
internal fun FloatingBar(
    navigationBadge: NavigationBadgeState,
    liquidState: LiquidState?,
    modifier: Modifier = Modifier,
    onUserInteraction: () -> Unit = {},
) {
    if (!useFullFeaturedLayout()) return
    val mainPagerState = LocalMainPagerState.current
    val revision by FloatingBarConfig.revision.collectAsStateWithLifecycle()
    val style = remember(revision) { FloatingBarConfig.style }
    val compact = remember(revision) { FloatingBarConfig.compact }
    val useGlass = remember(revision) { FloatingBarConfig.glass } && liquidState != null
    val barStyle = BottomBarControl.style(BottomBarLayout.Floating)
    val containerColor = barStyle.containerColor
    // A wallpaper selection fill fades with the surface it sits on; the liquid film has to be the
    // same colour, otherwise the lens below it is covered by an opaque pill again.
    val selectionColor = barStyle.indicatorColor
    val selectionContentColor = LocalFolkWallpaperTokens.current?.selection?.contentColor
    val glassBlurStrength = remember(revision) { FloatingBarConfig.glassBlurStrength }
    val glassTransparency = remember(revision) { FloatingBarConfig.glassTransparency }
    val glassHighlightStrength = remember(revision) { FloatingBarConfig.glassHighlightStrength }
    val glassSpecular = remember(revision) { FloatingBarConfig.glassSpecular }
    val glassInnerGlow = remember(revision) { FloatingBarConfig.glassInnerGlow }
    val glassBorder = remember(revision) { FloatingBarConfig.glassBorder }
    val wallpaperActive = LocalFolkWallpaperTokens.current != null
    val destinations = mainPagerState.destinations
    val selectedIndex = mainPagerState.selectedPage

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        val horizontalPadding = when {
            maxWidth > 600.dp -> 32.dp
            maxWidth > 400.dp -> 24.dp
            else -> 16.dp
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val barShape: Shape = when {
                style == FloatingBarConfig.Style.Drawer -> CircleShape
                compact -> RoundedCornerShape(percent = 50)
                useGlass -> CircleShape
                else -> MaterialTheme.shapes.large
            }
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .then(
                        if (useGlass) {
                            Modifier.clip(barShape).navBarGlassEffect(
                                shape = barShape,
                                blurStrength = glassBlurStrength,
                                transparency = glassTransparency,
                                highlightStrength = glassHighlightStrength,
                                enableSpecular = glassSpecular,
                                enableInnerGlow = glassInnerGlow,
                                enableBorder = glassBorder,
                                liquidState = liquidState,
                            )
                        } else {
                            Modifier
                        }
                    ),
                shape = barShape,
                color = if (useGlass) Color.Transparent else containerColor,
                // A translucent surface cannot carry a shadow: the shadow is painted behind a fill that
                // no longer hides it, so it reads as a dark halo around the capsule instead of a lift.
                tonalElevation = 0.dp,
                shadowElevation = if (useGlass || wallpaperActive) 0.dp else 3.dp,
            ) {
                if (style == FloatingBarConfig.Style.Drawer) {
                    FloatingBarDrawer(
                        destinations = destinations,
                        selectedIndex = selectedIndex,
                        badge = { badgeFor(it, navigationBadge) },
                        onSelect = {
                            if (it == mainPagerState.selectedDestination) return@FloatingBarDrawer
                            onUserInteraction()
                            mainPagerState.animateToDestination(it)
                            me.weishu.kernelsu.media.MediaFeedback.navigation()
                        },
                        selectionColor = selectionColor,
                        selectionContentColor = selectionContentColor,
                    )
                } else {
                    FloatingBarStandard(
                        destinations = destinations,
                        selectedIndex = selectedIndex,
                        badge = { badgeFor(it, navigationBadge) },
                        onSelect = {
                            onUserInteraction()
                            mainPagerState.animateToDestination(it)
                            me.weishu.kernelsu.media.MediaFeedback.navigation()
                        },
                        selectionColor = selectionColor,
                        selectionContentColor = selectionContentColor,
                    )
                }
                }
            CreditLine(Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
internal fun FloatingBadge(badge: NavBadge) {
    when (badge.tone) {
        BadgeTone.Alert -> Badge { MorphDigits(value = badge.count.toString()) }
        BadgeTone.Accent -> Badge(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { MorphDigits(value = badge.count.toString()) }
    }
}
