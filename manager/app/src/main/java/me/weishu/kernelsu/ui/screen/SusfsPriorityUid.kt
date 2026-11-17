package me.weishu.kernelsu.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.data.susfs.SusfsCapabilityState
import me.weishu.kernelsu.data.susfs.SusfsStatus
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.FolkFilledTonalButton
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.FolkTextButton
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import me.weishu.kernelsu.ui.viewmodel.SusfsViewModel
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun SusfsPriorityUidSection(config: JSONObject?, status: SusfsStatus, writable: Boolean, vm: SusfsViewModel) {
    var editing by remember { mutableStateOf(false) }
    var stop by remember { mutableStateOf(false) }
    val settings = config?.optJSONObject("settings")
    val values = settings?.optJSONArray("priority_uid_filter")
    val names = remember(values?.toString()) {
        values?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
    }
    if (!status.showPriorityUidFilter(names != null)) return
    val supported = status.priorityUidState == SusfsCapabilityState.Available
    val accepted = stringResource(R.string.susfs_priority_unverified)
    SegmentedColumn(title = stringResource(R.string.susfs_priority_title)) {
        item {
            SegmentedListItem(onClick = { editing = true }, enabled = config != null,
                headlineContent = { Text(stringResource(R.string.susfs_priority_apps)) },
                supportingContent = { Text(if (status.priorityUidState == SusfsCapabilityState.Unknown)
                    stringResource(R.string.susfs_state_unknown) else susfsDisabledReason(status, supported, writable)
                    ?: if (names == null) stringResource(R.string.susfs_unmanaged)
                    else stringResource(R.string.susfs_priority_count, names.size)) })
        }
        if (names != null) item {
            SegmentedListItem(onClick = { stop = true }, enabled = writable && supported,
                headlineContent = { Text(stringResource(R.string.susfs_priority_stop)) },
                supportingContent = { Text(stringResource(R.string.susfs_priority_stop_hint)) })
        }
        item { SegmentedListItem(headlineContent = { Text(stringResource(R.string.susfs_priority_hint)) },
            supportingContent = { Text(stringResource(R.string.susfs_priority_unverified)) }) }
        status.receipt?.let { receipt ->
            val key = "priority_uid_filter"
            val result = when {
                key in receipt.failures -> receipt.failures[key]
                key in receipt.applied -> accepted
                key in receipt.skipped -> receipt.skipped[key]
                else -> null
            }
            if (result != null) item { SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.susfs_priority_result)) },
                supportingContent = { Text(result) }) }
        }
    }
    if (stop) AlertDialog(onDismissRequest = { stop = false },
        title = { Text(stringResource(R.string.susfs_priority_stop)) },
        text = { Text(stringResource(R.string.susfs_priority_stop_hint)) },
        confirmButton = { TextButton(onClick = {
            vm.change { it.getJSONObject("settings").put("priority_uid_filter", JSONObject.NULL) }
            stop = false
        }) { Text(stringResource(R.string.susfs_priority_stop)) } },
        dismissButton = { TextButton(onClick = { stop = false }) { Text(stringResource(android.R.string.cancel)) } })
    if (editing) SusfsPriorityPicker(names.orEmpty(), writable && supported, { editing = false }) { selected ->
        vm.change { it.getJSONObject("settings").put("priority_uid_filter", JSONArray(selected.sorted())) }
        editing = false
    }
}

@Composable
private fun SusfsPriorityPicker(
    initial: Set<String>,
    editable: Boolean,
    dismiss: () -> Unit,
    save: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var error by remember { mutableStateOf(false) }
    val toggleApp: (String) -> Unit = { packageName ->
        selected = if (packageName in selected) {
            selected - packageName
        } else {
            selected + packageName
        }
    }
    LaunchedEffect(Unit) {
        try {
            val loader = SuperUserViewModel()
            loader.fetchAppList()
            loader.uiState.value.error?.let { throw it }
            apps = SuperUserViewModel.apps
                .filter { !it.special && it.packageInfo.applicationInfo != null && it.uid % 100000 in 10000..19999 }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            error = true
        }
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalFolkWallpaperTokens provides null) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .fillMaxHeight(0.9f),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.susfs_priority_apps),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f),
                            )
                            FolkIconButton(onClick = dismiss) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }

                        PrioritySearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(R.string.susfs_priority_search),
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.susfs_priority_count, selected.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.susfs_priority_shared),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        PriorityAppPickerContent(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            apps = apps,
                            error = error,
                            selected = selected,
                            query = query,
                            editable = editable,
                            onToggle = toggleApp,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FolkTextButton(onClick = dismiss) {
                                Text(stringResource(R.string.susfs_cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            FolkFilledTonalButton(
                                enabled = editable && apps != null && !error,
                                onClick = { save(selected) },
                            ) {
                                Text(stringResource(R.string.susfs_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrioritySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotEmpty()) {
                FolkIconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun PriorityAppPickerContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    apps: List<AppInfo>?,
    error: Boolean,
    selected: Set<String>,
    query: String,
    editable: Boolean,
    onToggle: (String) -> Unit,
) {
    when {
        error -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.susfs_priority_load_error), color = MaterialTheme.colorScheme.error)
        }

        apps == null -> Box(modifier, contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }

        else -> {
            val installedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
            val missing = (selected - installedPackages).filter {
                it.contains(query, ignoreCase = true)
            }.sorted()
            val visible = apps.filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            if (missing.isEmpty() && visible.isEmpty()) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.susfs_priority_empty))
                }
            } else {
                val rowCount = missing.size + visible.size
                LazyColumn(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(
                        bottom = 4.dp,
                    ),
                ) {
                    itemsIndexed(missing, key = { _, name -> "missing:$name" }) { index, name ->
                        SegmentedItem(index = index, count = rowCount) {
                            SegmentedListItem(
                                selected = true,
                                enabled = editable,
                                onClick = { onToggle(name) },
                                headlineContent = {
                                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(stringResource(R.string.susfs_priority_missing))
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = true,
                                        enabled = editable,
                                        onCheckedChange = { onToggle(name) },
                                    )
                                },
                            )
                        }
                    }
                    itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
                        val checked = app.packageName in selected
                        SegmentedItem(index = missing.size + index, count = rowCount) {
                            SegmentedListItem(
                                selected = checked,
                                enabled = editable,
                                onClick = { onToggle(app.packageName) },
                                leadingContent = {
                                    AppIconImage(
                                        modifier = Modifier.size(40.dp),
                                        packageInfo = app.packageInfo,
                                        label = app.label,
                                    )
                                },
                                headlineContent = {
                                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = checked,
                                        enabled = editable,
                                        onCheckedChange = { onToggle(app.packageName) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
