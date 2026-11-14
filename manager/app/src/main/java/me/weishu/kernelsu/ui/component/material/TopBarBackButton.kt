package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.LocalThemePreviewBack
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

@Composable
fun TopBarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    horizontalPadding: Dp = 10.dp,
) {
    val tokens = LocalFolkWallpaperTokens.current
    FolkIconButton(
        modifier = modifier.padding(horizontal = horizontalPadding),
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = tokens
                ?.chromeControlFill(MaterialTheme.colorScheme.surfaceContainerHighest)
                ?: MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = tokens?.chrome?.contentColor
                ?: MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
        )
    }
}

/** Adds the preview's exit control to a page's existing leading toolbar slot. */
@Composable
fun PreviewTopBarNavigation(content: @Composable () -> Unit = {}) {
    val onBack = LocalThemePreviewBack.current
    if (onBack == null) {
        content()
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TopBarBackButton(
            onClick = onBack,
            contentDescription = stringResource(R.string.theme_preview_back),
        )
        content()
    }
}
