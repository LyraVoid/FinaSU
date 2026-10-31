package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.material.ChromeEdge
import me.weishu.kernelsu.ui.component.material.WallpaperChromeZone

@Composable
fun BottomBarMaterial(navigationBadge: NavigationBadgeState) {
    val fullFeatured = useFullFeaturedLayout()
    if (!fullFeatured) return

    val mainPagerState = LocalMainPagerState.current
    val visibilityRevision = NavBarVisibilityConfig.revision.collectAsStateWithLifecycle().value
    val destinations = remember(visibilityRevision) { NavBarVisibilityConfig.visibleDestinations() }

    val barStyle = BottomBarControl.style(BottomBarLayout.Docked)
    WallpaperChromeZone(edge = ChromeEdge.Bottom, material = barStyle.scrim) {
        // The fork's credit rides on the bar itself, above the items: below them it would sit
        // inside the system bar inset the navigation bar already pads for.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CreditLine(Modifier.padding(top = 6.dp))
            ShortNavigationBar(
                containerColor = barStyle.containerColor,
                windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            ) {
                destinations.forEach { destination ->
                    val selected = mainPagerState.selectedDestination == destination
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                me.weishu.kernelsu.media.MediaFeedback.navigation()
                                mainPagerState.animateToDestination(destination)
                            }
                        },
                        icon = {
                            NavigationIconWithBadge(
                                destination = destination,
                                selected = selected,
                                badge = badgeFor(destination, navigationBadge),
                            )
                        },
                        label = {
                            Text(
                                stringResource(destination.label),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = barStyle.indicatorColor?.let {
                            ShortNavigationBarItemDefaults.colors(selectedIndicatorColor = it)
                        } ?: ShortNavigationBarItemDefaults.colors(),
                    )
                }
            }
        }
    }
}

/** The fork's signature, in the same muted weight as a bar label. */
@Composable
internal fun CreditLine(modifier: Modifier = Modifier) {
    Text(
        stringResource(me.weishu.kernelsu.R.string.panxcz_credit),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        maxLines = 1,
    )
}

@Composable
internal fun NavigationIconWithBadge(
    destination: BottomBarDestination,
    selected: Boolean,
    badge: NavBadge?,
) {
    if (badge != null) {
        BadgedBox(
            badge = {
                when (badge.tone) {
                    BadgeTone.Alert -> Badge {
                        Text(badge.count.toString())
                    }

                    BadgeTone.Accent -> Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(badge.count.toString())
                    }
                }
            }
        ) {
            NavBarIcon(destination, selected)
        }
    } else {
        NavBarIcon(destination, selected)
    }
}
