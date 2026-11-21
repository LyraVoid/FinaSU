package me.weishu.kernelsu.ui.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A package update resets enabled component states to the manifest defaults, so the launcher would
 * show the default icon until the manager is opened. Restore the saved alias before the user taps.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        LauncherIconUtils.applySaved(context.applicationContext)
    }
}
