package me.weishu.kernelsu.ui.component

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.surfaceAspect

/**
 * Shared image-upload flow for a card surface, used by both the wallpaper settings page and the
 * long-press options dialog.
 *
 * Set [request] to the surface being edited to start it: the photo picker opens, then a small
 * dialog lets the user crop the picture to the card's exact bounds or use it as is. It is a thin
 * wrapper over [ImageCropPickerHost], so surfaces get the same in-app crop the avatar and the
 * wallpaper use. [onPicked] receives the final image; the caller must keep [request] set until then.
 */
@Composable
fun SurfaceImagePickerHost(
    request: SurfaceId?,
    onDismissRequest: () -> Unit,
    onPicked: (SurfaceId, Uri) -> Unit,
) {
    val id = request
    ImageCropPickerHost(
        trigger = id?.value,
        aspect = remember(id) { id?.let { surfaceAspect(it) } ?: 1f },
        onDismissRequest = onDismissRequest,
        onPicked = { uri -> id?.let { onPicked(it, uri) } },
    )
}
