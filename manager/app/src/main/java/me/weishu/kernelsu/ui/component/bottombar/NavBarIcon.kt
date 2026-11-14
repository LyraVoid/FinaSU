package me.weishu.kernelsu.ui.component.bottombar

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.theme.LocalThemePreview

/**
 * Renders a bottom navigation destination icon. Prefers the user-picked custom icon (when custom
 * icons are enabled and an image is set) and falls back to the default Material vector.
 */
@Composable
fun NavBarIcon(
    destination: BottomBarDestination,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
) {
    val previewIcons = LocalThemePreview.current?.navIcons
    val revision by BottomBarIconConfig.revision.collectAsState()
    val animationRevision by NavIconAnimationConfig.revision.collectAsState()
    val animation = remember(animationRevision) { NavIconAnimationConfig.animation }
    val customUri = remember(revision, destination, previewIcons) {
        when {
            previewIcons != null -> previewIcons[destination]?.let { Uri.fromFile(it).toString() }
            BottomBarIconConfig.isEnabled -> BottomBarIconConfig.getCustomIconUri(destination.name)
            else -> null
        }
    }

    if (customUri != null) {
        val bitmap by produceState<Bitmap?>(initialValue = null, customUri) {
            value = withContext(Dispatchers.IO) {
                BottomBarIconConfig.loadIconBitmap(customUri)
            }
        }
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = stringResource(destination.label),
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit,
            )
            return
        }
    }

    when (animation) {
        NavIconAnimation.Morph -> MorphIcon(
            vector = destination.iconNotSelected,
            selected = isSelected,
            tint = tint,
            modifier = modifier.size(24.dp),
        )

        NavIconAnimation.Fade -> FilledNavIcon(
            outlined = destination.iconNotSelected,
            filled = destination.iconSelected,
            selected = isSelected,
            tint = tint,
            modifier = modifier.size(24.dp),
        )
    }
}

/**
 * Switches between the outlined and the real filled Material glyph.
 *
 * Material's outlined and filled icons have structurally different path data, so a literal path
 * interpolation is not possible; the switch is animated as a short cross-fade with a subtle
 * scale so it still reads as a single transformation.
 */
@Composable
private fun FilledNavIcon(
    outlined: ImageVector,
    filled: ImageVector,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navIconFill",
    )
    Box(modifier) {
        if (progress < 1f) {
            Icon(
                imageVector = outlined,
                contentDescription = null,
                tint = tint.copy(alpha = tint.alpha * (1f - progress)),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - OUTLINED_SCALE_LOSS * progress
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        if (progress > 0f) {
            Icon(
                imageVector = filled,
                contentDescription = null,
                tint = tint.copy(alpha = tint.alpha * progress),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - OUTLINED_SCALE_LOSS * (1f - progress)
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

private const val OUTLINED_SCALE_LOSS = 0.12f
