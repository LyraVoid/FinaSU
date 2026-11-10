package me.weishu.kernelsu.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R

/**
 * The single image-upload flow every caller shares: pick a picture, then crop it to [aspect] or use
 * it as is.
 *
 * Set [trigger] to a non-null value to start it; the photo picker opens, then a dialog offers the
 * crop-or-direct choice. Cropping runs in-app through [SurfaceCropDialog], never through the
 * platform `com.android.camera.action.CROP` intent, so the result is always written to a file the
 * app can read back and the framing matches exactly what the target will show. [onPicked] receives
 * the final image and the caller clears [trigger] once it is handled; a new [trigger] value restarts
 * the flow from a clean state. [aspect] is width / height.
 */
@Composable
fun ImageCropPickerHost(
    trigger: Any?,
    aspect: Float,
    onDismissRequest: () -> Unit,
    onPicked: (Uri) -> Unit,
    lossless: Boolean = false,
) {
    var picked by rememberSaveable(trigger) { mutableStateOf<Uri?>(null) }
    var cropping by rememberSaveable(trigger) { mutableStateOf(false) }
    var launched by rememberSaveable(trigger) { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (trigger != null) {
            if (uri != null) picked = uri else onDismissRequest()
        }
    }

    if (trigger == null) return
    LaunchedEffect(trigger) {
        if (!launched && picked == null) {
            launched = true
            pickLauncher.launch("image/*")
        }
    }

    val target = picked ?: return
    if (cropping) {
        SurfaceCropDialog(
            source = target,
            aspect = aspect,
            onDismiss = { cropping = false },
            onCropped = onPicked,
            lossless = lossless,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.wallpaper_crop_title)) },
        text = { Text(stringResource(R.string.wallpaper_crop_message)) },
        dismissButton = {
            TextButton(onClick = { onPicked(target) }) {
                Text(stringResource(R.string.wallpaper_crop_direct))
            }
        },
        confirmButton = {
            TextButton(onClick = { cropping = true }) {
                Text(stringResource(R.string.wallpaper_crop_confirm))
            }
        },
    )
}
