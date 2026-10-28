package me.weishu.kernelsu.ui.screen.settings

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.LabelOff
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkMountProvider
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.data.model.LayoutVariant
import me.weishu.kernelsu.data.model.NomountDriverState
import me.weishu.kernelsu.data.model.SupportLinksStyle
import me.weishu.kernelsu.data.themestore.ThemeStoreMode
import me.weishu.kernelsu.ui.component.KsuIsValid
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.screen.nomount.nomountUnavailableReason
import me.weishu.kernelsu.ui.component.profile.dialogs.SingleSelectDialog
import me.weishu.kernelsu.ui.util.LauncherIconUtils

/**
 * Contents of each settings category, split out of the old flat hub. Every function emits its own
 * [SegmentedColumn] groups; the hosting screen owns the scroll container, so these stay layout-free
 * and mirror each other's spacing.
 */

private val GroupModifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)

/** Which text of the custom home card the shared text dialog is currently editing. */
private enum class SupportLinksCustomField { TITLE, SUBTITLE, URL }

/** Whether [value] is a link the home card can safely hand to the browser. */
internal fun isSupportedLinkUrl(value: String): Boolean =
    Uri.parse(value.trim()).scheme?.lowercase() in setOf("http", "https")

@Composable
fun GeneralCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    KsuIsValid {
        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.SystemUpdate,
                        title = stringResource(id = R.string.settings_check_update),
                        summary = stringResource(id = R.string.settings_check_update_summary),
                        checked = uiState.checkUpdate,
                        onCheckedChange = actions.onSetCheckUpdate
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.SystemUpdateAlt,
                        title = stringResource(id = R.string.settings_module_check_update),
                        summary = stringResource(id = R.string.settings_check_update_summary),
                        checked = uiState.checkModuleUpdate,
                        onCheckedChange = actions.onSetCheckModuleUpdate
                    )
                }
            )
        )
    }

    val profileTemplate = stringResource(id = R.string.settings_profile_template)
    KsuIsValid {
        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf {
                SegmentedListItem(
                    onClick = actions.onOpenProfileTemplate,
                    headlineContent = { Text(profileTemplate) },
                    supportingContent = { Text(stringResource(id = R.string.settings_profile_template_summary)) },
                    leadingContent = { Icon(Icons.Filled.Description, profileTemplate) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
        )
    }

    val appLanguage = stringResource(id = R.string.settings_app_language)
    SegmentedColumn(
        modifier = GroupModifier,
        content = listOf {
            SegmentedListItem(
                onClick = actions.onOpenLanguage,
                headlineContent = { Text(appLanguage) },
                supportingContent = { Text(stringResource(id = R.string.settings_app_language_summary)) },
                leadingContent = { Icon(Icons.Filled.Translate, appLanguage) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            )
        }
    )
}

@Composable
fun AppearanceCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    val context = LocalContext.current
    var launcherIconStyle by remember { mutableStateOf(LauncherIconUtils.currentStyle(context)) }
    var showLauncherIconDialog by remember { mutableStateOf(false) }
    val launcherIconLabels = LauncherIconUtils.styles.associateWith { style ->
        stringResource(
            when (style) {
                LauncherIconUtils.STYLE_GEOMETRY -> R.string.launcher_icon_style_geometry
                LauncherIconUtils.STYLE_KERNELSU -> R.string.launcher_icon_style_kernelsu
                else -> R.string.launcher_icon_style_default
            }
        )
    }

    SegmentedColumn(
        modifier = GroupModifier,
        content = buildList {
            add {
                SegmentedListItem(
                    onClick = actions.onOpenTheme,
                    headlineContent = { Text(stringResource(id = R.string.settings_theme)) },
                    supportingContent = { Text(stringResource(id = R.string.settings_theme_summary)) },
                    leadingContent = { Icon(Icons.Filled.Palette, stringResource(id = R.string.settings_theme)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
            add {
                SegmentedListItem(
                    onClick = actions.onOpenWallpaper,
                    headlineContent = { Text(stringResource(id = R.string.wallpaper_title)) },
                    supportingContent = { Text(stringResource(id = R.string.settings_wallpaper_summary)) },
                    leadingContent = { Icon(Icons.Filled.Wallpaper, stringResource(id = R.string.wallpaper_title)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
            add {
                SegmentedListItem(
                    onClick = actions.onOpenFont,
                    headlineContent = { Text(stringResource(id = R.string.font_title)) },
                    supportingContent = { Text(stringResource(id = R.string.settings_font_summary)) },
                    leadingContent = { Icon(Icons.Filled.FontDownload, stringResource(id = R.string.font_title)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
            add {
                SegmentedListItem(
                    onClick = actions.onOpenThemeStore,
                    headlineContent = { Text(stringResource(id = R.string.theme_store_title)) },
                    supportingContent = { Text(stringResource(id = R.string.theme_store_subtitle)) },
                    leadingContent = { Icon(Icons.Filled.ShoppingBag, stringResource(id = R.string.theme_store_title)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
            add {
                val storeModeValues = listOf(ThemeStoreMode.BUILTIN, ThemeStoreMode.COMPAT)
                val storeModeItems = storeModeValues.map { mode ->
                    stringResource(
                        when (mode) {
                            ThemeStoreMode.COMPAT -> R.string.theme_store_mode_compat
                            else -> R.string.theme_store_mode_builtin
                        }
                    )
                }
                val selectedStoreMode =
                    storeModeValues.indexOf(ThemeStoreMode.normalize(uiState.themeStoreMode))
                        .coerceAtLeast(0)
                SegmentedDropdownItem(
                    icon = Icons.Filled.Language,
                    title = stringResource(id = R.string.settings_theme_store_mode),
                    summary = stringResource(id = R.string.settings_theme_store_mode_summary),
                    items = storeModeItems,
                    selectedIndex = selectedStoreMode,
                    onItemSelected = { index ->
                        actions.onSetThemeStoreMode(
                            storeModeValues.getOrElse(index) { ThemeStoreMode.BUILTIN }
                        )
                    }
                )
            }
            add {
                SegmentedListItem(
                    onClick = { showLauncherIconDialog = true },
                    headlineContent = { Text(stringResource(id = R.string.settings_launcher_icon)) },
                    supportingContent = {
                        Text(launcherIconLabels[launcherIconStyle] ?: launcherIconStyle)
                    },
                    leadingContent = { Icon(Icons.Filled.Apps, stringResource(id = R.string.settings_launcher_icon)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
        }
    )

    // Layout gathers the home screen's shape, the search pages' title treatment and the bottom
    // card, so everything that reshapes a page sits together. The three text rows below the card
    // picker only apply to the custom card, so they appear alongside it.
    var editingCustomField by remember { mutableStateOf<SupportLinksCustomField?>(null) }
    val customCardSelected = uiState.supportLinksStyle == SupportLinksStyle.CUSTOM
    SegmentedColumn(
        modifier = GroupModifier,
        title = stringResource(R.string.settings_section_layout),
    ) {
        item {
            val supportedHomeLayouts = HomeLayoutStyle.supported
            val homeLayoutItems = supportedHomeLayouts.map { style ->
                stringResource(
                    when (style) {
                        HomeLayoutStyle.GRID -> R.string.settings_home_layout_grid
                        HomeLayoutStyle.FOCUS -> R.string.settings_home_layout_focus
                        HomeLayoutStyle.DASHBOARD -> R.string.settings_home_layout_dashboard
                        HomeLayoutStyle.STATS -> R.string.settings_home_layout_stats
                        else -> R.string.settings_home_layout_circle
                    }
                )
            }
            val selectedHomeLayout =
                supportedHomeLayouts.indexOf(uiState.homeLayoutStyle).coerceAtLeast(0)
            SegmentedDropdownItem(
                icon = Icons.Filled.GridView,
                title = stringResource(id = R.string.settings_home_layout),
                summary = stringResource(id = R.string.settings_home_layout_summary),
                items = homeLayoutItems,
                selectedIndex = selectedHomeLayout,
                onItemSelected = { index ->
                    actions.onSetHomeLayoutStyle(
                        supportedHomeLayouts.getOrElse(index) { HomeLayoutStyle.DEFAULT }
                    )
                }
            )
        }
        item(visible = uiState.homeLayoutStyle == HomeLayoutStyle.STATS) {
            val statsTopItems = listOf(
                stringResource(R.string.settings_stats_top_layout_list),
                stringResource(R.string.settings_stats_top_layout_grid),
            )
            SegmentedDropdownItem(
                icon = Icons.Rounded.SpaceDashboard,
                title = stringResource(id = R.string.settings_stats_top_layout),
                items = statsTopItems,
                selectedIndex = if (uiState.statsTopLayout == "grid") 1 else 0,
                onItemSelected = { index ->
                    actions.onSetStatsTopLayout(if (index == 1) "grid" else "list")
                }
            )
        }
        item(
            visible = uiState.homeLayoutStyle == HomeLayoutStyle.CIRCLE ||
                (uiState.homeLayoutStyle == HomeLayoutStyle.STATS && uiState.statsTopLayout != "grid")
        ) {
            SegmentedSwitchItem(
                icon = Icons.AutoMirrored.Rounded.LabelOff,
                title = stringResource(id = R.string.settings_home_hide_status_tag),
                summary = stringResource(id = R.string.settings_home_hide_status_tag_summary),
                checked = uiState.listWorkingCardModeHidden,
                onCheckedChange = actions.onSetListWorkingCardModeHidden
            )
        }
        item {
            val supportedLayoutVariants = LayoutVariant.supported
            val layoutVariantItems = supportedLayoutVariants.map { variant ->
                stringResource(
                    when (variant) {
                        LayoutVariant.M3E -> R.string.settings_layout_variant_m3e
                        else -> R.string.settings_layout_variant_traditional
                    }
                )
            }
            val selectedLayoutVariant =
                supportedLayoutVariants.indexOf(uiState.layoutVariant).coerceAtLeast(0)
            SegmentedDropdownItem(
                icon = Icons.Rounded.SpaceDashboard,
                title = stringResource(id = R.string.settings_layout_variant),
                summary = stringResource(id = R.string.settings_layout_variant_summary),
                items = layoutVariantItems,
                selectedIndex = selectedLayoutVariant,
                onItemSelected = { index ->
                    actions.onSetLayoutVariant(
                        supportedLayoutVariants.getOrElse(index) { LayoutVariant.M3E }
                    )
                }
            )
        }
        item {
            val supportedSupportLinks = SupportLinksStyle.supported
            val supportLinksItems = supportedSupportLinks.map { style ->
                stringResource(
                    when (style) {
                        SupportLinksStyle.LEGACY -> R.string.settings_support_links_legacy
                        SupportLinksStyle.CUSTOM -> R.string.settings_support_links_custom
                        SupportLinksStyle.HIDDEN -> R.string.settings_support_links_hidden
                        else -> R.string.settings_support_links_finasu
                    }
                )
            }
            val selectedSupportLinks =
                supportedSupportLinks.indexOf(uiState.supportLinksStyle).coerceAtLeast(0)
            SegmentedDropdownItem(
                icon = Icons.Filled.Info,
                title = stringResource(id = R.string.settings_support_links),
                summary = stringResource(id = R.string.settings_support_links_summary),
                items = supportLinksItems,
                selectedIndex = selectedSupportLinks,
                onItemSelected = { index ->
                    actions.onSetSupportLinksStyle(
                        supportedSupportLinks.getOrElse(index) { SupportLinksStyle.DEFAULT }
                    )
                }
            )
        }
        item(visible = customCardSelected) {
            SegmentedListItem(
                onClick = { editingCustomField = SupportLinksCustomField.TITLE },
                headlineContent = { Text(stringResource(R.string.settings_support_links_custom_title)) },
                supportingContent = {
                    Text(
                        uiState.supportLinksCustomTitle.ifBlank {
                            stringResource(R.string.settings_support_links_custom_title_summary)
                        }
                    )
                },
                leadingContent = { Icon(Icons.Rounded.TextFields, null) },
            )
        }
        item(visible = customCardSelected) {
            SegmentedListItem(
                onClick = { editingCustomField = SupportLinksCustomField.SUBTITLE },
                headlineContent = { Text(stringResource(R.string.settings_support_links_custom_subtitle)) },
                supportingContent = {
                    Text(
                        uiState.supportLinksCustomSubtitle.ifBlank {
                            stringResource(R.string.settings_support_links_custom_subtitle_summary)
                        }
                    )
                },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Notes, null) },
            )
        }
        item(visible = customCardSelected) {
            SegmentedListItem(
                onClick = { editingCustomField = SupportLinksCustomField.URL },
                headlineContent = { Text(stringResource(R.string.settings_support_links_custom_url)) },
                supportingContent = {
                    Text(
                        uiState.supportLinksCustomUrl.ifBlank {
                            stringResource(R.string.settings_support_links_custom_url_summary)
                        }
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Link, null) },
            )
        }
    }

    when (editingCustomField) {
        SupportLinksCustomField.TITLE -> TextInputDialog(
            title = stringResource(R.string.settings_support_links_custom_title),
            label = stringResource(R.string.settings_support_links_custom_title_summary),
            initialValue = uiState.supportLinksCustomTitle,
            onConfirm = { value ->
                actions.onSetSupportLinksCustomTitle(value)
                editingCustomField = null
            },
            onDismiss = { editingCustomField = null },
        )
        SupportLinksCustomField.SUBTITLE -> TextInputDialog(
            title = stringResource(R.string.settings_support_links_custom_subtitle),
            label = stringResource(R.string.settings_support_links_custom_subtitle_summary),
            initialValue = uiState.supportLinksCustomSubtitle,
            onConfirm = { value ->
                actions.onSetSupportLinksCustomSubtitle(value)
                editingCustomField = null
            },
            onDismiss = { editingCustomField = null },
        )
        SupportLinksCustomField.URL -> TextInputDialog(
            title = stringResource(R.string.settings_support_links_custom_url),
            label = stringResource(R.string.settings_support_links_custom_url_summary),
            initialValue = uiState.supportLinksCustomUrl,
            // A malformed link would silently do nothing on the home screen, so refuse it here.
            onConfirm = { value ->
                if (isSupportedLinkUrl(value)) {
                    actions.onSetSupportLinksCustomUrl(value)
                    editingCustomField = null
                } else {
                    Toast.makeText(
                        context,
                        R.string.settings_support_links_custom_url_invalid,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { editingCustomField = null },
        )
        null -> Unit
    }

    AdvancedTitleSettings(modifier = GroupModifier)

    // The identifying rows only, mirroring FP: the base facts of the card stay visible everywhere.
    val zygiskRowPresent = uiState.folkZygiskStatus?.providesImplementation() == true
    SegmentedColumn(
        modifier = GroupModifier,
        title = stringResource(R.string.settings_home_info),
    ) {
        item {
            SegmentedSwitchItem(
                icon = Icons.Filled.Fingerprint,
                title = stringResource(R.string.settings_hide_home_fingerprint),
                summary = stringResource(R.string.settings_hide_home_fingerprint_summary),
                checked = uiState.hideHomeFingerprint,
                onCheckedChange = actions.onSetHideHomeFingerprint,
            )
        }
        item(visible = zygiskRowPresent) {
            SegmentedSwitchItem(
                icon = Icons.Filled.Vaccines,
                title = stringResource(R.string.settings_hide_home_zygisk),
                summary = stringResource(R.string.settings_hide_home_zygisk_summary),
                checked = uiState.hideHomeZygisk,
                onCheckedChange = actions.onSetHideHomeZygisk,
            )
        }
        item(visible = uiState.susfsDetected) {
            SegmentedSwitchItem(
                icon = Icons.Rounded.VisibilityOff,
                title = stringResource(R.string.settings_hide_home_susfs),
                summary = stringResource(R.string.settings_hide_home_susfs_summary),
                checked = uiState.hideHomeSusfs,
                onCheckedChange = actions.onSetHideHomeSusfs,
            )
        }
        item(visible = uiState.folkMountStatus != null && !uiState.folkMountReadError) {
            SegmentedSwitchItem(
                icon = Icons.Filled.Layers,
                title = stringResource(R.string.settings_hide_home_mount),
                summary = stringResource(R.string.settings_hide_home_mount_summary),
                checked = uiState.hideHomeMount,
                onCheckedChange = actions.onSetHideHomeMount,
            )
        }
    }

    if (showLauncherIconDialog) {
        SingleSelectDialog(
            title = stringResource(id = R.string.settings_launcher_icon),
            items = LauncherIconUtils.styles,
            selectedItem = launcherIconStyle,
            itemTitle = { style -> launcherIconLabels[style] ?: style },
            onConfirm = { style ->
                if (LauncherIconUtils.setStyle(context, style)) {
                    launcherIconStyle = style
                }
            },
            onDismiss = { showLauncherIconDialog = false }
        )
    }

    DisplayDensitySection(uiState, actions)
}

@Composable
fun BehaviorCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    NavigationSettingsSection(uiState, actions)

    KsuIsValid {
        val suCompatModeItems = listOf(
            stringResource(id = R.string.settings_mode_enable_by_default),
            stringResource(id = R.string.settings_mode_disable_until_reboot),
            stringResource(id = R.string.settings_mode_disable_always),
        )

        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    val suSummary = when (uiState.suCompatStatus) {
                        "forced" -> stringResource(R.string.feature_status_forced_summary)
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_sucompat_summary)
                    }
                    SegmentedDropdownItem(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = stringResource(id = R.string.settings_sucompat),
                        summary = suSummary,
                        items = suCompatModeItems,
                        enabled = uiState.suCompatStatus == "supported",
                        selectedIndex = uiState.suCompatMode,
                        onItemSelected = actions.onSetSuCompatMode
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.RestartAlt,
                        title = stringResource(id = R.string.settings_soft_reboot),
                        summary = stringResource(id = R.string.settings_soft_reboot_summary),
                        enabled = !uiState.isLateLoadMode,
                        checked = uiState.isLateLoadMode || uiState.useSoftReboot,
                        onCheckedChange = actions.onSetUseSoftReboot
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.DeveloperMode,
                        title = stringResource(id = R.string.enable_web_debugging),
                        summary = stringResource(id = R.string.enable_web_debugging_summary),
                        checked = uiState.enableWebDebugging,
                        onCheckedChange = actions.onSetEnableWebDebugging
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.FlashOn,
                        title = stringResource(id = R.string.settings_auto_jailbreak),
                        summary = stringResource(id = R.string.settings_auto_jailbreak_summary),
                        enabled = uiState.isLateLoadMode,
                        checked = uiState.autoJailbreak,
                        onCheckedChange = actions.onSetAutoJailbreak
                    )
                }
            )
        )
    }

    ModuleCardSettingsSection()
    ModuleBannerSettingsSection()
}

@Composable
fun FunctionCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    KsuIsValid {
        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    val umountSummary = when (uiState.kernelUmountStatus) {
                        "forced" -> stringResource(R.string.feature_status_forced_summary)
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_kernel_umount_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.LayersClear,
                        title = stringResource(id = R.string.settings_kernel_umount),
                        summary = umountSummary,
                        enabled = uiState.kernelUmountStatus == "supported",
                        checked = uiState.isKernelUmountEnabled,
                        onCheckedChange = actions.onSetKernelUmountEnabled
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.AutoMirrored.Filled.Rule,
                        title = stringResource(id = R.string.settings_umount_modules_default),
                        summary = stringResource(id = R.string.settings_umount_modules_default_summary),
                        checked = uiState.isDefaultUmountModules,
                        onCheckedChange = actions.onSetDefaultUmountModules
                    )
                },
                {
                    SegmentedListItem(
                        headlineContent = {
                            Text(stringResource(R.string.umount_paths_title))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.umount_paths_summary))
                        },
                        leadingContent = { Icon(Icons.Filled.LayersClear, null) },
                        onClick = actions.onOpenUmountPaths,
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.PhonelinkLock,
                        title = stringResource(id = R.string.settings_finasu_hide),
                        summary = stringResource(id = R.string.settings_finasu_hide_summary),
                        checked = uiState.isBootHideEnabled,
                        onCheckedChange = actions.onSetBootHideEnabled
                    )
                },
                {
                    val mountViewFilterSummary = when (uiState.mountViewFilterStatus) {
                        "forced" -> stringResource(R.string.feature_status_forced_summary)
                        "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(R.string.feature_status_managed_summary)
                        "unavailable" -> stringResource(R.string.settings_mount_view_filter_unavailable)
                        else -> stringResource(R.string.settings_mount_view_filter_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Rounded.VisibilityOff,
                        title = stringResource(R.string.settings_mount_view_filter),
                        summary = mountViewFilterSummary,
                        enabled = uiState.mountViewFilterStatus == "supported" &&
                            !uiState.isMountViewFilterWriting,
                        checked = uiState.isMountViewFilterEnabled,
                        onCheckedChange = actions.onSetMountViewFilterEnabled,
                    )
                },
                {
                    val avcSpoofSummary = when (uiState.avcSpoofStatus) {
                        "forced" -> stringResource(R.string.feature_status_forced_summary)
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_avc_spoof_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Security,
                        title = stringResource(id = R.string.settings_avc_spoof),
                        summary = avcSpoofSummary,
                        enabled = uiState.avcSpoofStatus == "supported" && !uiState.isAvcSpoofWriting,
                        checked = uiState.isAvcSpoofEnabled,
                        onCheckedChange = actions.onSetAvcSpoofEnabled
                    )
                },
                {
                    val sulogSummary = when (uiState.sulogStatus) {
                        "forced" -> stringResource(R.string.feature_status_forced_summary)
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_sulog_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = stringResource(id = R.string.settings_sulog),
                        summary = sulogSummary,
                        enabled = uiState.sulogStatus == "supported",
                        checked = uiState.isSulogEnabled,
                        onCheckedChange = actions.onSetSulogEnabled
                    )
                }
            )
        )

        // §6.1: hide the dynamic-manager toggle entirely when the kernel cannot support it, instead
        // of offering a switch that does nothing.
        if (uiState.isDynamicManagerAvailable) {
            SegmentedColumn(
                modifier = GroupModifier,
                content = listOf {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = stringResource(id = R.string.allow_any_dynamic_manager),
                        summary = stringResource(id = R.string.allow_any_dynamic_manager_summary),
                        checked = uiState.allowAnyDynamicManager,
                        onCheckedChange = actions.onSetAllowAnyDynamicManager
                    )
                }
            )
        }

        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    FolkZygiskStatusItem(uiState, actions)
                },
                {
                    SuiStatusItem(uiState, actions)
                },
                {
                    val folkMountStatus = uiState.folkMountStatus
                    val available = folkMountStatus != null && !uiState.folkMountReadError
                    val mode = folkMountStatus?.configuredMode
                    val modeItems = listOf(
                        stringResource(id = R.string.settings_folk_mount_mode_auto),
                        stringResource(id = R.string.settings_folk_mount_mode_builtin),
                        stringResource(id = R.string.settings_folk_mount_mode_metamodule),
                        stringResource(id = R.string.settings_folk_mount_mode_nomount),
                        stringResource(id = R.string.settings_folk_mount_mode_off),
                    )
                    val summary = when {
                        uiState.isFolkMountLoading ->
                            stringResource(id = R.string.settings_folk_mount_summary)

                        !available ->
                            stringResource(id = R.string.settings_folk_mount_unavailable_summary)

                        else -> {
                            val base = stringResource(
                                id = when (mode) {
                                    FolkMountMode.AUTO -> R.string.settings_folk_mount_mode_auto_summary
                                    FolkMountMode.BUILTIN -> R.string.settings_folk_mount_mode_builtin_summary
                                    FolkMountMode.METAMODULE -> R.string.settings_folk_mount_mode_metamodule_summary
                                    FolkMountMode.NOMOUNT -> R.string.settings_folk_mount_mode_nomount_summary
                                    FolkMountMode.OFF -> R.string.settings_folk_mount_mode_off_summary
                                    null -> R.string.settings_folk_mount_summary
                                }
                            )
                            val bootLine = when (folkMountStatus.bootProvider) {
                                FolkMountProvider.BUILTIN -> stringResource(
                                    id = R.string.settings_folk_mount_boot_provider,
                                    stringResource(id = R.string.settings_folk_mount_provider_builtin)
                                )

                                FolkMountProvider.METAMODULE -> stringResource(
                                    id = R.string.settings_folk_mount_boot_provider,
                                    stringResource(id = R.string.settings_folk_mount_provider_metamodule)
                                )

                                FolkMountProvider.NOMOUNT -> stringResource(
                                    id = R.string.settings_folk_mount_boot_provider,
                                    stringResource(id = R.string.settings_folk_mount_provider_nomount)
                                )

                                FolkMountProvider.NONE -> stringResource(
                                    id = R.string.settings_folk_mount_provider_none
                                )

                                null -> stringResource(id = R.string.settings_folk_mount_boot_provider_unknown)
                            }
                            val compatHint = when {
                                mode == FolkMountMode.BUILTIN && folkMountStatus.metamoduleEnabled ->
                                    stringResource(id = R.string.settings_folk_mount_metamodule_compat_hint)

                                mode == FolkMountMode.NOMOUNT &&
                                    folkMountStatus.bootProvider != null &&
                                    folkMountStatus.bootProvider != FolkMountProvider.NOMOUNT ->
                                    folkMountStatus.bootWarning
                                        ?: stringResource(
                                            id = R.string.settings_folk_mount_nomount_unavailable_hint
                                        )

                                else -> null
                            }
                            listOfNotNull(
                                base,
                                bootLine,
                                stringResource(id = R.string.settings_folk_mount_reboot_hint),
                                compatHint,
                            ).joinToString("\n")
                        }
                    }
                    SegmentedDropdownItem(
                        icon = Icons.Filled.Layers,
                        title = stringResource(id = R.string.settings_folk_mount),
                        summary = summary,
                        items = if (available) modeItems else emptyList(),
                        enabled = available && !uiState.isFolkMountLoading && !uiState.isFolkMountWriting,
                        selectedIndex = mode?.ordinal ?: 0,
                        onItemSelected = { index ->
                            FolkMountMode.entries.getOrNull(index)?.let(actions.onSetFolkMountMode)
                        }
                    )
                },
                {
                    // NoMount is its own page: the row only reports the driver
                    // and injection state, never a success it did not observe.
                    val nomount = uiState.nomountStatus
                    val nomountSummary = when {
                        uiState.isNomountLoading -> stringResource(R.string.nomount_loading)
                        uiState.nomountReadError -> stringResource(R.string.nomount_read_error)
                        nomount == null -> stringResource(R.string.nomount_loading)
                        // Say why it is unusable and what to do, not just that it is.
                        !nomount.apiOk -> nomountUnavailableReason(nomount) ?: stringResource(
                            id = R.string.nomount_driver_unavailable,
                            stringResource(
                                id = when (nomount.driverState) {
                                    NomountDriverState.ABSENT -> R.string.nomount_driver_absent
                                    NomountDriverState.OUTDATED -> R.string.nomount_driver_outdated
                                    NomountDriverState.READY -> R.string.nomount_driver_ready
                                }
                            )
                        )
                        else -> stringResource(
                            id = R.string.nomount_rules_value,
                            nomount.rulesApplied,
                            nomount.rulesExpected,
                        )
                    }
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(id = R.string.settings_nomount)) },
                        supportingContent = { Text(nomountSummary) },
                        leadingContent = { Icon(Icons.Filled.Layers, null) },
                        onClick = actions.onOpenNomount,
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        },
                    )
                },
                {
                    // UTS View has enough configuration that a switch in the
                    // settings list would misrepresent it as a single toggle.
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(id = R.string.settings_uts_view)) },
                        supportingContent = { Text(stringResource(id = R.string.settings_uts_view_summary)) },
                        leadingContent = { Icon(Icons.Rounded.TextFields, null) },
                        onClick = actions.onOpenUtsView,
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        },
                    )
                },
                {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(id = R.string.settings_netisolate)) },
                        supportingContent = {
                            Text(stringResource(id = R.string.settings_netisolate_summary))
                        },
                        leadingContent = { Icon(Icons.Filled.WifiOff, null) },
                        onClick = actions.onOpenNetIsolate,
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        },
                    )
                }
            )
        )
    }
}

@Composable
fun SecurityCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    var showSELinuxModeDialog by remember { mutableStateOf(false) }
    KsuIsValid {
        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                        "forced" -> stringResource(R.string.feature_status_forced_summary)
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_selinux_hide_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Security,
                        title = stringResource(id = R.string.settings_selinux_hide),
                        summary = selinuxHideSummary,
                        enabled = uiState.selinuxHideStatus == "supported",
                        checked = uiState.isSelinuxHideEnabled,
                        onCheckedChange = actions.onSetSelinuxHideEnabled
                    )
                },
                {
                    val modeDisplay = when (uiState.selinuxMode) {
                        "Enforcing" -> stringResource(R.string.settings_selinux_mode_enforcing)
                        "Permissive" -> stringResource(R.string.settings_selinux_mode_permissive)
                        "Disabled" -> stringResource(R.string.selinux_status_disabled)
                        else -> stringResource(R.string.selinux_status_unknown)
                    }
                    val modeTitle = stringResource(R.string.settings_selinux_mode)
                    SegmentedListItem(
                        onClick = { showSELinuxModeDialog = true },
                        enabled = !uiState.isSELinuxModeWriting,
                        headlineContent = { Text(modeTitle) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_selinux_current_mode, modeDisplay))
                        },
                        leadingContent = { Icon(Icons.Filled.Lock, modeTitle) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        },
                    )
                },
                {
                    val adbRootSummary = when (uiState.adbRootStatus) {
                        "forced" -> stringResource(R.string.feature_status_forced_summary)
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_adb_root_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Adb,
                        title = stringResource(id = R.string.settings_adb_root),
                        summary = adbRootSummary,
                        enabled = uiState.adbRootStatus == "supported",
                        checked = uiState.isAdbRootEnabled,
                        onCheckedChange = actions.onSetAdbRootEnabled
                    )
                },
                {
                    val summary = when (uiState.shellSuAdbOnlyStatus) {
                        "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(R.string.feature_status_managed_summary)
                        "forced" -> stringResource(R.string.feature_status_forced_summary)
                        else -> stringResource(R.string.settings_shell_su_adb_only_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.VerifiedUser,
                        title = stringResource(R.string.settings_shell_su_adb_only),
                        summary = summary,
                        enabled = uiState.shellSuAdbOnlyStatus == "supported",
                        checked = uiState.isShellSuAdbOnlyEnabled,
                        onCheckedChange = actions.onSetShellSuAdbOnlyEnabled
                    )
                }
            )
        )
    }

    if (showSELinuxModeDialog) {
        SELinuxModeDialog(
            currentMode = uiState.selinuxMode,
            onDismiss = { showSELinuxModeDialog = false },
            onConfirm = actions.onSetSELinuxMode,
        )
    }
}
