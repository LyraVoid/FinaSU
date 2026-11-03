package me.weishu.kernelsu.ui.component.module

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.theme.ContinuousCornerShape
import me.weishu.kernelsu.ui.component.material.FolkWallpaperSurface
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole

val LocalInsideSplicedGroup = compositionLocalOf { false }

private val CornerRadius = 16.dp
private val ConnectionRadius = 5.dp

fun <T> LazyListScope.splicedLazyColumnGroup(
    items: List<T>,
    key: ((index: Int, item: T) -> Any)? = null,
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    animatePlacement: Boolean = false,
    itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) {
    val isAtLeastTiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    itemsIndexed(items = items, key = key, contentType = contentType) { index, item ->
        val isFirst = index == 0
        val isLast = index == items.size - 1
        val targetTopRadius = if (isFirst) CornerRadius else ConnectionRadius
        val targetBottomRadius = if (isLast) CornerRadius else ConnectionRadius
        val currentTopRadius = if (isAtLeastTiramisu) {
            animateDpAsState(
                targetValue = targetTopRadius,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "TopCornerRadius",
            ).value
        } else {
            targetTopRadius
        }
        val currentBottomRadius = if (isAtLeastTiramisu) {
            animateDpAsState(
                targetValue = targetBottomRadius,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "BottomCornerRadius",
            ).value
        } else {
            targetBottomRadius
        }
        val shape = ContinuousCornerShape(
            topStart = currentTopRadius,
            topEnd = currentTopRadius,
            bottomEnd = currentBottomRadius,
            bottomStart = currentBottomRadius,
        )
        FolkWallpaperSurface(
            role = WallpaperSurfaceRole.Group,
            modifier = Modifier
                .then(if (animatePlacement) Modifier.animateItem() else Modifier)
                .padding(horizontal = 16.dp)
                .padding(top = if (isFirst) 0.dp else 2.dp),
            shape = shape,
            fallbackColor = MaterialTheme.colorScheme.surfaceBright,
        ) {
            CompositionLocalProvider(LocalInsideSplicedGroup provides true) {
                itemContent(index, item)
            }
        }
    }
}
