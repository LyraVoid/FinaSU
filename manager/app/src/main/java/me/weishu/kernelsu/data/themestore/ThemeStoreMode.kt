package me.weishu.kernelsu.data.themestore

/**
 * How the theme store hands a theme to the user.
 *
 * [BUILTIN] downloads the `.fpt` inside the app and offers to apply it right away. [COMPAT] is the
 * browser fallback for devices where the in-app download or import misbehaves: the download button
 * opens the store link in the browser instead, and the user opens the saved `.fpt` through the
 * normal file-open flow. The store still lists/searches/filters remotely in both modes.
 *
 * The tokens match FolkPatch's `theme_mode` values so a shared preference keeps the same meaning.
 */
object ThemeStoreMode {
    /** No choice made yet; the store shows its one-time onboarding prompt. */
    const val UNSET = ""
    const val BUILTIN = "builtin"
    const val COMPAT = "compat"

    /** The effective mode: everything except an explicit [COMPAT] behaves as [BUILTIN]. */
    fun normalize(mode: String?): String = if (mode == COMPAT) COMPAT else BUILTIN

    fun isCompat(mode: String?): Boolean = mode == COMPAT
}
