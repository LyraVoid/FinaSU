package me.weishu.kernelsu.ui.component.bottombar

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.ksuApp

/**
 * How the selected navigation icon changes.
 *
 * [Morph] keeps the outlined glyph and collapses its enclosed contours, which reads as the icon
 * filling in. [Fade] cross-fades into the real filled Material glyph with a slight scale.
 */
enum class NavIconAnimation {
    Morph, Fade;

    val storageValue: String get() = name.lowercase()

    companion object {
        fun fromValue(value: String?): NavIconAnimation =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: Morph
    }
}

/**
 * Persisted icon animation. [revision] bumps on every write so the bar icons recompose immediately.
 */
object NavIconAnimationConfig {

    private const val PREFS = "settings"
    private const val KEY_ANIMATION = "nav_icon_animation"

    private val prefs get() = ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    var animation: NavIconAnimation
        get() = NavIconAnimation.fromValue(prefs.getString(KEY_ANIMATION, null))
        set(value) {
            prefs.edit { putString(KEY_ANIMATION, value.storageValue) }
            _revision.value++
        }
}
