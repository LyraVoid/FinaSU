package me.weishu.kernelsu.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Ported from FolkPatch's LauncherIconUtils. Switches between the enabled
 * launcher <activity-alias> entries so the home screen icon can be changed
 * at runtime without reinstalling the app.
 */
object LauncherIconUtils {
    private const val TAG = "LauncherIconUtils"
    private const val PREFS = "settings"

    private const val ALIAS_DEFAULT = "me.weishu.kernelsu.ui.MainActivityDefault"
    private const val ALIAS_GEOMETRY = "me.weishu.kernelsu.ui.MainActivityGeometry"
    private const val ALIAS_KERNELSU = "me.weishu.kernelsu.ui.MainActivityKernelSU"

    private const val MAIN_ACTIVITY = ALIAS_DEFAULT

    private val ALL_ALIASES = listOf(ALIAS_DEFAULT, ALIAS_GEOMETRY, ALIAS_KERNELSU)

    const val PREF_ICON_STYLE = "launcher_icon_style"
    const val STYLE_DEFAULT = "default"
    const val STYLE_GEOMETRY = "geometry"
    const val STYLE_KERNELSU = "kernelsu"

    val styles = listOf(STYLE_DEFAULT, STYLE_GEOMETRY, STYLE_KERNELSU)

    /** Resolves the active icon style, defaulting to [STYLE_DEFAULT]. */
    fun currentStyle(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(PREF_ICON_STYLE, STYLE_DEFAULT)
            ?.takeIf { it in styles } ?: STYLE_DEFAULT
    }

    fun setStyle(context: Context, style: String): Boolean {
        if (style !in styles || !updateLauncherState(context, style)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PREF_ICON_STYLE, style).apply()
        return true
    }

    /** ComponentName of the launcher alias that should be enabled for [style]. */
    fun componentFor(context: Context, style: String): ComponentName = when (style) {
        STYLE_GEOMETRY -> alias(context, ALIAS_GEOMETRY)
        STYLE_KERNELSU -> alias(context, ALIAS_KERNELSU)
        else -> alias(context, MAIN_ACTIVITY)
    }

    private fun alias(context: Context, name: String): ComponentName =
        // The owning package is the applicationId, but relative manifest class
        // names are expanded against the code namespace, not the applicationId.
        ComponentName(context.packageName, name)

    fun updateLauncherState(context: Context, style: String = currentStyle(context)): Boolean {
        val target = componentFor(context, style)
        val pm = context.packageManager
        return runCatching {
            // Re-applying an unchanged alias restarts the process on some ROMs, which would kill
            // the first launch after an update.
            fun apply(component: ComponentName, enabled: Boolean) {
                val wanted = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                if (pm.getComponentEnabledSetting(component) == wanted) return
                pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
            }
            apply(target, true)
            ALL_ALIASES.map { alias(context, it) }
                .filter { it != target }
                .forEach { apply(it, false) }
        }.onFailure {
            Log.e(TAG, "failed to update launcher icon state", it)
        }.isSuccess
    }

    fun applySaved(context: Context) {
        updateLauncherState(context)
    }
}
