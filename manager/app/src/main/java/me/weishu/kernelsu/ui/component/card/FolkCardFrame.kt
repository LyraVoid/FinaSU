package me.weishu.kernelsu.ui.component.card

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.weishu.kernelsu.modulebanner.ModuleBannerConfig
import me.weishu.kernelsu.ui.component.material.FolkWallpaperSurface
import me.weishu.kernelsu.ui.component.material.folkPressScale
import me.weishu.kernelsu.ui.component.module.LocalInsideSplicedGroup
import me.weishu.kernelsu.ui.navigation3.navOrigin
import me.weishu.kernelsu.ui.screen.module.ModuleBannerVisual
import me.weishu.kernelsu.ui.theme.ContinuousCornerShape
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole

/**
 * The shell every card of the module design shares: the animated corner, press scaling, the
 * wallpaper tint and the banner drawn behind the content.
 *
 * Module and plugin cards only differ in what they put inside, so both wrap their content in this
 * frame and inherit the same look and interactions.
 */
@Composable
fun FolkCardFrame(
    banner: ModuleBannerVisual?,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onClickLabel: String? = null,
    modifier: Modifier = Modifier,
    bannerCacheKey: String? = null,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val cardCorner by animateDpAsState(
        targetValue = when {
            expanded -> 28.dp
            pressed -> 24.dp
            else -> 20.dp
        },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "cardCorner",
    )
    val cardShape = ContinuousCornerShape(cardCorner)
    val insideSplicedGroup = LocalInsideSplicedGroup.current

    val isWallpaperMode = LocalFolkWallpaperTokens.current != null
    val navOriginColor = LocalFolkWallpaperTokens.current?.group?.fill
        ?: MaterialTheme.colorScheme.surfaceBright
    val opacity = cardSurfaceOpacity()
    val bannerImageAlpha = bannerImageAlpha(isWallpaperMode, opacity)

    val clickModifier = Modifier
        .fillMaxWidth()
        .navOrigin(
            cornerRadius = cardCorner,
            color = navOriginColor,
            interactionSource = interactionSource,
        )
        .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
        .folkPressScale(interactionSource)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClickLabel = onClickLabel,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onLongPress()
            },
        )

    val framed: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth()) {
            banner?.takeIf { ModuleBannerConfig.policy.active }?.let { image ->
                val fadeColor = MaterialTheme.colorScheme.surface
                val gradientAlpha = if (isWallpaperMode) 0.5f else 0.8f
                val bannerModel = remember(image.model, bannerCacheKey) {
                    if (bannerCacheKey == null) {
                        image.model
                    } else {
                        ImageRequest.Builder(context)
                            .data(image.model)
                            .memoryCacheKey(bannerCacheKey)
                            .build()
                    }
                }
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = bannerImageAlpha,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        fadeColor.copy(alpha = 0f),
                                        fadeColor.copy(alpha = gradientAlpha),
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY,
                                ),
                            ),
                    )
                }
            }
            content()
        }
    }

    if (insideSplicedGroup) {
        // The group already draws the surface; a second one would double the corner and the tint.
        Box(modifier = modifier.then(clickModifier)) { framed() }
    } else {
        FolkWallpaperSurface(
            role = WallpaperSurfaceRole.Group,
            modifier = modifier.clip(cardShape).then(clickModifier),
            shape = cardShape,
            minimumAlpha = opacity,
            fallbackColor = MaterialTheme.colorScheme.surfaceBright,
        ) {
            framed()
        }
    }
}

/** Tint of a card, raised over a wallpaper so the content stays readable. */
@Composable
fun cardSurfaceOpacity(): Float {
    if (LocalFolkWallpaperTokens.current == null) return 1f
    return WallpaperConfig.opacity.coerceAtLeast(0.35f)
}

/** How strongly the banner shows through the card. */
@Composable
private fun bannerImageAlpha(isWallpaperMode: Boolean, opacity: Float): Float = when {
    ModuleBannerConfig.customOpacityEnabled -> ModuleBannerConfig.customOpacity
    isWallpaperMode -> (0.35f + (opacity - 0.2f) * 0.5f).coerceIn(0.25f, 0.6f)
    else -> 0.18f
}
