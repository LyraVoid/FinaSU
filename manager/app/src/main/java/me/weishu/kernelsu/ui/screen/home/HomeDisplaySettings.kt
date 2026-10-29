package me.weishu.kernelsu.ui.screen.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl

/**
 * Which rows of the home info card the user asked to hide. Only the identifying rows are optional;
 * the base facts (manager version, kernel, device model, SELinux, Seccomp) always stay.
 */
internal data class HomeDisplaySettings(
    val hideFingerprint: Boolean,
    val hideZygisk: Boolean,
    val hideSusfs: Boolean,
    val hideMount: Boolean,
    val hideKernelFullVersion: Boolean,
)

@Composable
internal fun rememberHomeDisplaySettings(): HomeDisplaySettings {
    val context = LocalContext.current.applicationContext
    val repository = remember { SettingsRepositoryImpl() }
    fun read() = HomeDisplaySettings(
        hideFingerprint = repository.hideHomeFingerprint,
        hideZygisk = repository.hideHomeZygisk,
        hideSusfs = repository.hideHomeSusfs,
        hideMount = repository.hideHomeMount,
        hideKernelFullVersion = repository.hideHomeKernelFullVersion,
    )
    var settings by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null ||
                key == "hide_home_fingerprint" ||
                key == "hide_home_zygisk" ||
                key == "hide_home_susfs" ||
                key == "hide_home_mount" ||
                key == "hide_home_kernel_full_version"
            ) {
                settings = read()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
