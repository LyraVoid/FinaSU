package me.weishu.kernelsu.ui.screen.home

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.SurfaceOptionsDialog
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.ui.theme.FolkShape
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.wallpaper.AnimatedFileImage
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager
import me.weishu.kernelsu.wallpaper.isAnimatedImageFile
import me.weishu.kernelsu.wallpaper.surface.SurfaceBounds
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceFlag

/** The gap between the tiles of the two-column grid. */
private val GridTileSpacing = 12.dp

/**
 * How far a work-card artwork may stretch the grid tiles away from a square.
 *
 * The tiles normally take the height their own content needs. When the work card carries an
 * artwork the row follows that artwork's aspect instead, so a picture the user cropped taller (or
 * wider) is shown at its own proportions instead of being forced into the tile's height. The clamps
 * keep an extreme image from pushing the rest of the home screen off the page.
 */
private const val MaxArtworkTileRatio = 1.6f
private const val MinArtworkTileRatio = 0.62f

/**
 * The pixel aspect ratio (width / height) of the work card's artwork, or null while it is unknown
 * or the surface has no image.
 *
 * Only the image bounds are read, so this is a cheap header parse rather than a full decode.
 */
@Composable
private fun rememberWorkCardArtworkAspect(uri: String?): Float? {
    if (uri.isNullOrEmpty()) return null
    val path = remember(uri) { uri.toUri().path }
    val aspect by produceState<Float?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            val file = path?.let(::File)
            if (file == null || !file.exists() || file.length() == 0L) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
            } else {
                null
            }
        }
    }
    return aspect
}

/**
 * The two-column grid: a hero status card on the left with the two count cards stacked on the
 * right. Unavailable counts keep their place so changes in capability do not rearrange the grid.
 *
 * While the work card paints an artwork the row takes that artwork's aspect, so the picture is
 * drawn one-to-one and the card grows with it instead of cropping it back to a fixed tile height.
 * Without an image the row keeps sizing itself from its content, exactly as before.
 */
@Composable
internal fun GridHomeContent(
    state: HomeUiState,
    actions: HomeActions,
    superuserCount: Int,
    moduleEnabledCount: Int,
) {
    val countsAvailable = useFullFeaturedLayout() && state.isRootAvailable
    val workCardSurface = WallpaperConfig.workCardSurface
    val artworkAspect = rememberWorkCardArtworkAspect(
        workCardSurface.imageUri.takeIf { workCardSurface.hasImage }
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - GridTileSpacing) / 2
        val artworkTileHeight = artworkAspect?.let { aspect ->
            (tileWidth / aspect).coerceIn(tileWidth * MinArtworkTileRatio, tileWidth * MaxArtworkTileRatio)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (artworkTileHeight != null) {
                        Modifier.height(artworkTileHeight)
                    } else {
                        Modifier.height(IntrinsicSize.Min)
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(GridTileSpacing)
        ) {
            GridStatusCard(
                state = state,
                actions = actions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            CountCardPair(
                superuserCount = superuserCount,
                moduleEnabledCount = moduleEnabledCount,
                onOpenSuperUser = actions.onOpenSuperUser,
                onOpenModule = actions.onOpenModule,
                layout = CountCardLayout.Vertical,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                pairSpacing = GridTileSpacing,
                // Keep enough room for the label and value in each tile.
                contentPadding = PaddingValues(18.dp),
                emphasis = CountCardEmphasis.Value,
                available = countsAvailable,
            )
        }
    }
}

/**
 * The grid hero card. The status icon sits in the top corner while the state title and its mode
 * tag sit against the bottom edge, so the card reads as a tile rather than a list row.
 */
@Composable
private fun GridStatusCard(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()
    val hookFailed = state.kernelHook.failed

    val workCardSurface = WallpaperConfig.workCardSurface
    val workCardPhoto = rememberSurfacePhoto(workCardSurface.imageUri.takeIf { workCardSurface.hasImage })
    val workCardStyle = HomeWorkCardControl.style(
        layout = HomeWorkCardLayout.Grid,
        working = ksuActive && !hookFailed,
        surface = workCardSurface,
        photo = workCardPhoto,
    )
    val containerColor = workCardStyle.containerColor
    val contentColor = workCardStyle.contentColor ?: contentColorFor(containerColor)
    val backgroundUri = workCardStyle.workCardBackgroundUri

    val interactionSource = remember { MutableInteractionSource() }
    val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
    val pressed by interactionSource.collectIsPressedAsState()
    var longPressActive by remember { mutableStateOf(false) }
    var petCount by remember { mutableStateOf(0) }
    val petBounce = remember { Animatable(0f) }
    val heldDeformation by animateFloatAsState(
        targetValue = if (backgroundUri != null && !reduceMotion) {
            if (longPressActive) 0.16f else if (pressed) 0.045f else 0f
        } else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
        label = "workCardHeldDeformation",
    )

    LaunchedEffect(petCount, reduceMotion, backgroundUri) {
        if (petCount > 0 && !reduceMotion && backgroundUri != null) {
            petBounce.snapTo(0.14f)
            petBounce.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 180f))
        } else {
            petBounce.snapTo(0f)
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release || interaction is PressInteraction.Cancel) {
                longPressActive = false
            }
        }
    }

    val statusIcon = when {
        ksuActive && hookFailed -> Icons.Rounded.Warning
        ksuActive -> Icons.Rounded.CheckCircle
        notInstalled -> Icons.Rounded.Warning
        else -> Icons.Rounded.Block
    }
    val statusTitle = when {
        ksuActive && hookFailed -> kernelHookValue(state.kernelHook)
        ksuActive -> stringResource(R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val workingMode = if (ksuActive) {
        when (state.lkmMode) {
            null -> ""
            true -> "LKM"
            else -> "Built-In"
        }
    } else ""

    var showWorkCardOptions by remember { mutableStateOf(false) }

    HomeCard(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        wallpaperRole = workCardStyle.wallpaperRole,
        interactionSource = interactionSource,
        onClick = {
            if (backgroundUri != null) petCount++
        },
        onLongClick = {
            longPressActive = true
            showWorkCardOptions = true
        },
    ) {
        Box(
            modifier = Modifier
                // Size the image's actual parent, not only the outer Card's Column. The floor is
                // dropped once an artwork drives the tile height, so a wide picture cannot force
                // the content taller than the card it is clipped to.
                .then(if (backgroundUri == null) Modifier.heightIn(min = 160.dp) else Modifier)
                .fillMaxSize()
                .onSizeChanged { SurfaceBounds.report(SurfaceRegistry.GRID_WORK_CARD, it.width, it.height) },
        ) {
            if (backgroundUri != null) {
                SurfaceBackgroundImage(
                    photo = workCardPhoto,
                    surface = workCardSurface,
                    // Show the picture the user actually framed: with Crop the card would cut every
                    // ratio back to its own aspect, so changing the crop frame would change nothing.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val deformation = heldDeformation + petBounce.value
                            scaleX = 1f + deformation * 0.7f
                            scaleY = 1f - deformation
                        },
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!workCardSurface.hasFlag(SurfaceFlag.HideText)) {
                    Text(
                        text = statusTitle,
                        style = FolkType.Title
                    )
                }
                if (ksuActive && workingMode.isNotEmpty()) {
                    if (!workCardSurface.hasFlag(SurfaceFlag.HideMode)) {
                        StatusTag(
                            label = workingMode,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (notInstalled && state.isSELinuxPermissive) {
                    StatusJailbreakButton(onClick = actions.onJailbreakClick)
                }
            }
            if (!workCardSurface.hasFlag(SurfaceFlag.HideIcon)) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusTitle,
                    tint = contentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                )
            }
            if (backgroundUri != null && reduceMotion && pressed) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = if (longPressActive) 3.dp else 2.dp,
                            color = contentColor.copy(alpha = if (longPressActive) 1f else 0.8f),
                            shape = FolkShape.Corner20,
                        )
                )
            }
        }
    }

    if (showWorkCardOptions) {
        SurfaceOptionsDialog(
            surfaceId = SurfaceRegistry.GRID_WORK_CARD,
            onDismiss = { showWorkCardOptions = false },
        )
    }
}
