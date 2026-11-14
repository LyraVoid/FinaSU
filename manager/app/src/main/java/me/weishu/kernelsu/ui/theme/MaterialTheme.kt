package me.weishu.kernelsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.PaletteStyle
import me.weishu.kernelsu.ui.webui.MonetColorsProvider
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.LocalWallpaperDim
import me.weishu.kernelsu.wallpaper.LocalWallpaperPage
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.adaptColorScheme
import me.weishu.kernelsu.wallpaper.guardedDim
import me.weishu.kernelsu.wallpaper.resolveFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.useDarkNeutral

// Default dark ripple alpha (~10% pressed) is nearly invisible on near-black surfaces, so boost
// it for a clear press feedback at night. Light mode keeps Compose's default.
private val DarkRippleAlpha = RippleAlpha(
    draggedAlpha = 0.32f,
    focusedAlpha = 0.24f,
    hoveredAlpha = 0.16f,
    pressedAlpha = 0.24f,
)

@Composable
fun MaterialKernelSUTheme(
    appSettings: AppSettings,
    preview: ThemePreviewSnapshot? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val amoledMode = appSettings.colorMode.isAmoled
    val dynamicColor = appSettings.useSystemDynamicColor

    val wallpaperPage = LocalWallpaperPage.current
    val previewWallpaper = preview?.wallpapers?.getOrNull(wallpaperPage)
    val previewVideo = preview?.videoBackground
    val previewBackground = previewVideo != null || previewWallpaper != null
    val videoActive = preview == null && WallpaperConfig.enabled &&
        me.weishu.kernelsu.media.VisualMediaConfig.let {
            it.videoEnabled && it.file(context, true) != null
        }
    val wallpaperActive = if (preview != null) previewBackground else WallpaperConfig.isPageActive(wallpaperPage, videoActive)
    // The accent palette is derived from one fixed image, never from the page currently on screen:
    // a page swipe must not re-theme every component (`animateAsState` would then drag the whole
    // app through a color transition and make the swipe feel laggy). Only the background itself and
    // its content polarity follow the page.
    val seedPage = if (WallpaperConfig.multiBackgroundEnabled) WallpaperConfig.PAGE_HOME else wallpaperPage
    val pageSeed = if (previewWallpaper != null) 0 else WallpaperConfig.pageSeed(seedPage)
    val wallpaperSeed = if (
        preview == null && WallpaperConfig.isActive && WallpaperConfig.useWallpaperColor &&
        pageSeed != 0
    ) {
        Color(pageSeed)
    } else {
        Color.Unspecified
    }
    val baseSeed = when {
        wallpaperSeed != Color.Unspecified -> wallpaperSeed
        dynamicColor -> Color.Unspecified
        else -> Color(appSettings.keyColor)
    }

    val colorScheme = rememberKernelSUColorScheme(
        seedColor = baseSeed,
        isDark = darkTheme,
        isAmoled = amoledMode,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
        contrastLevel = appSettings.contrastLevel,
        generationMode = appSettings.colorGenerationMode,
    )

    // In wallpaper mode the neutral roles follow the wallpaper's intrinsic brightness so text stays
    // legible; a dark theme never flips to light, readability there is left to the night dim. The
    // scrim is intentionally not an input here: dragging the day dim must never invert polarity.
    val wallpaperDim = previewWallpaper?.dim ?: WallpaperConfig.effectiveDim(darkTheme)
    val previewLuminance = remember(previewWallpaper?.file) {
        previewWallpaper?.file?.let { me.weishu.kernelsu.wallpaper.WallpaperManager.computeLuminance(it) } ?: -1f
    }
    val darkNeutral = useDarkNeutral(darkTheme, if (preview != null) previewLuminance else WallpaperConfig.pageLuminance(wallpaperPage))
    val neutralScheme = rememberKernelSUColorScheme(
        seedColor = baseSeed,
        isDark = darkNeutral,
        isAmoled = false,
        paletteStyle = PaletteStyle.Neutral,
        colorSpec = appSettings.colorSpec,
        contrastLevel = appSettings.contrastLevel,
        generationMode = appSettings.colorGenerationMode,
    )
    val chosenNeutral = if (wallpaperActive && darkNeutral != darkTheme) neutralScheme else colorScheme
    val adaptedColorScheme = adaptColorScheme(
        base = colorScheme,
        neutral = chosenNeutral,
        active = wallpaperActive,
    )
    // Animate the scheme once, then derive both the MaterialTheme palette and the wallpaper tokens
    // from the same animated instance. Otherwise a panel's fill/content comes from the target scheme
    // while the text inside it is still interpolating, leaving a transient low-contrast frame.
    val animatedColorScheme = adaptedColorScheme.animateAsState()
    val wallpaperTokens = if (wallpaperActive) {
        resolveFolkWallpaperTokens(animatedColorScheme, previewWallpaper?.opacity ?: WallpaperConfig.opacity)
    } else {
        null
    }
    val renderDim = if (wallpaperActive && darkNeutral && !darkTheme) {
        guardedDim(wallpaperDim, adaptedColorScheme) ?: wallpaperDim
    } else {
        wallpaperDim
    }.takeIf { wallpaperActive }

    // In wallpaper mode the top of the screen is the wallpaper itself, so the system-bar icon
    // brightness must follow the adapted content brightness, not just the theme mode.
    val systemBarDark = darkTheme || (wallpaperActive && darkNeutral)

    LaunchedEffect(systemBarDark) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !systemBarDark
            isAppearanceLightNavigationBars = !systemBarDark
        }
    }

    val configuredFontFamily = remember(FontConfig.fontMode, FontConfig.customFontFilename) {
        FontConfig.getFontFamily(context)
    }
    val fontFamily = preview?.fontFamily ?: configuredFontFamily

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = remember(fontFamily) { getTypography(fontFamily) },
        shapes = FolkShape.materialShapes,
        content = {
            // alpha28 has no non-deprecated way to override the ripple alpha, so the constructor is
            // suppressed rather than the alpha left at a value that disappears on near-black.
            @Suppress("DEPRECATION")
            val rippleConfiguration = if (darkTheme) {
                RippleConfiguration(rippleAlpha = DarkRippleAlpha)
            } else {
                LocalRippleConfiguration.current
            }
            CompositionLocalProvider(
                LocalRippleConfiguration provides rippleConfiguration,
                LocalWallpaperDim provides renderDim,
                LocalFolkWallpaperTokens provides wallpaperTokens,
            ) {
                if (preview == null) MonetColorsProvider.UpdateCss(colorScheme)
                content()
            }
        }
    )
}
