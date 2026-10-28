package me.weishu.kernelsu.wallpaper

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Which wallpaper surface role a component is drawing.
 *
 * There is deliberately no `Canvas` role: the canvas is the transparent Scaffold background with
 * no material of its own. A component either draws one of these roles or draws nothing.
 */
enum class WallpaperSurfaceRole { Group, Raised, Overlay }

/**
 * One wallpaper-mode material: an opaque semantic [tint] rendered at [alpha] over the wallpaper,
 * plus the content colors that are known to pair with it.
 */
@Immutable
data class WallpaperMaterial(
    val tint: Color,
    val alpha: Float,
    val contentColor: Color,
    val supportingColor: Color,
) {
    val fill: Color get() = tint.copy(alpha = alpha)
}

/**
 * The wallpaper materials, one per [WallpaperSurfaceRole], plus [chrome] for the top/bottom bars and
 * [selection] for a selected control that still needs a fill of its own.
 *
 * Held only while wallpaper mode is active (see [LocalFolkWallpaperTokens]); consumers fall back to
 * their original solid colors when it is null.
 */
@Immutable
data class FolkWallpaperTokens(
    val group: WallpaperMaterial,
    val raised: WallpaperMaterial,
    val overlay: WallpaperMaterial,
    val chrome: WallpaperMaterial,
    val selection: WallpaperMaterial,
) {
    fun material(role: WallpaperSurfaceRole, minimumAlpha: Float = 0f): WallpaperMaterial {
        val material = when (role) {
            WallpaperSurfaceRole.Group -> group
            WallpaperSurfaceRole.Raised -> raised
            WallpaperSurfaceRole.Overlay -> overlay
        }
        return if (material.alpha >= minimumAlpha) material else {
            material.copy(alpha = minimumAlpha.coerceIn(0f, 1f))
        }
    }

    /**
     * Raised fill for a surface drawn on top of the chrome platform, so that it lands on [raised]
     * instead of compositing with the platform beneath it.
     */
    fun raisedOverChrome(): Color {
        val under = chrome.alpha
        val alpha = if (under >= 1f) 0f else (raised.alpha - under) / (1f - under)
        return raised.tint.copy(alpha = alpha.coerceIn(0f, 1f))
    }

    /**
     * Fill for a small control drawn on the chrome platform.
     *
     * It keeps the caller's own tone, one step above the bar, but takes the platform's alpha, so the
     * chip fades together with the bar it sits on instead of cutting a solid hole into the wallpaper.
     */
    fun chromeControlFill(tint: Color): Color = tint.copy(alpha = chrome.alpha)
}

/**
 * Null outside wallpaper mode; provided by the theme so components can opt into the wallpaper
 * materials without reading [WallpaperConfig] themselves.
 */
val LocalFolkWallpaperTokens = compositionLocalOf<FolkWallpaperTokens?> { null }

/**
 * How translucent a bar may become: at opacity 0 the chrome still keeps this much of its tint.
 *
 * Panels may fade to nothing, but the top and bottom bars carry labels that sit on top of scrolling
 * content, so they hold a small platform of their own.
 */
private const val CHROME_ALPHA_FLOOR = 0.10f

/**
 * How much of the pair's content colour a supporting line keeps.
 *
 * The semantic `onSurfaceVariant`/`outline` greys are tuned against an opaque panel. Once the fill
 * is translucent the wallpaper shows through, and those greys lose the contrast they were chosen
 * for. Deriving the supporting colour from the content colour keeps the pair's own contrast, and the
 * small alpha drop preserves the hierarchy between a card's main line and its supporting line.
 */
private const val SUPPORTING_CONTENT_ALPHA = 0.8f

/**
 * How much accent tint a selected control keeps at opacity 0.
 *
 * A selection fill is the one place where the accent colour still has to be visible: at zero opacity
 * a fully transparent pill leaves only the icon polarity to read the state. The floor is high because
 * a tinted fill that is too thin does not read as a tint at all: whatever hue the photo happens to
 * have behind it bleeds through and turns the accent into a stain.
 */
private const val SELECTION_ALPHA_FLOOR = 0.45f

/**
 * Maps the user-facing opacity preference (0..1) onto the role alphas.
 *
 * The preference *is* the surface alpha: 0 lets the wallpaper show through completely and 1 is
 * fully opaque, so every role tracks the slider one-to-one instead of living in a narrowed band.
 * Three exceptions survive on purpose: [overlay] (dialogs and menus) stays near-opaque, because a
 * popup that lets the content behind it read through is not usable; [chrome] never drops below
 * [CHROME_ALPHA_FLOOR], because the bars' labels sit over scrolling content; and [selection] keeps
 * [SELECTION_ALPHA_FLOOR] so the selected control still reads as selected.
 */
fun resolveFolkWallpaperTokens(colorScheme: ColorScheme, opacity: Float): FolkWallpaperTokens {
    val alpha = opacity.coerceIn(0f, 1f)
    val panel = WallpaperMaterial(
        tint = colorScheme.surface,
        alpha = alpha,
        contentColor = colorScheme.onSurface,
        supportingColor = colorScheme.onSurface.copy(alpha = SUPPORTING_CONTENT_ALPHA),
    )
    return FolkWallpaperTokens(
        group = panel,
        raised = panel.copy(alpha = (alpha + 0.12f).coerceAtMost(1f)),
        // The overlay stays near-opaque, so its supporting grey still sits on a panel of its own and
        // keeps the semantic variant colour.
        overlay = WallpaperMaterial(
            tint = colorScheme.surfaceContainerHigh,
            alpha = 0.96f,
            contentColor = colorScheme.onSurface,
            supportingColor = colorScheme.onSurfaceVariant,
        ),
        chrome = panel.copy(alpha = CHROME_ALPHA_FLOOR + (1f - CHROME_ALPHA_FLOOR) * alpha),
        // The accent tint here is the container role, not the panel tint: a selected control has to
        // stay distinguishable from the surface it sits on, and the accent is what carries that.
        // Content follows the panel rather than `onSecondaryContainer`, because a translucent fill
        // composites with whatever is behind it instead of being the colour that pair was tuned for.
        selection = WallpaperMaterial(
            tint = colorScheme.secondaryContainer,
            alpha = SELECTION_ALPHA_FLOOR + (1f - SELECTION_ALPHA_FLOOR) * alpha,
            contentColor = colorScheme.onSurface,
            supportingColor = colorScheme.onSurface.copy(alpha = SUPPORTING_CONTENT_ALPHA),
        ),
    )
}
