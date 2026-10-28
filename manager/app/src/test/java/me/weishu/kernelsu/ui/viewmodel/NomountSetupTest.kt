package me.weishu.kernelsu.ui.viewmodel

import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkMountProvider
import me.weishu.kernelsu.data.model.FolkMountStatus
import me.weishu.kernelsu.data.model.NomountStatus
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NomountSetupTest {

    @Test
    fun `setup is offered until VFS is configured`() {
        assertTrue(
            shouldShowNomountSetup(
                nomount(),
                mount(FolkMountMode.AUTO, FolkMountProvider.BUILTIN),
            )
        )
        assertFalse(
            shouldShowNomountSetup(
                null,
                mount(FolkMountMode.AUTO, FolkMountProvider.BUILTIN),
            )
        )
        assertFalse(
            shouldShowNomountSetup(
                nomount(),
                mount(FolkMountMode.NOMOUNT, FolkMountProvider.NOMOUNT),
            )
        )
    }

    @Test
    fun `fallback can be retried but an external owner suppresses built-in setup`() {
        assertTrue(
            shouldShowNomountSetup(
                nomount(),
                mount(FolkMountMode.NOMOUNT, FolkMountProvider.BUILTIN),
            )
        )
        assertFalse(
            shouldShowNomountSetup(
                nomount("external-nomount"),
                mount(FolkMountMode.AUTO, FolkMountProvider.BUILTIN),
            )
        )
    }

    private fun nomount(deferredTo: String? = null) = NomountStatus.fromJson(
        JSONObject()
            .put("schema_version", 2)
            .put("driver_state", "absent")
            .put("api_ok", false)
            .put("deferred_to", deferredTo)
    )

    private fun mount(
        mode: FolkMountMode,
        provider: FolkMountProvider,
    ) = FolkMountStatus(
        configuredMode = mode,
        nextProvider = provider,
        metamoduleId = null,
        metamoduleEnabled = false,
        bootId = "boot",
        bootProvider = provider,
        bootResult = "mounted",
        targetCount = 1,
        bootWarning = null,
        error = null,
    )
}
