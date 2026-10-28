package me.weishu.kernelsu.ui.component.material

import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole

/**
 * Marks that the current subtree already sits on a wallpaper material, so nested surfaces do not
 * stack a second translucent fill and darken the wallpaper.
 */
val LocalWallpaperMaterialRole = staticCompositionLocalOf<WallpaperSurfaceRole?> { null }

/**
 * The supporting content colour for a secondary line inside a wallpaper material.
 *
 * It is the material's own content colour at a reduced alpha (see [WallpaperMaterial.supportingColor]),
 * so the pair keeps its contrast over the translucent fill instead of falling back to the semantic
 * greys that were tuned against an opaque panel.
 *
 * Null outside wallpaper mode, and outside any wallpaper surface while it is on, so callers keep
 * their `onSurfaceVariant`/`outline` fallback and non-wallpaper rendering stays byte-identical.
 */
val LocalWallpaperSupportingColor = staticCompositionLocalOf<Color?> { null }

/**
 * The unified transparency layering controller.
 *
 * Every on-screen container that wants to sit on the wallpaper should draw its fill through this
 * composable instead of hard-coding a surface colour: cards go through [TonalCard], list groups
 * through [SegmentedColumn], the top/bottom bars through [WallpaperChromeZone]. They all end up
 * here, so there is exactly ONE alpha per [WallpaperSurfaceRole], resolved centrally by
 * `resolveFolkWallpaperTokens`. That is what keeps separate panels from drifting apart.
 *
 * While wallpaper mode is active it renders the wallpaper material for [role]. Otherwise it renders a
 * plain [Surface] with [fallbackColor] when one is given, or passes through untouched, so callers can
 * use it unconditionally and non-wallpaper rendering stays byte-identical. It also provides
 * [LocalWallpaperMaterialRole] so descendants do not layer a second fill.
 *
 * Only the fill alpha changes; `Modifier.alpha` is never used, so text and icons stay fully opaque.
 *
 * @param fallbackColor fill used when wallpaper mode is off. `Color.Unspecified` keeps the caller's
 *   own styling (passthrough).
 * @param fallbackContentColor content colour used with [fallbackColor]; defaults to
 *   `contentColorFor(fallbackColor)`.
 */
@Composable
fun FolkWallpaperSurface(
    role: WallpaperSurfaceRole,
    shape: Shape,
    modifier: Modifier = Modifier,
    minimumAlpha: Float = 0f,
    fallbackColor: Color = Color.Unspecified,
    fallbackContentColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFolkWallpaperTokens.current
    if (tokens == null || LocalWallpaperMaterialRole.current != null) {
        if (tokens == null && fallbackColor != Color.Unspecified) {
            Surface(
                modifier = modifier,
                shape = shape,
                color = fallbackColor,
                contentColor = if (fallbackContentColor != Color.Unspecified) {
                    fallbackContentColor
                } else {
                    contentColorFor(fallbackColor)
                },
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                content()
            }
        } else {
            content()
        }
        return
    }
    val material = tokens.material(role, minimumAlpha)
    Surface(
        modifier = modifier,
        shape = shape,
        color = material.fill,
        contentColor = material.contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        CompositionLocalProvider(
            LocalWallpaperMaterialRole provides role,
            LocalWallpaperSupportingColor provides material.supportingColor,
        ) {
            content()
        }
    }
}
