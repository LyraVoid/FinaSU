package me.weishu.kernelsu.ui.component.kernel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.boot.KernelFacts
import me.weishu.kernelsu.data.boot.KernelFieldValue
import me.weishu.kernelsu.data.boot.KernelImageFacts
import me.weishu.kernelsu.data.boot.KernelSlotFacts
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem

internal fun distinctKernelValues(facts: KernelFacts?): List<Pair<String, String>> =
    facts?.slots.orEmpty()
        .filter(KernelSlotFacts::kernelValuesAvailable)
        .map { it.release!!.value to it.version!!.value }
        .distinct()

internal fun nextSlotSelection(
    currentSlot: String?,
    lastCurrentSlot: String?,
    selectedId: String?,
    slotIds: List<String>,
): String? = if (selectedId !in slotIds || lastCurrentSlot != currentSlot) {
    currentSlot ?: slotIds.singleOrNull()
} else {
    selectedId
}

/**
 * Read-only boot image facts shared by SUSFS and UTS View. Using a value only
 * changes the caller's draft; persistence and application remain separate.
 */
@Composable
fun BootSlotSource(
    facts: KernelFacts?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onUseKernel: ((KernelSlotFacts) -> Unit)? = null,
    onUseCmdline: ((String, KernelFieldValue) -> Unit)? = null,
    onUseBootconfig: ((String, KernelFieldValue) -> Unit)? = null,
    draftSourceSlot: String? = null,
    draftSourceIdentity: String? = null,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastCurrentSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable(selectedId) { mutableStateOf(false) }
    val slots = facts?.slots.orEmpty()
    LaunchedEffect(facts?.currentSlot, slots.map { it.id }) {
        selectedId = nextSlotSelection(
            facts?.currentSlot,
            lastCurrentSlot,
            selectedId,
            slots.map { it.id },
        )
        lastCurrentSlot = facts?.currentSlot
    }
    val selected = slots.firstOrNull { it.id == selectedId }
    val values = distinctKernelValues(facts)

    SegmentedColumn(modifier = modifier, title = stringResource(R.string.boot_slot_source)) {
        item(key = "provenance") {
            val provenance = listOfNotNull(
                stringResource(R.string.boot_slot_source_hint),
                facts?.readAt?.let { "${facts.parser ?: "finasu-boot-facts"} · $it" },
            ).joinToString("\n")
            SegmentedListItem(
                headlineContent = { Text(stringResource(R.string.boot_slot_original)) },
                supportingContent = { Text(provenance) },
                leadingContent = { Icon(Icons.Rounded.Storage, null) },
            )
        }
        if (loading) {
            item(key = "loading") {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.boot_slot_loading)) },
                    leadingContent = { Icon(Icons.Rounded.Storage, null) },
                )
            }
        }
        facts?.conflicts?.forEachIndexed { index, conflict ->
            item(key = "conflict:$index") {
                SegmentedListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.boot_slot_conflict),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    supportingContent = { Text(conflict) },
                    leadingContent = { Icon(Icons.Rounded.Storage, null) },
                )
            }
        }
        if (!error.isNullOrBlank()) {
            item(key = "error") {
                SegmentedListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.boot_slot_unavailable),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    supportingContent = { Text(error) },
                    leadingContent = { Icon(Icons.Rounded.Storage, null) },
                )
            }
        }
        if (!loading && facts != null && slots.isEmpty()) {
            item(key = "empty") {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.boot_slot_unavailable)) },
                )
            }
        }
        slots.forEach { slot ->
            item(key = "slot:${slot.id}") {
                SegmentedListItem(
                    selected = slot.id == selectedId,
                    onClick = { selectedId = slot.id },
                    headlineContent = {
                        Text(
                            stringResource(
                                if (slot.current) R.string.boot_slot_current else R.string.boot_slot_name,
                                slot.id.uppercase(),
                            ),
                        )
                    },
                    supportingContent = {
                        val detail = when {
                            !slot.exists -> stringResource(R.string.boot_slot_missing)
                            !slot.readable -> slot.reason ?: stringResource(R.string.boot_slot_unreadable)
                            slot.kernelValuesAvailable -> "${slot.release?.value}\n${slot.version?.value}"
                            else -> slot.reason ?: slot.missingFields.joinToString()
                        }
                        Text(detail, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { Icon(Icons.Rounded.Storage, null) },
                )
            }
        }
        if (values.size > 1) {
            item(key = "different") {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.boot_slot_values_differ)) },
                )
            }
        } else if (values.size == 1 && slots.count(KernelSlotFacts::kernelValuesAvailable) > 1) {
            item(key = "same") {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.boot_slot_values_same)) },
                )
            }
        }
        selected?.let { slot ->
            item(key = "release") {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.boot_slot_release)) },
                    supportingContent = { Text(fieldDetail(slot.release)) },
                )
            }
            item(key = "version") {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(R.string.boot_slot_version)) },
                    supportingContent = { Text(fieldDetail(slot.version)) },
                )
            }
            slot.cmdline.forEachIndexed { index, value ->
                item(key = "cmdline:$index") {
                    SegmentedListItem(
                        enabled = onUseCmdline != null,
                        onClick = onUseCmdline?.let { action -> { action(slot.id, value) } },
                        headlineContent = { Text(stringResource(R.string.boot_slot_cmdline)) },
                        supportingContent = {
                            Text(
                                "${value.value}\n${value.source} · ${value.confidence}",
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            slot.bootconfig.forEachIndexed { index, value ->
                item(key = "bootconfig:$index") {
                    SegmentedListItem(
                        enabled = onUseBootconfig != null,
                        onClick = onUseBootconfig?.let { action -> { action(slot.id, value) } },
                        headlineContent = { Text(stringResource(R.string.boot_slot_bootconfig)) },
                        supportingContent = {
                            Text(
                                "${value.value}\n${value.source} · ${value.confidence}",
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            onUseKernel?.let { action ->
                if (slot.kernelValuesAvailable) {
                    item(key = "use-kernel") {
                        SegmentedListItem(
                            onClick = { action(slot) },
                            headlineContent = { Text(stringResource(R.string.boot_slot_use_kernel)) },
                            supportingContent = { Text(stringResource(R.string.boot_slot_draft_only)) },
                            leadingContent = { Icon(Icons.Rounded.Storage, null) },
                        )
                    }
                }
            }
            if (draftSourceSlot == slot.id &&
                draftSourceIdentity != null &&
                slot.sourceIdentity != draftSourceIdentity
            ) {
                item(key = "stale") {
                    SegmentedListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.boot_slot_source_changed),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
            item(key = "details-toggle") {
                SegmentedListItem(
                    onClick = { showDetails = !showDetails },
                    headlineContent = { Text(stringResource(R.string.boot_slot_details)) },
                    trailingContent = { Text(if (showDetails) "▴" else "▾") },
                    leadingContent = { Icon(Icons.Rounded.Storage, null) },
                )
            }
            if (showDetails) {
                val evidence = facts?.evidence.orEmpty().filter {
                    it.current || it.slot == slot.id
                }
                if (evidence.isNotEmpty()) {
                    item(key = "evidence") {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.boot_slot_evidence)) },
                            supportingContent = {
                                Text(
                                    evidence.joinToString("\n") {
                                        "${it.source} · ${it.status}" +
                                            (it.slot?.let { value -> " · $value" } ?: "") +
                                            (it.detail?.let { value -> "\n$value" } ?: "")
                                    },
                                    maxLines = 10,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                if (slot.images.isNotEmpty()) {
                    item(key = "source-header") {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.boot_slot_image_source)) },
                        )
                    }
                    slot.images.forEachIndexed { index, image ->
                        item(key = "image:$index") {
                            SegmentedListItem(
                                headlineContent = { Text(image.name) },
                                supportingContent = {
                                    Text(
                                        imageDetail(image),
                                        maxLines = 8,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        item(key = "refresh") {
            SegmentedListItem(
                enabled = !loading,
                onClick = onRefresh,
                headlineContent = { Text(stringResource(R.string.boot_slot_refresh)) },
                leadingContent = { Icon(Icons.Rounded.Refresh, null) },
            )
        }
    }
}

@Composable
private fun fieldDetail(field: KernelFieldValue?): String =
    field?.let { "${it.value}\n${it.source} · ${it.confidence}" }
        ?: stringResource(R.string.boot_slot_missing)

@Composable
private fun imageDetail(image: KernelImageFacts): String {
    val readable = stringResource(
        if (image.readable) R.string.boot_slot_readable else R.string.boot_slot_unreadable,
    )
    val missing = stringResource(R.string.boot_slot_missing)
    val lines = mutableListOf(
        buildString {
            append(image.format)
            image.headerVersion?.let { append(" · v$it") }
            append(" · $readable")
        },
        image.path,
    )
    image.avb?.let {
        lines += stringResource(R.string.boot_slot_avb, it.footerVersion, it.originalImageSize)
        if (!it.signatureVerified) lines += stringResource(R.string.boot_slot_signature_unchecked)
    }
    lines += "SHA-256: ${image.sha256 ?: missing}"
    image.reason?.let(lines::add)
    return lines.joinToString("\n")
}
