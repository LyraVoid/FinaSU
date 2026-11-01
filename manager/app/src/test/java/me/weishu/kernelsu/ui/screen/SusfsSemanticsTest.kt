package me.weishu.kernelsu.ui.screen

import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.susfs.SusfsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SusfsSemanticsTest {
    @Test
    fun `provider and controller wire values have separate labels`() {
        val builtinProvider = SusfsStatus(
            protocol = "compatible",
            implementation = "builtin",
            ownership = "builtin",
        )
        val lkmProvider = builtinProvider.copy(implementation = "lkm")

        assertEquals(R.string.susfs_provider_builtin, susfsProviderLabelResource(builtinProvider))
        assertEquals(R.string.susfs_provider_lkm, susfsProviderLabelResource(lkmProvider))
        assertEquals(R.string.susfs_owner_builtin, susfsControllerLabelResource(builtinProvider))
        assertEquals(R.string.susfs_owner_builtin, susfsControllerLabelResource(lkmProvider))
        assertEquals(
            R.string.susfs_owner_external,
            susfsControllerLabelResource(builtinProvider.copy(ownership = "external")),
        )
        assertEquals(
            R.string.susfs_owner_external,
            susfsControllerLabelResource(lkmProvider.copy(ownership = "external")),
        )
        assertEquals(R.string.susfs_ownership, susfsControllerHintResource(builtinProvider))
        assertEquals(R.string.susfs_ownership, susfsControllerHintResource(lkmProvider))
        assertEquals(
            R.string.susfs_owner_external_reason,
            susfsControllerHintResource(builtinProvider.copy(ownership = "external")),
        )
        assertEquals(
            R.string.susfs_owner_external_reason,
            susfsControllerHintResource(lkmProvider.copy(ownership = "external")),
        )
        assertEquals(
            R.string.susfs_owner_pending,
            susfsControllerLabelResource(builtinProvider.copy(pendingOwner = "external")),
        )
        assertEquals(
            R.string.susfs_owner_unknown,
            susfsControllerLabelResource(builtinProvider.copy(ownership = "unknown")),
        )
    }

    @Test
    fun `provider evidence maps to explicit states`() {
        assertEquals(
            R.string.susfs_provider_not_detected,
            susfsProviderLabelResource(SusfsStatus(protocol = "not_detected")),
        )
        assertEquals(
            R.string.susfs_provider_unknown,
            susfsProviderLabelResource(SusfsStatus(protocol = "unknown_protocol", implementation = "lkm")),
        )
        assertEquals(
            R.string.susfs_provider_unknown,
            susfsProviderLabelResource(SusfsStatus(protocol = "root_unavailable")),
        )
    }

    @Test
    fun `lkm lifecycle guidance is selected only for lkm providers`() {
        val builtin = SusfsStatus(implementation = "builtin", management = "applied")
        val lkm = builtin.copy(implementation = "lkm")
        val unknown = builtin.copy(implementation = "unknown")

        assertEquals(R.string.susfs_ack_hint, susfsOperationHintResource(builtin))
        assertEquals(R.string.susfs_lkm_ack_hint, susfsOperationHintResource(lkm))
        assertEquals(R.string.susfs_ack_hint, susfsOperationHintResource(unknown))
        assertEquals(R.string.susfs_partial_hint, susfsOperationHintResource(builtin.copy(management = "partial")))
        assertNull(susfsOperationHintResource(builtin.copy(management = "pending_reboot")))
        assertNull(susfsOperationHintResource(builtin.copy(management = "saved", automatic = true)))
        assertEquals(
            R.string.susfs_error_hint,
            susfsOperationHintResource(builtin.copy(management = "failed")),
        )
        assertEquals(
            R.string.susfs_error_hint,
            susfsOperationHintResource(builtin.copy(management = "unknown")),
        )
    }
}
