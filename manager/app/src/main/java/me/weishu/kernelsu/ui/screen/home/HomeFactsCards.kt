package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkMountProvider
import me.weishu.kernelsu.ui.component.material.LocalWallpaperSupportingColor
import me.weishu.kernelsu.ui.component.material.folkPressScale
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceId

/** The manager build and the policy it runs under, or the system facts the stats board closes with. */
@Composable
internal fun HomeFactsTile(
    state: HomeUiState,
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: SurfaceConfig? = null,
    onLongClick: (() -> Unit)? = null,
    surfaceId: SurfaceId? = null,
    onSusfs: () -> Unit = {},
) {
    val display = rememberHomeDisplaySettings()
    HomeTileCard(
        title = title,
        icon = icon,
        modifier = modifier.fillMaxWidth(),
        background = background,
        onLongClick = onLongClick,
        surfaceId = surfaceId,
    ) {
        HomeFactRow(
            label = stringResource(R.string.home_manager_version),
            value = state.systemInfo.managerVersion,
        )
        HomeFactRow(
            label = stringResource(R.string.home_kernel),
            value = state.systemInfo.kernelVersion,
        )
        if (!display.hideKernelFullVersion) {
            state.systemInfo.kernelFullVersion?.takeIf { it.isNotBlank() }?.let { value ->
                HomeFactRow(
                    label = stringResource(R.string.home_kernel_full_version),
                    value = value,
                )
            }
        }
        HomeFactRow(
            label = stringResource(R.string.home_device_model),
            value = state.systemInfo.deviceModel,
        )
        if (!display.hideFingerprint) {
            HomeFactRow(
                label = stringResource(R.string.home_fingerprint),
                value = state.systemInfo.fingerprint,
            )
        }
        HomeFactRow(
            label = stringResource(R.string.home_selinux_status),
            value = selinuxDisplayName(state.systemInfo.selinuxStatus),
        )
        HomeFactRow(
            label = stringResource(R.string.home_seccomp_status),
            value = seccompDisplayName(state.systemInfo.seccompStatus),
        )
        if (!display.hideZygisk) {
            state.zygiskProvider?.let { provider ->
                HomeFactRow(
                    label = stringResource(R.string.home_zygisk_implement),
                    value = provider,
                )
            }
        }
        if (!display.hideMount) {
            homeMountValue(state.mount)?.let { value ->
                HomeFactRow(
                    label = stringResource(R.string.home_mount_implementation),
                    value = value,
                )
            }
        }
        if (!display.hideSusfs) SusfsHomeRow(state.susfs, onSusfs)
    }
}

/** One `label: value` line, the shape every row of the Focus board takes. */
@Composable
internal fun HomeFactRow(
    label: String,
    value: String,
) {
    val overColor = LocalHomeTileCardContentColor.current
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label: ",
            style = FolkType.Summary,
            color = overColor?.copy(alpha = 0.8f)
                ?: LocalWallpaperSupportingColor.current
                ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = FolkType.Summary.copy(fontWeight = FontWeight.Medium),
            color = overColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun InfoCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
    susfs: me.weishu.kernelsu.data.susfs.SusfsStatus = me.weishu.kernelsu.data.susfs.SusfsStatus(),
    zygiskProvider: String? = null,
    mount: HomeMount = HomeMount(),
    onSusfs: () -> Unit = {},
) {
    val selinuxDisplay = selinuxDisplayName(systemInfo.selinuxStatus)
    val seccompDisplay = seccompDisplayName(systemInfo.seccompStatus)
    val display = rememberHomeDisplaySettings()

    HomeCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoRow(
                icon = Icons.Filled.Tag,
                label = stringResource(R.string.home_manager_version),
                value = systemInfo.managerVersion,
            )
            InfoRow(
                icon = Icons.Filled.DeveloperBoard,
                label = stringResource(R.string.home_kernel),
                value = systemInfo.kernelVersion,
            )
            if (!display.hideKernelFullVersion) {
                systemInfo.kernelFullVersion?.takeIf { it.isNotBlank() }?.let { value ->
                    InfoRow(
                        icon = Icons.Filled.DeveloperBoard,
                        label = stringResource(R.string.home_kernel_full_version),
                        value = value,
                    )
                }
            }
            InfoRow(
                icon = Icons.Filled.Smartphone,
                label = stringResource(R.string.home_device_model),
                value = systemInfo.deviceModel,
            )
            if (!display.hideFingerprint) {
                InfoRow(
                    icon = Icons.Filled.Fingerprint,
                    label = stringResource(R.string.home_fingerprint),
                    value = systemInfo.fingerprint,
                )
            }
            InfoRow(
                icon = Icons.Filled.Security,
                label = stringResource(R.string.home_selinux_status),
                value = selinuxDisplay,
            )
            InfoRow(
                icon = Icons.Filled.FilterList,
                label = stringResource(R.string.home_seccomp_status),
                value = seccompDisplay,
            )
            if (zygiskProvider != null && !display.hideZygisk) {
                InfoRow(
                    icon = Icons.Filled.Vaccines,
                    label = stringResource(R.string.home_zygisk_implement),
                    value = zygiskProvider,
                )
            }
            if (!display.hideMount) {
                homeMountValue(mount)?.let { value ->
                    InfoRow(
                        icon = Icons.Filled.Layers,
                        label = stringResource(R.string.home_mount_implementation),
                        value = value,
                    )
                }
            }
            if (susfs.detected && !display.hideSusfs) {
                val interactionSource = remember { MutableInteractionSource() }
                val haptic = LocalHapticFeedback.current
                val management = me.weishu.kernelsu.ui.screen.susfsManagementLabel(susfs.management)
                InfoRow(
                    icon = Icons.Rounded.VisibilityOff,
                    label = stringResource(R.string.susfs_title),
                    value = "${susfs.version} · ${susfs.implementation} · $management",
                    modifier = Modifier
                        .folkPressScale(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSusfs()
                            },
                        ),
                    trailingContent = {
                        Text(
                            text = stringResource(R.string.susfs_manage),
                            style = FolkType.Summary,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SusfsHomeRow(status: me.weishu.kernelsu.data.susfs.SusfsStatus, onClick: () -> Unit) {
    if (!status.detected) return
    val overColor = LocalHomeTileCardContentColor.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("SUSFS · ${status.version} · ${status.implementation}", style = FolkType.Summary,
                color = overColor ?: MaterialTheme.colorScheme.onSurface)
            Text(me.weishu.kernelsu.ui.screen.susfsManagementLabel(status.management), style = FolkType.Machine,
                color = overColor?.copy(alpha = 0.8f)
                    ?: LocalWallpaperSupportingColor.current
                    ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.susfs_manage), style = FolkType.Summary,
            color = overColor ?: MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // On wallpaper the material carries its own supporting colour, tuned against the translucent
        // fill; off it we keep the semantic greys.
        val supportingColor = LocalWallpaperSupportingColor.current
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = supportingColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = FolkType.Summary)
            Text(
                text = value,
                style = FolkType.Machine,
                color = supportingColor ?: MaterialTheme.colorScheme.outline
            )
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(16.dp))
            trailingContent()
        }
    }
}

/** The provider that really mounted modules this boot, or null when it cannot be named. */
@Composable
private fun homeMountValue(mount: HomeMount): String? = when (mount.provider) {
    FolkMountProvider.METAMODULE -> listOfNotNull(
        stringResource(R.string.settings_folk_mount_provider_metamodule),
        mount.metamodule?.name?.takeIf { it.isNotBlank() } ?: mount.metamoduleId,
        mount.metamodule?.version?.takeIf { it.isNotBlank() && it != "Unknown" },
    ).joinToString(" · ")

    FolkMountProvider.BUILTIN -> stringResource(R.string.home_mount_builtin_magic)
    FolkMountProvider.NOMOUNT -> stringResource(R.string.home_mount_builtin_vfs)
    FolkMountProvider.NONE -> stringResource(R.string.settings_folk_mount_mode_off)
    null -> null
}
