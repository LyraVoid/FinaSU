package me.weishu.kernelsu.ui

import androidx.compose.runtime.staticCompositionLocalOf
import me.weishu.kernelsu.data.model.LayoutVariant

/**
 * The layout variant selected by the user, provided by the app root.
 */
val LocalLayoutVariant = staticCompositionLocalOf { LayoutVariant.DEFAULT }
