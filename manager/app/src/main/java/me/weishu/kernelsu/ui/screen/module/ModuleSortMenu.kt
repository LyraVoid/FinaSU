package me.weishu.kernelsu.ui.screen.module

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.data.model.ModuleNameOrder
import me.weishu.kernelsu.data.model.ModuleSortGroup
import me.weishu.kernelsu.data.model.ModuleSortPriorityGroups
import me.weishu.kernelsu.R as AppR

@Composable
internal fun ModuleSortMenu(
    expanded: Boolean,
    groups: Set<ModuleSortGroup>,
    enabledFirst: Boolean,
    nameOrder: ModuleNameOrder,
    customOrder: List<String>,
    customOrderEnabled: Boolean,
    onDismiss: () -> Unit,
    onGroupChange: (ModuleSortGroup, Boolean) -> Unit,
    onEnabledFirstChange: (Boolean) -> Unit,
    onNameOrderChange: (ModuleNameOrder) -> Unit,
    onCustomOrderEnabledChange: (Boolean) -> Unit,
    onResetCustomOrder: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val showReset = customOrderEnabled || customOrder.isNotEmpty()

    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        val priorityCount = 1 + ModuleSortPriorityGroups.size
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
            CheckableItem(
                text = stringResource(AppR.string.module_sort_enabled_first),
                checked = enabledFirst,
                shapeIndex = 0,
                shapeCount = priorityCount,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onEnabledFirstChange(!enabledFirst)
            }
            ModuleSortPriorityGroups.forEachIndexed { index, group ->
                val checked = group in groups
                CheckableItem(
                    text = stringResource(group.titleRes()),
                    checked = checked,
                    shapeIndex = index + 1,
                    shapeCount = priorityCount,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onGroupChange(group, !checked)
                }
            }
        }

        Spacer(Modifier.height(MenuDefaults.GroupSpacing))

        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
            val orderCount = 2 + 1 + if (showReset) 1 else 0
            CheckableItem(
                text = stringResource(AppR.string.sort_asc),
                checked = nameOrder == ModuleNameOrder.Ascending,
                shapeIndex = 0,
                shapeCount = orderCount,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onNameOrderChange(ModuleNameOrder.Ascending)
            }
            CheckableItem(
                text = stringResource(AppR.string.sort_desc),
                checked = nameOrder == ModuleNameOrder.Descending,
                shapeIndex = 1,
                shapeCount = orderCount,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onNameOrderChange(ModuleNameOrder.Descending)
            }
            CheckableItem(
                text = stringResource(AppR.string.module_sort_custom_order),
                checked = customOrderEnabled,
                shapeIndex = 2,
                shapeCount = orderCount,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onCustomOrderEnabledChange(!customOrderEnabled)
                onDismiss()
            }
            if (showReset) {
                DropdownMenuItem(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onResetCustomOrder()
                        onDismiss()
                    },
                    text = { Text(stringResource(AppR.string.module_sort_reset_custom_order)) },
                    shape = MenuDefaults.itemShape(index = 3, count = orderCount).shape,
                )
            }
        }
    }
}

@Composable
private fun CheckableItem(
    text: String,
    checked: Boolean,
    shapeIndex: Int,
    shapeCount: Int,
    onClick: () -> Unit,
) {
    CheckableDropdownMenuItem(
        text = { Text(text) },
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = MenuDefaults.itemShape(index = shapeIndex, count = shapeCount),
        checkedLeadingIcon = {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
            )
        },
    )
}

private fun ModuleSortGroup.titleRes(): Int = when (this) {
    ModuleSortGroup.MetaModule -> AppR.string.module_sort_group_metamodule
    ModuleSortGroup.Zygisk -> AppR.string.module_sort_kind_zygisk
    ModuleSortGroup.LSPosed -> AppR.string.module_sort_kind_lsposed
    ModuleSortGroup.WebUi -> AppR.string.module_sort_kind_webui
    ModuleSortGroup.ActionScript -> AppR.string.module_sort_kind_action
}
