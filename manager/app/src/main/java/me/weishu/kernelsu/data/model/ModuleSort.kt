package me.weishu.kernelsu.data.model

import java.text.Collator

/**
 * A kind of installed module, in the order the list should read. The pieces the rest of the system
 * is built on come first, so the list reads roughly like the boot order instead of the alphabet.
 *
 * The stored string of each group is what the priority preference holds, so a value written by a
 * newer version is ignored rather than breaking the order.
 */
enum class ModuleSortGroup(val value: String) {
    /** A module other modules attach to; a device can only have one. */
    MetaModule("metamodule"),

    /** A module that provides or loads the Zygisk runtime. */
    Zygisk("zygisk"),

    /** A module that hooks through the Xposed framework. */
    LSPosed("lsposed"),

    /** A module with a screen of its own. */
    WebUi("webui"),

    /** A module with a script to run. */
    ActionScript("action"),
}

/**
 * The groups a reader can tick, in the order they are ranked while all of them are ticked. A group
 * that is not ticked does not disappear; it only stops being lifted above the alphabet.
 */
val ModuleSortPriorityGroups: List<ModuleSortGroup> = listOf(
    ModuleSortGroup.MetaModule,
    ModuleSortGroup.Zygisk,
    ModuleSortGroup.LSPosed,
    ModuleSortGroup.WebUi,
    ModuleSortGroup.ActionScript,
)

enum class ModuleNameOrder(val value: String) {
    Default("id"),
    Ascending("name_asc"),
    Descending("name_desc"),
    ;

    companion object {
        fun fromValue(stored: String?): ModuleNameOrder =
            entries.firstOrNull { it.value == stored } ?: Default
    }
}

object ModuleNameOrderStore {
    const val Key = "module_sort_name_order"

    fun encode(order: ModuleNameOrder): String = order.value

    fun decode(stored: String?): ModuleNameOrder = ModuleNameOrder.fromValue(stored)
}

/** Whether [id] looks like a module that carries the Zygisk runtime. */
fun isZygiskModule(id: String): Boolean = id.contains("zygisk", ignoreCase = true)

/** Whether [name] looks like a module built on the Xposed framework. */
fun isLSPosedModule(name: String): Boolean = name.contains("LSPosed", ignoreCase = true)

/**
 * The module ids that carry the Zygisk runtime, the same set the daemon recognises. Sharing an id
 * is the only thing these modules agree on: the id of a provider is unrelated to the ids of the
 * modules that hook through it, so the alphabet alone cannot put the provider first.
 */
private val ZygiskProviderIds = setOf(
    "zygisksu",
    "zygisknext",
    "rezygisk",
    "neozygisk",
    "shirokozygisk",
    "onyxzygisk",
    "yukizygisk",
    "brezygisk",
)

/** Whether [id] provides the Zygisk runtime rather than using it. */
fun isZygiskProvider(id: String): Boolean = id.lowercase() in ZygiskProviderIds

/**
 * The facts the order is decided from. A module carries them; this file reads no disk.
 *
 * [enabled] is consulted only when enabled-first is on. Without that option a module that is
 * switched off keeps the place its kind earns, because sinking it to the bottom would move it out
 * from under the finger that just toggled it - which is exactly when someone is looking for it.
 */
data class ModuleSortFacts(
    val id: String,
    val name: String,
    val metaModule: Boolean,
    val hasWebUi: Boolean,
    val hasActionScript: Boolean,
    val enabled: Boolean = true,
)

/** Which group [this] belongs to, or `null` when no group claims it. */
fun ModuleSortFacts.sortGroup(): ModuleSortGroup? = when {
    metaModule -> ModuleSortGroup.MetaModule
    isZygiskModule(id) -> ModuleSortGroup.Zygisk
    isLSPosedModule(name) -> ModuleSortGroup.LSPosed
    hasWebUi -> ModuleSortGroup.WebUi
    hasActionScript -> ModuleSortGroup.ActionScript
    else -> null
}

fun moduleSortComparator(
    collator: Collator,
    priorities: Set<ModuleSortGroup> = ModuleSortPriorityGroups.toSet(),
    enabledFirst: Boolean = false,
    nameOrder: ModuleNameOrder = ModuleNameOrder.Default,
    manualOrder: List<String> = emptyList(),
): Comparator<ModuleSortFacts> {
    val ranking = ModuleSortPriorityGroups.filter { it in priorities }
    val positions = manualOrder.withIndex().associate { it.value to it.index }
    val byGroup = compareBy<ModuleSortFacts> { facts ->
        val index = facts.sortGroup()?.let(ranking::indexOf) ?: -1
        index.takeIf { it >= 0 } ?: ranking.size
    }.thenBy { facts -> facts.sortRank() }
        .thenBy { facts -> enabledFirst && !facts.enabled }
        .thenBy { facts -> positions[facts.id] ?: Int.MAX_VALUE }

    return when (nameOrder) {
        ModuleNameOrder.Default -> byGroup.thenBy(collator) { it.id }
        ModuleNameOrder.Ascending -> byGroup
            .thenBy(collator) { it.name.ifBlank { it.id } }
            .thenBy(collator) { it.id }

        ModuleNameOrder.Descending -> byGroup
            .thenByDescending(collator) { it.name.ifBlank { it.id } }
            .thenBy(collator) { it.id }
    }
}

/**
 * Where [this] sits inside its own group. Only the Zygisk group has an order of its own: the module
 * that provides the runtime leads the modules that hook through it, so the one the device actually
 * loads is not read past a list of its users.
 */
private fun ModuleSortFacts.sortRank(): Int =
    if (sortGroup() == ModuleSortGroup.Zygisk && !isZygiskProvider(id)) 1 else 0

/**
 * Keeps the relative order of [order], drops ids no longer installed, and appends ids not yet in
 * it in [moduleIds] order. An empty [order] stays empty: a reader who never set a custom order
 * does not get one from the first refresh. An empty [moduleIds] also leaves the order untouched:
 * a refresh that failed to read anything must not be mistaken for every module being uninstalled.
 */
fun reconcileCustomOrder(order: List<String>, moduleIds: List<String>): List<String> {
    if (order.isEmpty() || moduleIds.isEmpty()) return order

    val installed = moduleIds.toHashSet()
    val kept = order.filter { it in installed }
    val keptIds = kept.toHashSet()
    return kept + moduleIds.filter { it !in keptIds }
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Reads and writes the ticked groups. Both ends are pure so the stored shape stays testable
 * without a device: an empty string is a reader who unticked everything, while no stored value
 * at all is a reader who has never opened the picker, and gets all of them.
 */
object ModuleSortPriorityStore {
    const val Key = "module_sort_groups"

    val Default: Set<ModuleSortGroup> = ModuleSortPriorityGroups.toSet()

    fun encode(groups: Set<ModuleSortGroup>): String =
        ModuleSortPriorityGroups.filter { it in groups }.joinToString(",") { it.value }

    fun decode(stored: String?): Set<ModuleSortGroup> {
        if (stored == null) return Default

        val tokens = stored.split(',').map(String::trim).filter(String::isNotEmpty)
        val known = tokens.mapNotNullTo(mutableSetOf()) { token ->
            ModuleSortPriorityGroups.firstOrNull { it.value == token }
        }

        // A value holding nothing we know is not a reader's choice; it is a preference from some
        // other version of this screen, and all of them is the safer reading.
        return if (known.isEmpty() && tokens.isNotEmpty()) Default else known
    }
}

/**
 * Reads and writes the custom order, one module id per line. An empty string means no custom
 * order is set.
 */
object ModuleCustomOrderStore {
    const val Key = "module_sort_custom_order"

    fun encode(ids: List<String>): String =
        ids.filter(String::isNotBlank).distinct().joinToString("\n")

    fun decode(stored: String?): List<String> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.split('\n').filter(String::isNotEmpty).distinct()
    }
}
