package me.weishu.kernelsu.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkZygiskStatus
import me.weishu.kernelsu.data.model.FolkMountStatus
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.data.model.LayoutVariant
import me.weishu.kernelsu.data.model.NomountStatus
import me.weishu.kernelsu.data.model.ModuleCustomOrderStore
import me.weishu.kernelsu.data.model.ModuleSortGroup
import me.weishu.kernelsu.data.model.SuiStatus
import me.weishu.kernelsu.data.model.SuiApp
import me.weishu.kernelsu.data.sui.SuiBridge
import me.weishu.kernelsu.data.model.ModuleSortPriorityStore
import me.weishu.kernelsu.data.model.SupportLinksStyle
import me.weishu.kernelsu.data.modulestore.StoreSourceKind
import me.weishu.kernelsu.data.themestore.ThemeStoreMode
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.magica.BootCompletedReceiver
import me.weishu.kernelsu.ui.util.execKsud
import me.weishu.kernelsu.ui.util.execKsudResult
import me.weishu.kernelsu.ui.util.getFeaturePersistValue
import me.weishu.kernelsu.ui.util.getFeatureValue
import me.weishu.kernelsu.ui.util.getFeatureStatus
import me.weishu.kernelsu.ui.util.getSELinuxStatusRaw
import me.weishu.kernelsu.ui.util.setSELinuxEnforcing
import org.json.JSONObject
import java.security.SecureRandom

private const val SETTINGS_PREFS = "settings"
private const val KEY_USE_SOFT_REBOOT = "soft_reboot"

/** Partitions backed up by default; the runtime list is filtered by what the device actually has. */
val DEFAULT_BACKUP_PARTITIONS = setOf("boot", "init_boot", "vendor_boot")

/** Prefer soft reboot: always in jailbreak mode, or when the setting is enabled. */
/**
 * Single-quote a value for the root shell.
 *
 * Module ids are already validated by ksud; this keeps the command line
 * unambiguous even if a caller passes something unexpected.
 */
private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

private fun ksudFailure(result: com.topjohnwu.superuser.Shell.Result, fallback: String): String {
    return (result.err + result.out)
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .lastOrNull()
        ?.removePrefix("Error: ")
        ?.takeIf(String::isNotEmpty)
        ?: "$fallback (exit ${result.code})"
}

private fun requireKsud(result: com.topjohnwu.superuser.Shell.Result, fallback: String) {
    check(result.isSuccess) { ksudFailure(result, fallback) }
}

fun isSoftRebootPreferred(): Boolean =
    Natives.isLateLoadMode || ksuApp.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_USE_SOFT_REBOOT, false)

class SettingsRepositoryImpl : SettingsRepository {

    private companion object {
        private const val INTENT_TOKEN_KEY = "intent_token"
        private val secureRandom = SecureRandom()
    }

    private val prefs by lazy {
        ksuApp.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

    override var checkUpdate: Boolean
        get() = prefs.getBoolean("check_update", true)
        set(value) = prefs.edit { putBoolean("check_update", value) }

    override var checkModuleUpdate: Boolean
        get() = prefs.getBoolean("module_check_update", true)
        set(value) = prefs.edit { putBoolean("module_check_update", value) }

    override var themeMode: Int
        get() = prefs.getInt("color_mode", 0)
        set(value) = prefs.edit { putInt("color_mode", value) }

    override var keyColor: Int
        get() = prefs.getInt("key_color", 0)
        set(value) = prefs.edit { putInt("key_color", value) }

    override var colorStyle: String
        get() = prefs.getString("color_style", PaletteStyle.TonalSpot.name) ?: PaletteStyle.TonalSpot.name
        set(value) = prefs.edit { putString("color_style", value) }

    override var colorSpec: String
        get() = prefs.getString("color_spec", ColorSpec.SpecVersion.SPEC_2025.name) ?: ColorSpec.SpecVersion.SPEC_2025.name
        set(value) = prefs.edit { putString("color_spec", value) }

    override var useSystemDynamicColor: Boolean
        get() = prefs.getBoolean("use_system_dynamic_color", keyColor == 0)
        set(value) = prefs.edit { putBoolean("use_system_dynamic_color", value) }

    override var colorGenerationMode: String
        get() = prefs.getString("color_generation_mode", "custom") ?: "custom"
        set(value) = prefs.edit { putString("color_generation_mode", value) }

    override var colorContrast: String
        get() = prefs.getString("color_contrast", "STANDARD") ?: "STANDARD"
        set(value) = prefs.edit { putString("color_contrast", value) }

    override var statsTopLayout: String
        get() = prefs.getString("stats_top_layout", "list") ?: "list"
        set(value) = prefs.edit { putString("stats_top_layout", value) }

    override var listWorkingCardModeHidden: Boolean
        get() = prefs.getBoolean("list_working_card_mode_hidden", false)
        set(value) = prefs.edit { putBoolean("list_working_card_mode_hidden", value) }

    override var hideHomeFingerprint: Boolean
        get() = prefs.getBoolean("hide_home_fingerprint", false)
        set(value) = prefs.edit { putBoolean("hide_home_fingerprint", value) }

    override var hideHomeZygisk: Boolean
        get() = prefs.getBoolean("hide_home_zygisk", false)
        set(value) = prefs.edit { putBoolean("hide_home_zygisk", value) }

    override var hideHomeSusfs: Boolean
        get() = prefs.getBoolean("hide_home_susfs", false)
        set(value) = prefs.edit { putBoolean("hide_home_susfs", value) }

    override var hideHomeMount: Boolean
        get() = prefs.getBoolean("hide_home_mount", false)
        set(value) = prefs.edit { putBoolean("hide_home_mount", value) }

    override var hideHomeKernelFullVersion: Boolean
        get() = prefs.getBoolean("hide_home_kernel_full_version", false)
        set(value) = prefs.edit { putBoolean("hide_home_kernel_full_version", value) }

    override var enablePredictiveBack: Boolean
        get() = prefs.getBoolean("enable_predictive_back", false)
        set(value) = prefs.edit { putBoolean("enable_predictive_back", value) }

    override var enableNavigationBadge: Boolean
        get() = prefs.getBoolean("enable_navigation_badge", true)
        set(value) = prefs.edit { putBoolean("enable_navigation_badge", value) }

    override var pageScale: Float
        get() = prefs.getFloat("page_scale", 1.0f)
        set(value) = prefs.edit { putFloat("page_scale", value) }

    override var moduleDescriptionMaxLines: Int
        get() = prefs.getInt("module_description_max_lines", 4)
        set(value) = prefs.edit { putInt("module_description_max_lines", value) }

    override var enableWebDebugging: Boolean
        get() = prefs.getBoolean("enable_web_debugging", false)
        set(value) = prefs.edit { putBoolean("enable_web_debugging", value) }

    override var homeLayoutStyle: String
        get() = HomeLayoutStyle.fromValue(
            prefs.getString("home_layout_style", HomeLayoutStyle.DEFAULT)
        )
        set(value) = prefs.edit { putString("home_layout_style", HomeLayoutStyle.fromValue(value)) }

    override var supportLinksStyle: String
        get() = SupportLinksStyle.fromValue(
            prefs.getString("support_links_style", SupportLinksStyle.DEFAULT)
        )
        set(value) = prefs.edit { putString("support_links_style", SupportLinksStyle.fromValue(value)) }

    override var supportLinksCustomTitle: String
        get() = prefs.getString("support_links_custom_title", "") ?: ""
        set(value) = prefs.edit { putString("support_links_custom_title", value) }

    override var supportLinksCustomSubtitle: String
        get() = prefs.getString("support_links_custom_subtitle", "") ?: ""
        set(value) = prefs.edit { putString("support_links_custom_subtitle", value) }

    override var supportLinksCustomUrl: String
        get() = prefs.getString("support_links_custom_url", "") ?: ""
        set(value) = prefs.edit { putString("support_links_custom_url", value) }

    override var themeStoreMode: String
        get() = prefs.getString("theme_store_mode", ThemeStoreMode.UNSET) ?: ThemeStoreMode.UNSET
        set(value) = prefs.edit { putString("theme_store_mode", value) }
    override var layoutVariant: String
        get() = LayoutVariant.fromValue(
            prefs.getString("layout_variant", LayoutVariant.M3E)
        )
        set(value) = prefs.edit { putString("layout_variant", LayoutVariant.fromValue(value)) }

    override var moduleSortGroups: Set<ModuleSortGroup>
        get() = ModuleSortPriorityStore.decode(prefs.getString(ModuleSortPriorityStore.Key, null))
        set(value) = prefs.edit { putString(ModuleSortPriorityStore.Key, ModuleSortPriorityStore.encode(value)) }

    override var moduleSortEnabledFirst: Boolean
        get() = prefs.getBoolean("module_sort_enabled_first", false)
        set(value) = prefs.edit { putBoolean("module_sort_enabled_first", value) }

    override var moduleSortCustomOrder: List<String>
        get() = ModuleCustomOrderStore.decode(prefs.getString(ModuleCustomOrderStore.Key, null))
        set(value) = prefs.edit {
            if (value.isEmpty()) remove(ModuleCustomOrderStore.Key)
            else putString(ModuleCustomOrderStore.Key, ModuleCustomOrderStore.encode(value))
        }

    override var superuserShowSystemApps: Boolean
        get() = prefs.getBoolean("show_system_apps", false)
        set(value) = prefs.edit { putBoolean("show_system_apps", value) }

    override var superuserShowOnlyPrimaryUserApps: Boolean
        get() = prefs.getBoolean("show_only_primary_user_apps", false)
        set(value) = prefs.edit { putBoolean("show_only_primary_user_apps", value) }

    override var superuserSortOption: Int
        get() = prefs.getInt("superuser_sort_option", 0)
        set(value) = prefs.edit { putInt("superuser_sort_option", value) }

    override var allowAnyDynamicManager: Boolean
        get() = prefs.getBoolean("allow_any_dynamic_manager", false)
        set(value) = prefs.edit { putBoolean("allow_any_dynamic_manager", value) }

    override var suLogFilters: Set<String>?
        get() = prefs.getStringSet("sulog_filters", null)?.toSet()
        set(filters) = prefs.edit { putStringSet("sulog_filters", filters) }

    override var autoJailbreak: Boolean
        get() = prefs.getBoolean("auto_jailbreak", false)
        set(value) {
            runCatching {
                ksuApp.packageManager.setComponentEnabledSetting(
                    ComponentName(ksuApp, BootCompletedReceiver::class.java),
                    if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }.onFailure {
                Log.e("Settings", "failed to change boot receiver state to $value", it)
            }
            prefs.edit {
                putBoolean("auto_jailbreak", value)
            }
        }

    override var useSoftReboot: Boolean
        get() = prefs.getBoolean(KEY_USE_SOFT_REBOOT, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_SOFT_REBOOT, value) }

    override var repoSourceKind: StoreSourceKind
        get() = StoreSourceKind.fromValue(prefs.getString("repo_source", null))
        set(value) = prefs.edit { putString("repo_source", value.value) }

    override var repoCustomUrl: String
        get() = prefs.getString("repo_custom_url", "").orEmpty()
        set(value) = prefs.edit { putString("repo_custom_url", value) }

    override var repoSelectedRepositoryUrl: String
        get() = prefs.getString("repo_cluster_url", "").orEmpty()
        set(value) = prefs.edit { putString("repo_cluster_url", value) }

    override var backupLocalEnabled: Boolean
        get() = prefs.getBoolean("backup_local_enabled", true)
        set(value) = prefs.edit { putBoolean("backup_local_enabled", value) }

    override var backupWebDavEnabled: Boolean
        get() = prefs.getBoolean("backup_webdav_enabled", false)
        set(value) = prefs.edit { putBoolean("backup_webdav_enabled", value) }

    override var webDavUrl: String
        get() = prefs.getString("backup_webdav_url", "").orEmpty()
        set(value) = prefs.edit { putString("backup_webdav_url", value) }

    override var webDavUsername: String
        get() = prefs.getString("backup_webdav_username", "").orEmpty()
        set(value) = prefs.edit { putString("backup_webdav_username", value) }

    override var webDavPath: String
        get() = prefs.getString("backup_webdav_path", "").orEmpty()
        set(value) = prefs.edit { putString("backup_webdav_path", value) }

    override var backupModulesEnabled: Boolean
        get() = prefs.getBoolean("backup_modules_enabled", true)
        set(value) = prefs.edit { putBoolean("backup_modules_enabled", value) }

    override var backupAllowlistEnabled: Boolean
        get() = prefs.getBoolean("backup_allowlist_enabled", true)
        set(value) = prefs.edit { putBoolean("backup_allowlist_enabled", value) }

    override var backupPartitions: Set<String>
        get() = prefs.getStringSet("backup_partitions", DEFAULT_BACKUP_PARTITIONS)?.toSet()
            ?: DEFAULT_BACKUP_PARTITIONS
        set(value) = prefs.edit { putStringSet("backup_partitions", value) }

    override var backupBeforeFlash: Boolean
        get() = prefs.getBoolean("backup_before_flash", false)
        set(value) = prefs.edit { putBoolean("backup_before_flash", value) }

    override val intentToken: String
        get() {
        val existing = prefs.getString(INTENT_TOKEN_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val token = ByteArray(32).also(secureRandom::nextBytes)
            .joinToString(separator = "") { "%02x".format(it) }
        prefs.edit { putString(INTENT_TOKEN_KEY, token) }
        return token
    }

    override suspend fun getSuCompatStatus(): String = getFeatureStatus("su_compat")

    override suspend fun getSuCompatPersistValue(): Long? = getFeaturePersistValue("su_compat")

    override fun isSuEnabled(): Boolean = Natives.isSuEnabled()

    override fun setSuEnabled(enabled: Boolean): Boolean = Natives.setSuEnabled(enabled)

    override fun setSuCompatModePref(mode: Int) = prefs.edit { putInt("su_compat_mode", mode) }

    override fun getSuCompatModePref(): Int = prefs.getInt("su_compat_mode", 0)

    override suspend fun getKernelUmountStatus(): String = getFeatureStatus("kernel_umount")

    override fun isKernelUmountEnabled(): Boolean = Natives.isKernelUmountEnabled()

    override fun setKernelUmountEnabled(enabled: Boolean): Boolean = Natives.setKernelUmountEnabled(enabled)

    override suspend fun getSelinuxHideStatus(): String = getFeatureStatus("selinux_hide")

    override fun isSelinuxHideEnabled(): Boolean = Natives.isSelinuxHideEnabled()

    override fun setSelinuxHideEnabled(enabled: Boolean): Int = Natives.setSelinuxHideEnabled(enabled)

    override suspend fun getMountViewFilterStatus(): String = getFeatureStatus("mount_view_filter")

    override suspend fun isMountViewFilterEnabled(): Boolean = getFeatureValue("mount_view_filter") == 1L

    override suspend fun setMountViewFilterEnabled(enabled: Boolean): Result<Unit> = runCatching {
        requireKsud(
            execKsudResult("feature set mount_view_filter ${if (enabled) 1 else 0}"),
            "Mount view filter configuration failed",
        )
    }

    override suspend fun getSELinuxMode(): String = withContext(Dispatchers.IO) { getSELinuxStatusRaw() }

    override suspend fun setSELinuxMode(enforcing: Boolean): Boolean =
        withContext(Dispatchers.IO) { setSELinuxEnforcing(enforcing) }

    override suspend fun getSulogStatus(): String = getFeatureStatus("sulog")

    override suspend fun getAvcSpoofStatus(): String = getFeatureStatus("avc_spoof")

    override fun isAvcSpoofEnabled(): Boolean = Natives.isAvcSpoofEnabled()

    override fun setAvcSpoofEnabled(enabled: Boolean): Boolean = Natives.setAvcSpoofEnabled(enabled)

    override suspend fun getSulogPersistValue(): Long? = getFeaturePersistValue("sulog")
    override suspend fun getSulogValue(): Long? = getFeatureValue("sulog")

    override fun setSulogEnabled(enabled: Boolean): Boolean = execKsud("feature set sulog ${if (enabled) 1 else 0}", true)

    override suspend fun getFolkMountStatus(): Result<FolkMountStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val result = execKsudResult("mount status --json")
            check(result.isSuccess) { "ksud mount status failed (exit ${result.code})" }
            val payload = result.out.firstOrNull { it.isNotBlank() }
                ?: error("ksud mount status returned no payload")
            val json = JSONObject(payload)
            val schema = json.optInt("schema_version", -1)
            check(schema == FolkMountStatus.SCHEMA_VERSION) {
                "unsupported folk mount schema_version $schema"
            }
            FolkMountStatus.fromJson(json)
        }
    }

    override suspend fun getFolkZygiskStatus(): Result<FolkZygiskStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = execKsudResult("zygisk status --json")
                check(result.isSuccess) { "ksud zygisk status failed (exit ${result.code})" }
                val payload = result.out.filter { it.isNotBlank() }
                check(payload.size == 1) { "Invalid ksud zygisk status output" }
                FolkZygiskStatus.fromJson(JSONObject(payload.single()))
            }
        }

    override suspend fun setFolkZygiskEnabled(enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (enabled) {
                    val status = getFolkZygiskStatus().getOrThrow()
                    if (!status.engineAvailable) {
                        requireKsud(
                            execKsudResult("zygisk install-engine"),
                            "Engine installation failed",
                        )
                    }
                }
                val command = if (enabled) "enable" else "disable"
                requireKsud(
                    execKsudResult("zygisk $command"),
                    "FolkZygisk configuration failed",
                )
            }
        }

    override suspend fun recoverFolkZygisk(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireKsud(
                execKsudResult("zygisk recover"),
                "FolkZygisk recovery failed",
            )
        }
    }

    override suspend fun getSuiStatus(): Result<SuiStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val result = execKsudResult("sui status --json")
            check(result.isSuccess) { "ksud sui status failed (exit ${result.code})" }
            val payload = result.out.filter { it.isNotBlank() }
            check(payload.size == 1) { "Invalid ksud sui status output" }
            SuiStatus.fromJson(JSONObject(payload.single()))
        }
    }

    override suspend fun setSuiEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val command = if (enabled) "sui enable" else "sui disable"
            check(execKsudResult(command).isSuccess) { "ksud $command failed" }
        }
    }

    override suspend fun repairSui(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { check(execKsudResult("sui repair").isSuccess) { "ksud sui repair failed" } }
    }

    override suspend fun suiProbeManagement(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val service = SuiBridge.service() ?: error("Sui bridge returned no binder")
            val uid = android.os.Process.myUid()
            val flags = service.getFlagsForUid(uid, SuiBridge.MASK_PERMISSION)
            // Idempotent write: it throws unless Sui accepts this app as its manager.
            service.updateFlagsForUid(uid, SuiBridge.MASK_PERMISSION, flags)
            "binder=ok uid=$uid flags=$flags"
        }
    }

    override suspend fun suiAppPermissions(): Result<List<SuiApp>> = withContext(Dispatchers.IO) {
        runCatching {
            val service = SuiBridge.service() ?: error("Sui bridge returned no binder")
            val pm = ksuApp.packageManager
            val entries = LinkedHashMap<Int, SuiApp>()
            val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            for (resolved in pm.queryIntentActivities(launcher, 0)) {
                val info = resolved.activityInfo?.applicationInfo ?: continue
                val uid = info.uid
                // Root, shell and system uids are not app permission targets.
                if (uid < 10000 || entries.containsKey(uid)) {
                    continue
                }
                entries[uid] = SuiApp(
                    uid = uid,
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    mode = service.getFlagsForUid(uid, SuiApp.MODE_MASK),
                    applicationInfo = info,
                    requestsShizuku = requestsShizuku(pm, info.packageName),
                )
            }
            entries.values.sortedBy { it.label.lowercase() }
        }
    }

    override suspend fun setSuiAppMode(uid: Int, mode: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val service = SuiBridge.service() ?: error("Sui bridge returned no binder")
            service.updateFlagsForUid(uid, SuiApp.MODE_MASK, mode)
        }
    }

    override suspend fun suiLaunchableAppCount(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            ksuApp.packageManager.queryIntentActivities(launcher, 0)
                .mapNotNull { it.activityInfo?.applicationInfo?.uid }
                .filter { it >= 10000 }
                .distinct()
                .size
        }
    }

    override suspend fun suiDefaultMode(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            SuiBridge.defaultMode() ?: error("Sui did not report the default permission")
        }
    }

    override suspend fun setSuiDefaultMode(mode: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(SuiBridge.setDefaultMode(mode and SuiApp.MODE_MASK)) {
                "Sui rejected the default permission"
            }
        }
    }

    override suspend fun suiSettingsFlags(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            SuiBridge.globalSettings() ?: error("Sui did not report global settings")
        }
    }

    override suspend fun setSuiSettingsFlags(flags: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(SuiBridge.setGlobalSettings(flags)) { "Sui rejected the setting" }
        }
    }

    override suspend fun suiHiddenUids(): Result<List<Int>> = withContext(Dispatchers.IO) {
        runCatching {
            SuiBridge.hiddenUids()?.toList() ?: error("Sui did not report hidden apps")
        }
    }

    private fun requestsShizuku(pm: PackageManager, packageName: String): Boolean = runCatching {
        pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(SuiApp.PERMISSION_API_V23) == true
    }.getOrDefault(false)

    override suspend fun setFolkMountMode(mode: FolkMountMode): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // `mode.token` is a fixed enum value; never interpolate arbitrary input.
            requireKsud(
                execKsudResult("mount set-mode ${mode.token}"),
                "ksud mount set-mode ${mode.token} failed",
            )
        }
    }

    override suspend fun getNomountStatus(): Result<NomountStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val result = execKsudResult("nomount status --json")
            check(result.isSuccess) { "ksud nomount status failed (exit ${result.code})" }
            val payload = result.out.firstOrNull { it.isNotBlank() }
                ?: error("ksud nomount status returned no payload")
            NomountStatus.fromJson(JSONObject(payload))
        }
    }

    override suspend fun setNomountIsolated(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val state = if (enabled) "on" else "off"
            check(execKsud("nomount uid isolated $state", true)) {
                "ksud nomount uid isolated $state failed"
            }
        }
    }

    override suspend fun injectNomountRules(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(execKsud("nomount inject", true)) { "ksud nomount inject failed" }
        }
    }

    override suspend fun clearNomountRules(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(execKsud("nomount clear", true)) { "ksud nomount clear failed" }
        }
    }

    override suspend fun clearNomountEverything(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Rules first, then the exclusions: if a step fails, the failure is
            // reported instead of leaving a half-cleared state looking done.
            check(execKsud("nomount clear", true)) { "ksud nomount clear failed" }
            check(execKsud("nomount uid set", true)) { "ksud nomount uid set failed" }
        }
    }

    override suspend fun setNomountUids(uids: List<Int>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // UIDs are integers from the app list; nothing user-typed reaches the shell.
            val list = uids.distinct().sorted().joinToString(" ")
            // An empty list clears every exclusion.
            val command = if (list.isEmpty()) "nomount uid set" else "nomount uid set $list"
            check(execKsud(command, true)) { "ksud nomount uid set failed" }
        }
    }

    override suspend fun hotLoadNomountModule(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(execKsud("nomount hot-load ${shellQuote(id)}", true)) {
                "ksud nomount hot-load failed"
            }
        }
    }

    override suspend fun hotUnloadNomountModule(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(execKsud("nomount hot-unload ${shellQuote(id)}", true)) {
                "ksud nomount hot-unload failed"
            }
        }
    }

    override suspend fun setModuleSkip(id: String, skip: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val flag = if (skip) "" else " --off"
            check(execKsud("module skip ${shellQuote(id)}$flag", true)) {
                "ksud module skip failed"
            }
        }
    }

    override suspend fun getAdbRootStatus(): String = getFeatureStatus("adb_root")

    override suspend fun getShellSuAdbOnlyStatus(): String = getFeatureStatus("shell_su_adb_only")
    override suspend fun getShellSuAdbOnlyPersistValue(): Long? = getFeaturePersistValue("shell_su_adb_only")
    override suspend fun getShellSuAdbOnlyValue(): Long? = getFeatureValue("shell_su_adb_only")
    override fun setShellSuAdbOnlyEnabled(enabled: Boolean): Boolean =
        execKsud("feature set shell_su_adb_only ${if (enabled) 1 else 0}", true)

    override suspend fun getAdbRootPersistValue(): Long? = getFeaturePersistValue("adb_root")
    override suspend fun getAdbRootValue(): Long? = getFeatureValue("adb_root")

    override fun setAdbRootEnabled(enabled: Boolean): Boolean =
        if (execKsud("feature set adb_root ${if (enabled) 1 else 0}", true)) {
            ShellUtils.fastCmd("setprop ctl.restart adbd")
            true
        } else {
            false
        }

    override suspend fun getBootHideStatus(): String =
        execKsudResult("boot-hide status").out.firstOrNull()?.trim().orEmpty()

    override fun setBootHideEnabled(enabled: Boolean): Boolean =
        execKsud("boot-hide ${if (enabled) "enable" else "disable"}", true)

    override fun isDefaultUmountModules(): Boolean = Natives.isDefaultUmountModules()

    override fun setDefaultUmountModules(enabled: Boolean): Boolean = Natives.setDefaultUmountModules(enabled)

    override fun isLkmMode(): Boolean = Natives.isLkmMode

    override fun execKsudFeatureSave(): Boolean = execKsud("feature save", true)
}
