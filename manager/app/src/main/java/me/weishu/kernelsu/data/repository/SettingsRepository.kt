package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkZygiskStatus
import me.weishu.kernelsu.data.model.FolkMountStatus
import me.weishu.kernelsu.data.model.NomountStatus
import me.weishu.kernelsu.data.model.SuiStatus
import me.weishu.kernelsu.data.model.SuiApp
import me.weishu.kernelsu.data.model.ModuleSortGroup
import me.weishu.kernelsu.data.modulestore.StoreSourceKind

interface SettingsRepository {
    var checkUpdate: Boolean
    var checkModuleUpdate: Boolean
    var themeMode: Int
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    var useSystemDynamicColor: Boolean
    var colorGenerationMode: String
    var colorContrast: String
    var statsTopLayout: String
    var listWorkingCardModeHidden: Boolean
    var hideHomeFingerprint: Boolean
    var hideHomeZygisk: Boolean
    var hideHomeSusfs: Boolean
    var hideHomeMount: Boolean
    var hideHomeKernelFullVersion: Boolean
    var enablePredictiveBack: Boolean
    var enableNavigationBadge: Boolean
    var pageScale: Float
    var moduleDescriptionMaxLines: Int
    var enableWebDebugging: Boolean
    var homeLayoutStyle: String
    var supportLinksStyle: String

    /** Text of the `SupportLinksStyle.CUSTOM` home card. Empty values hide the card. */
    var supportLinksCustomTitle: String
    var supportLinksCustomSubtitle: String
    var supportLinksCustomUrl: String

    /** Theme store transfer mode; see `ThemeStoreMode`. Empty means no choice has been made yet. */
    var themeStoreMode: String
    var layoutVariant: String
    var moduleSortGroups: Set<ModuleSortGroup>
    var moduleSortEnabledFirst: Boolean
    var moduleSortCustomOrder: List<String>
    var superuserShowSystemApps: Boolean
    var superuserShowOnlyPrimaryUserApps: Boolean
    var superuserSortOption: Int
    var allowAnyDynamicManager: Boolean
    var suLogFilters: Set<String>?
    var autoJailbreak: Boolean
    var useSoftReboot: Boolean
    var repoSourceKind: StoreSourceKind
    var repoCustomUrl: String
    var repoSelectedRepositoryUrl: String
    val intentToken: String

    // Backup targets and content. The WebDAV password is *not* here: it is a reverse-readable
    // secret and lives in the keystore-backed SecretStore.
    var backupLocalEnabled: Boolean
    var backupWebDavEnabled: Boolean
    var webDavUrl: String
    var webDavUsername: String
    var webDavPath: String
    var backupModulesEnabled: Boolean
    var backupAllowlistEnabled: Boolean
    var backupPartitions: Set<String>

    /** When enabled, a snapshot is taken automatically before a flash overwrites a target. */
    var backupBeforeFlash: Boolean

    suspend fun getSuCompatStatus(): String
    suspend fun getSuCompatPersistValue(): Long?
    fun isSuEnabled(): Boolean
    fun setSuEnabled(enabled: Boolean): Boolean
    fun setSuCompatModePref(mode: Int)
    fun getSuCompatModePref(): Int

    suspend fun getKernelUmountStatus(): String
    fun isKernelUmountEnabled(): Boolean
    fun setKernelUmountEnabled(enabled: Boolean): Boolean

    suspend fun getSelinuxHideStatus(): String
    fun isSelinuxHideEnabled(): Boolean
    fun setSelinuxHideEnabled(enabled: Boolean): Int

    suspend fun getMountViewFilterStatus(): String
    suspend fun isMountViewFilterEnabled(): Boolean
    suspend fun setMountViewFilterEnabled(enabled: Boolean): Result<Unit>

    suspend fun getSELinuxMode(): String
    suspend fun setSELinuxMode(enforcing: Boolean): Boolean

    suspend fun getSulogStatus(): String
    suspend fun getAvcSpoofStatus(): String
    fun isAvcSpoofEnabled(): Boolean
    fun setAvcSpoofEnabled(enabled: Boolean): Boolean
    suspend fun getSulogPersistValue(): Long?
    suspend fun getSulogValue(): Long?
    fun setSulogEnabled(enabled: Boolean): Boolean

    suspend fun getFolkMountStatus(): Result<FolkMountStatus>
    suspend fun getFolkZygiskStatus(): Result<FolkZygiskStatus>
    suspend fun setFolkZygiskEnabled(enabled: Boolean): Result<Unit>
    suspend fun recoverFolkZygisk(): Result<Unit>
    suspend fun getSuiStatus(): Result<SuiStatus>
    suspend fun setSuiEnabled(enabled: Boolean): Result<Unit>
    suspend fun repairSui(): Result<Unit>
    suspend fun suiProbeManagement(): Result<String>
    suspend fun suiAppPermissions(): Result<List<SuiApp>>
    suspend fun suiLaunchableAppCount(): Result<Int>
    suspend fun suiDefaultMode(): Result<Int>
    suspend fun setSuiDefaultMode(mode: Int): Result<Unit>
    suspend fun suiSettingsFlags(): Result<Int>
    suspend fun setSuiSettingsFlags(flags: Int): Result<Unit>
    suspend fun suiHiddenUids(): Result<List<Int>>
    suspend fun setSuiAppMode(uid: Int, mode: Int): Result<Unit>
    suspend fun setFolkMountMode(mode: FolkMountMode): Result<Unit>
    suspend fun getNomountStatus(): Result<NomountStatus>
    suspend fun setNomountIsolated(enabled: Boolean): Result<Unit>
    suspend fun injectNomountRules(): Result<Unit>
    suspend fun clearNomountRules(): Result<Unit>
    suspend fun clearNomountEverything(): Result<Unit>
    suspend fun setNomountUids(uids: List<Int>): Result<Unit>
    suspend fun hotLoadNomountModule(id: String): Result<Unit>
    suspend fun hotUnloadNomountModule(id: String): Result<Unit>
    suspend fun setModuleSkip(id: String, skip: Boolean): Result<Unit>

    suspend fun getAdbRootStatus(): String
    suspend fun getAdbRootPersistValue(): Long?
    suspend fun getAdbRootValue(): Long?
    fun setAdbRootEnabled(enabled: Boolean): Boolean
    suspend fun getBootHideStatus(): String
    fun setBootHideEnabled(enabled: Boolean): Boolean
    suspend fun getShellSuAdbOnlyStatus(): String
    suspend fun getShellSuAdbOnlyPersistValue(): Long?
    suspend fun getShellSuAdbOnlyValue(): Long?
    fun setShellSuAdbOnlyEnabled(enabled: Boolean): Boolean

    fun isDefaultUmountModules(): Boolean
    fun setDefaultUmountModules(enabled: Boolean): Boolean

    fun isLkmMode(): Boolean

    fun execKsudFeatureSave(): Boolean
}
