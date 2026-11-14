package me.weishu.kernelsu.ui.component

import android.widget.ImageView
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.media.TitleTextMode
import me.weishu.kernelsu.media.VisualMediaConfig
import me.weishu.kernelsu.ui.theme.LocalThemePreview
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.wallpaper.AnimatedFileImage

@Composable
fun HomeTitleImage() {
    val preview = LocalThemePreview.current?.visualMedia
    val file = preview?.titleImage ?: VisualMediaConfig.file(LocalContext.current, false)
    val titleEnabled = preview?.titleEnabled ?: VisualMediaConfig.titleEnabled
    if (!titleEnabled || file == null) {
        Text(homeTitleText())
        return
    }
    val opacity = if (isInDarkTheme()) {
        preview?.titleNightOpacity ?: VisualMediaConfig.titleNightOpacity
    } else {
        preview?.titleDayOpacity ?: VisualMediaConfig.titleDayOpacity
    }
    AnimatedFileImage(
        file = file,
        scaleType = ImageView.ScaleType.FIT_CENTER,
        contentDescription = stringResource(R.string.app_name),
        revision = preview?.let { file.path } ?: VisualMediaConfig.revision.toString(),
        dim = preview?.titleDim ?: VisualMediaConfig.titleDim,
        modifier = Modifier
            .height(40.dp)
            .offset(x = ((preview?.titleOffsetX ?: VisualMediaConfig.titleOffsetX) * 100f).dp)
            .alpha(opacity),
    )
}

/** The text the home top bar shows while no title image is in use. */
@Composable
fun homeTitleText(): String {
    val preview = LocalThemePreview.current?.visualMedia
    return when (preview?.titleTextMode ?: VisualMediaConfig.titleTextMode) {
        TitleTextMode.DEFAULT -> stringResource(R.string.app_name)
        TitleTextMode.KERNELSU -> stringResource(R.string.home_title_kernelsu)
        TitleTextMode.CUSTOM -> (preview?.customTitleText ?: VisualMediaConfig.customTitleText)
            .ifBlank { stringResource(R.string.app_name) }
    }
}
