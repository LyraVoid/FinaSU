package me.weishu.kernelsu.ui.navigation3

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.screen.modulerepo.StoreModuleArg
import me.weishu.kernelsu.ui.util.FlashItSerializer
import me.weishu.kernelsu.ui.util.StoreModuleArgSerializer
import me.weishu.kernelsu.ui.util.TemplateInfoSerializer
import me.weishu.kernelsu.ui.viewmodel.TemplateViewModel

/**
 * Type-safe navigation keys for navigation3.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
@Serializable
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object Language : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object Sulog : Route

    @Parcelize
    @Serializable
    data object Susfs : Route

    @Parcelize
    @Serializable
    data object UtsView : Route

    @Parcelize
    @Serializable
    data object NetIsolate : Route

    @Parcelize
    @Serializable
    data object Nomount : Route

    @Parcelize
    @Serializable
    data object UmountPaths : Route

    @Parcelize
    @Serializable
    data object SuiPermissions : Route

    @Parcelize
    @Serializable
    data object NomountExclusions : Route

    @Parcelize
    @Serializable
    data object ColorPalette : Route

    @Parcelize
    @Serializable
    data object Wallpaper : Route

    @Parcelize
    @Serializable
    data object WallpaperGallery : Route

    @Parcelize
    @Serializable
    data object Font : Route

    @Parcelize
    @Serializable
    data object ThemeStore : Route

    @Parcelize
    @Serializable
    data object MyThemes : Route

    @Parcelize
    @Serializable
    data object ApiMarketplace : Route

    /** One settings category, opened from the settings hub's category grid. `key` matches
     *  [me.weishu.kernelsu.ui.component.settings.SettingsCategory.key]. */
    @Parcelize
    @Serializable
    data class SettingsCategory(val key: String) : Route

    /** Browses the configured WebDAV collection; [path] is storage-relative to the base. */
    @Parcelize
    @Serializable
    data class WebDavBrowser(val path: String = "") : Route

    /** Lists backups across every configured backend. */
    @Parcelize
    @Serializable
    data object BackupList : Route

    @Parcelize
    @Serializable
    data object AppProfileTemplate : Route

    @Parcelize
    @Serializable
    data class TemplateEditor(
        @Serializable(with = TemplateInfoSerializer::class) val template: TemplateViewModel.TemplateInfo,
        val readOnly: Boolean
    ) : Route

    @Parcelize
    @Serializable
    data class AppProfile(val uid: Int) : Route

    @Parcelize
    @Serializable
    data object Install : Route

    @Parcelize
    @Serializable
    data class ModuleRepoDetail(@Serializable(with = StoreModuleArgSerializer::class) val module: StoreModuleArg) : Route

    @Parcelize
    @Serializable
    data object ModuleRepo : Route

    @Parcelize
    @Serializable
    data object KernelModules : Route

    @Parcelize
    @Serializable
    data object PluginStore : Route

    @Parcelize
    @Serializable
    data class Flash(@Serializable(with = FlashItSerializer::class) val flashIt: FlashIt) : Route

    @Parcelize
    @Serializable
    data class ExecuteModuleAction(val moduleId: String, val fromShortcut: Boolean = false) : Route
}
