package me.weishu.kernelsu.ui.screen.builtinzygisk

import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkZygiskStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PanelGoldenTest {
    private fun read(name: String) = FolkZygiskStatus.fromJson(JSONObject(
        checkNotNull(javaClass.getResource("/folkzygisk/$name.json")).readText(),
    ))

    @Test fun contract() {
        data class Case(val file: String, val phase: Phase, val title: Int, val actions: List<PanelAction>)
        val cases = listOf(
            Case("injected", Phase.Injected, R.string.folkzygisk_injected, emptyList()),
            Case("abi32", Phase.Injected, R.string.folkzygisk_injected, emptyList()),
            Case("partial", Phase.Partial, R.string.folkzygisk_partial, emptyList()),
            Case("external", Phase.External, R.string.fz_external, emptyList()),
            Case("protected", Phase.Protected, R.string.folkzygisk_startup_protection, listOf(PanelAction.Recover, PanelAction.Reset)),
            Case("missing", Phase.Missing, R.string.folkzygisk_engine_missing, listOf(PanelAction.Install)),
            Case("update", Phase.Update, R.string.fz_update, listOf(PanelAction.Install)),
            Case("starting", Phase.Starting, R.string.folkzygisk_starting, emptyList()),
            Case("failed", Phase.Failed, R.string.folkzygisk_failed, emptyList()),
            Case("pending", Phase.Pending, R.string.folkzygisk_next_enabled, emptyList()),
            Case("unverified", Phase.Unverified, R.string.folkzygisk_unverified, emptyList()),
            Case("disabled", Phase.Disabled, R.string.folkzygisk_next_disabled, emptyList()),
        )
        for (case in cases) {
            val status = read(case.file).copy(pendingRestart = case.file == "pending")
            val panel = mapStatus(status, status.runtime)
            assertEquals(case.file, case.phase, panel.phase)
            assertEquals(case.file, case.title, panel.phase.title)
            assertEquals(case.file, case.actions, panel.actions)
        }
        assertFalse(read("abi32").runtime!!.abis.getValue("32").supported)
        assertEquals(
            listOf(ModuleRow("Sample One (sample-one)", "64 / 32"), ModuleRow("sample-two", "64")),
            mapStatus(read("injected"), read("injected").runtime).modules,
        )
        assertTrue(mapStatus(read("disabled"), null).canToggle)
        assertTrue(mapStatus(read("external"), null).canToggle)
        assertFalse(mapStatus(read("external").copy(enabled = false), null).canToggle)
        assertTrue(mapStatus(read("protected"), null).canToggle)
        assertFalse(mapStatus(read("protected").copy(enabled = false), null).canToggle)
        val only64 = read("abi32")
        assertEquals(R.string.home_unsupported, mapStatus(only64, only64.runtime).abis.last().label)
        assertFalse(sanitized(read("injected")).toString().contains("private-boot"))
    }

    @Test fun rejectsMalformedSnapshots() {
        for (file in listOf("bad_schema", "bad_abi")) assertThrows(Exception::class.java) { read(file) }
        assertNull(FolkZygiskPanelUiState(status = read("injected"), readError = true).panel)
    }
}
