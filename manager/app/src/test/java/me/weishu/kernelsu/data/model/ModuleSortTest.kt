package me.weishu.kernelsu.data.model

import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleSortTest {

    private val collator = Collator.getInstance(Locale.ROOT)

    private fun facts(
        id: String,
        name: String = id,
        meta: Boolean = false,
        webUi: Boolean = false,
        action: Boolean = false,
        enabled: Boolean = true,
    ) = ModuleSortFacts(
        id = id,
        name = name,
        metaModule = meta,
        hasWebUi = webUi,
        hasActionScript = action,
        enabled = enabled,
    )

    private fun sorted(
        modules: List<ModuleSortFacts>,
        priorities: Set<ModuleSortGroup> = ModuleSortPriorityGroups.toSet(),
        enabledFirst: Boolean = false,
        nameOrder: ModuleNameOrder = ModuleNameOrder.Default,
        manualOrder: List<String> = emptyList(),
    ): List<String> = modules
        .sortedWith(moduleSortComparator(collator, priorities, enabledFirst, nameOrder, manualOrder))
        .map { it.id }

    @Test
    fun `group detection prefers the more specific kind`() {
        // A metamodule named like an Xposed module is still a metamodule.
        assertEquals(
            ModuleSortGroup.MetaModule,
            facts("zygisk_meta", name = "LSPosed Meta", meta = true, webUi = true).sortGroup(),
        )
        assertEquals(
            ModuleSortGroup.Zygisk,
            facts("zygisknext", name = "LSPosed helper", webUi = true).sortGroup(),
        )
        assertEquals(
            ModuleSortGroup.LSPosed,
            facts("some_xposed", name = "LSPosed", webUi = true).sortGroup(),
        )
        assertEquals(ModuleSortGroup.WebUi, facts("web", webUi = true, action = true).sortGroup())
        assertEquals(ModuleSortGroup.ActionScript, facts("act", action = true).sortGroup())
        assertNull(facts("plain").sortGroup())
    }

    @Test
    fun `the zygisk runtime provider leads the modules that hook through it`() {
        val modules = listOf(
            facts("zygisk_lsposed", name = "LSPosed IT"),
            facts("zygisk-module-xfingerprint-pay-wechat", name = "Zygisk - 指纹支付 - 微信"),
            facts("zygisksu", name = "Zygisk Next"),
            facts("rezygisk"),
        )

        // The two providers are the only modules carrying the runtime, and both read before the
        // modules that merely mention Zygisk in their id.
        assertEquals(
            listOf("rezygisk", "zygisksu", "zygisk_lsposed", "zygisk-module-xfingerprint-pay-wechat"),
            sorted(modules),
        )
    }

    @Test
    fun `a zygisk provider is recognised by its id alone`() {
        assertTrue(isZygiskProvider("zygisksu"))
        assertTrue(isZygiskProvider("ZygiskNext"))
        assertTrue(isZygiskProvider("rezygisk"))
        assertTrue(isZygiskProvider("NeoZygisk"))
        assertTrue(isZygiskProvider("shirokozygisk"))
        assertTrue(isZygiskProvider("onyxzygisk"))
        assertTrue(isZygiskProvider("yukizygisk"))
        assertTrue(isZygiskProvider("brezygisk"))
        assertFalse(isZygiskProvider("zygisk_lsposed"))
        assertFalse(isZygiskProvider("zygisk-module-xfingerprint-pay-wechat"))
    }

    @Test
    fun `priority order ranks ticked groups above the alphabet`() {
        val modules = listOf(
            facts("aaa_plain"),
            facts("zzz_action", action = true),
            facts("mmm_web", webUi = true),
            facts("zzz_lsposed", name = "LSPosed"),
            facts("mmm_zygisk"),
            facts("zzz_meta", meta = true),
        )

        assertEquals(
            listOf("zzz_meta", "mmm_zygisk", "zzz_lsposed", "mmm_web", "zzz_action", "aaa_plain"),
            sorted(modules),
        )
    }

    @Test
    fun `unticked groups fall below the ticked ones and sort by id`() {
        val modules = listOf(
            facts("aaa_plain"),
            facts("bbb_web", webUi = true),
            facts("ccc_action", action = true),
        )

        // Only the web UI group is lifted; the action group now sorts with the rest by id.
        assertEquals(
            listOf("bbb_web", "aaa_plain", "ccc_action"),
            sorted(modules, setOf(ModuleSortGroup.WebUi)),
        )
    }

    @Test
    fun `unticking everything is plain alphabetical order`() {
        val modules = listOf(facts("bbb_web", webUi = true), facts("aaa_plain"))
        assertEquals(listOf("aaa_plain", "bbb_web"), sorted(modules, emptySet()))
    }

    @Test
    fun `enabled first floats enabled modules above disabled ones within their group`() {
        val modules = listOf(
            facts("aaa_disabled", enabled = false),
            facts("zzz_enabled", enabled = true),
        )

        assertEquals(listOf("aaa_disabled", "zzz_enabled"), sorted(modules))
        assertEquals(
            listOf("zzz_enabled", "aaa_disabled"),
            sorted(modules, enabledFirst = true),
        )
    }

    @Test
    fun `enabled first does not reorder across groups`() {
        // The metamodule is switched off, but it still outranks the switched-on Zygisk module.
        val modules = listOf(
            facts("aaa_zygisk", enabled = true),
            facts("zzz_meta", meta = true, enabled = false),
        )

        assertEquals(
            listOf("zzz_meta", "aaa_zygisk"),
            sorted(modules, enabledFirst = true),
        )
    }

    @Test
    fun `the manual order decides the places inside a kind`() {
        val modules = listOf(
            facts("aaa_plain"),
            facts("bbb_plain"),
            facts("ccc_plain"),
        )

        assertEquals(
            listOf("ccc_plain", "aaa_plain", "bbb_plain"),
            sorted(modules, priorities = emptySet(), manualOrder = listOf("ccc_plain", "aaa_plain", "bbb_plain")),
        )
    }

    @Test
    fun `a ticked kind still outranks the manual order`() {
        val modules = listOf(
            facts("zzz_meta", meta = true),
            facts("aaa_web", webUi = true),
        )

        assertEquals(
            listOf("zzz_meta", "aaa_web"),
            sorted(
                modules,
                priorities = setOf(ModuleSortGroup.MetaModule),
                manualOrder = listOf("aaa_web", "zzz_meta"),
            ),
        )
    }

    @Test
    fun `modules the manual order does not mention follow the name order`() {
        val modules = listOf(
            facts("aaa_plain", name = "Zeta"),
            facts("bbb_plain", name = "Alpha"),
            facts("ccc_plain", name = "Mid"),
        )

        assertEquals(
            listOf("ccc_plain", "bbb_plain", "aaa_plain"),
            sorted(
                modules,
                priorities = emptySet(),
                nameOrder = ModuleNameOrder.Ascending,
                manualOrder = listOf("ccc_plain"),
            ),
        )
    }

    @Test
    fun `enabled first is a no-op when every module shares the same state`() {
        val allEnabled = listOf(facts("bbb_web", webUi = true), facts("aaa_plain"))
        val allDisabled = allEnabled.map { it.copy(enabled = false) }

        assertEquals(sorted(allEnabled), sorted(allEnabled, enabledFirst = true))
        assertEquals(sorted(allDisabled), sorted(allDisabled, enabledFirst = true))
    }

    @Test
    fun `priority store round trips and treats an empty choice as a choice`() {
        val all = ModuleSortPriorityGroups.toSet()
        assertEquals(all, ModuleSortPriorityStore.decode(ModuleSortPriorityStore.encode(all)))
        assertEquals(
            setOf(ModuleSortGroup.Zygisk, ModuleSortGroup.WebUi),
            ModuleSortPriorityStore.decode(
                ModuleSortPriorityStore.encode(setOf(ModuleSortGroup.WebUi, ModuleSortGroup.Zygisk)),
            ),
        )
        assertEquals(all, ModuleSortPriorityStore.decode(null))
        assertEquals(emptySet<ModuleSortGroup>(), ModuleSortPriorityStore.decode(""))
        // Values from another version are ignored, and all groups is the safer reading.
        assertEquals(all, ModuleSortPriorityStore.decode("unknown"))
        assertEquals(all, ModuleSortPriorityStore.decode("unknown,zeta"))
    }

    @Test
    fun `priority store ignores unknown tokens beside known ones`() {
        assertEquals(
            setOf(ModuleSortGroup.LSPosed),
            ModuleSortPriorityStore.decode("lsposed,whatever"),
        )
    }

    @Test
    fun `custom order store round trips and drops blanks`() {
        assertEquals(
            listOf("alpha", "beta"),
            ModuleCustomOrderStore.decode(ModuleCustomOrderStore.encode(listOf("alpha", "", "beta", "alpha"))),
        )
        assertEquals(emptyList<String>(), ModuleCustomOrderStore.decode(null))
        assertEquals(emptyList<String>(), ModuleCustomOrderStore.decode(""))
    }

    @Test
    fun `reconcile keeps installed order and appends new modules`() {
        assertEquals(
            listOf("b", "c", "a"),
            reconcileCustomOrder(order = listOf("b", "gone", "c"), moduleIds = listOf("a", "b", "c")),
        )
    }

    @Test
    fun `reconcile leaves an unset order unset`() {
        assertEquals(emptyList<String>(), reconcileCustomOrder(order = emptyList(), moduleIds = listOf("a", "b")))
    }

    @Test
    fun `reconcile keeps the order when the installed list reads empty`() {
        // A refresh that fails returns no modules; that must not erase the reader's order.
        assertEquals(
            listOf("b", "a"),
            reconcileCustomOrder(order = listOf("b", "a"), moduleIds = emptyList()),
        )
    }

    @Test
    fun `name order reads the name a reader sees instead of the id`() {
        val modules = listOf(
            facts("zzz_fix", name = "Alpha"),
            facts("aaa_fix", name = "Beta"),
        )

        assertEquals(listOf("aaa_fix", "zzz_fix"), sorted(modules))
        assertEquals(
            listOf("zzz_fix", "aaa_fix"),
            sorted(modules, nameOrder = ModuleNameOrder.Ascending),
        )
        assertEquals(
            listOf("aaa_fix", "zzz_fix"),
            sorted(modules, nameOrder = ModuleNameOrder.Descending),
        )
    }

    @Test
    fun `name order never lifts a module out of its group`() {
        val modules = listOf(
            facts("aaa_web", name = "Zeta", webUi = true),
            facts("zzz_plain", name = "Alpha"),
        )

        assertEquals(
            listOf("aaa_web", "zzz_plain"),
            sorted(modules, nameOrder = ModuleNameOrder.Ascending),
        )
    }

    @Test
    fun `name order falls back to the id when two modules share a name`() {
        val modules = listOf(
            facts("bbb", name = "Same"),
            facts("aaa", name = "Same"),
        )

        assertEquals(
            listOf("aaa", "bbb"),
            sorted(modules, nameOrder = ModuleNameOrder.Ascending),
        )
        assertEquals(
            listOf("aaa", "bbb"),
            sorted(modules, nameOrder = ModuleNameOrder.Descending),
        )
    }

    @Test
    fun `name order store falls back to the default`() {
        assertEquals(
            ModuleNameOrder.Ascending,
            ModuleNameOrderStore.decode(ModuleNameOrderStore.encode(ModuleNameOrder.Ascending)),
        )
        assertEquals(ModuleNameOrder.Default, ModuleNameOrderStore.decode(null))
        assertEquals(ModuleNameOrder.Default, ModuleNameOrderStore.decode("size_desc"))
    }

    @Test
    fun `moved takes an item out and puts it back at the target`() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
    }

    @Test
    fun `moved leaves the list alone when the drag goes nowhere`() {
        val items = listOf("a", "b", "c")
        assertSame(items, items.moved(1, 1))
        assertSame(items, items.moved(-1, 2))
        assertSame(items, items.moved(0, 3))
        assertSame(items, items.moved(3, 0))
    }
}
