package me.weishu.kernelsu.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.weishu.kernelsu.data.boot.FakeKernelFactsSource
import me.weishu.kernelsu.data.boot.testKernelFacts
import me.weishu.kernelsu.data.boot.testKernelSlot
import me.weishu.kernelsu.data.uts.UtsDataSource
import me.weishu.kernelsu.data.uts.UtsFields
import me.weishu.kernelsu.data.uts.UtsTarget
import me.weishu.kernelsu.data.uts.UtsTargetApp
import me.weishu.kernelsu.data.uts.UtsViewMode
import me.weishu.kernelsu.data.uts.UtsViewStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UtsViewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeUtsDataSource()
    private val kernelFacts = FakeKernelFactsSource()
    private var shellGenerationValue = 1L
    private val current = UtsFields(
        sysname = "Linux",
        nodename = "real-device",
        release = "6.6.118-android15",
        version = "#1 SMP",
        machine = "aarch64",
        mask = 0x1f,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = UtsViewViewModel(
        repository = repository,
        kernelFactsSource = kernelFacts,
        readUname = { current },
        shellGeneration = { shellGenerationValue },
        getString = { "message" },
    )

    @Test
    fun `first failure does not suppress the recovered draft`() = runTest(dispatcher) {
        repository.statusError = IllegalStateException("root unavailable")
        kernelFacts.value = testKernelFacts(
            testKernelSlot("a", "6.6.30-a", "#1 A", "hash-a", current = true),
        )
        val model = model()
        advanceUntilIdle()
        assertFalse(model.uiState.value.hasLoaded)
        assertEquals("6.6.30-a", model.uiState.value.kernelFacts!!.slot("a")!!.release!!.value)

        repository.statusError = null
        repository.current = status(mask = 4, release = "6.1.75-android14")
        model.refresh()
        advanceUntilIdle()

        assertTrue(model.uiState.value.hasLoaded)
        assertEquals("6.1.75-android14", model.uiState.value.draft.release)
    }

    @Test
    fun `late stale refresh cannot overwrite a newer response`() = runTest(dispatcher) {
        val staleResponse = CompletableDeferred<Unit>()
        repository.blockNextStatus = staleResponse
        repository.current = status(mask = 4, release = "old")
        val model = model()
        runCurrent()

        repository.current = status(mask = 4, release = "new")
        model.refresh()
        advanceUntilIdle()
        staleResponse.complete(Unit)
        advanceUntilIdle()

        assertEquals("new", model.uiState.value.draft.release)
    }

    @Test
    fun `shell generation change drops an operation result and releases busy`() = runTest(dispatcher) {
        repository.current = status(enabled = true, active = true, mask = 4, release = "spoofed")
        val model = model()
        advanceUntilIdle()

        val blocked = CompletableDeferred<Unit>()
        repository.blockNextStatus = blocked
        model.setEnabled(false)
        runCurrent()
        shellGenerationValue = 2L
        blocked.complete(Unit)
        advanceUntilIdle()

        assertFalse(model.uiState.value.busy)
        assertTrue(model.uiState.value.status.enabled)

        shellGenerationValue = 3L
        model.refresh()
        advanceUntilIdle()

        assertFalse(model.uiState.value.status.enabled)
    }

    @Test
    fun `disabling never saves a dirty draft`() = runTest(dispatcher) {
        repository.current = status(enabled = true, active = true, mask = 4, release = "spoofed")
        val model = model()
        advanceUntilIdle()

        model.setField(2, "")
        model.setEnabled(false)
        advanceUntilIdle()

        assertTrue(repository.saves.isEmpty())
        assertEquals(1, repository.disableCalls)
        assertFalse(model.uiState.value.status.enabled)
        assertEquals("", model.uiState.value.draft.release)
    }

    @Test
    fun `operation failure clears busy and keeps the message`() = runTest(dispatcher) {
        repository.current = status(enabled = true, active = true, mask = 4, release = "spoofed")
        val model = model()
        advanceUntilIdle()

        repository.disableError = IllegalStateException("disable failed")
        model.setEnabled(false)
        advanceUntilIdle()

        assertFalse(model.uiState.value.busy)
        assertEquals("disable failed", model.uiState.value.message)
    }

    @Test
    fun `operation failure refreshes an outcome that completed after timeout`() = runTest(dispatcher) {
        repository.current = status(enabled = true, active = true, mask = 4, release = "spoofed")
        val model = model()
        advanceUntilIdle()

        repository.disableError = IllegalStateException("command timed out")
        repository.current = status(enabled = false, active = false, mask = 4, release = "spoofed")
        model.setEnabled(false)
        advanceUntilIdle()

        assertEquals("command timed out", model.uiState.value.message)
        assertFalse(model.uiState.value.status.enabled)
        assertFalse(model.uiState.value.busy)
    }

    @Test
    fun `enabling a dirty draft never saves implicitly`() = runTest(dispatcher) {
        repository.current = status(mask = 4, release = "saved")
        val model = model()
        advanceUntilIdle()

        model.setField(2, "draft")
        model.setEnabled(true)
        advanceUntilIdle()

        assertTrue(repository.saves.isEmpty())
        assertEquals(0, repository.enableCalls)
    }

    @Test
    fun `fill current values preserves domainname and its mask`() = runTest(dispatcher) {
        repository.current = status()
        val model = model()
        advanceUntilIdle()

        model.setField(5, "draft.example")
        model.fillCurrentValues()

        assertEquals("draft.example", model.uiState.value.draft.domainname)
        assertEquals("6.6.118-android15", model.uiState.value.draft.release)
        assertEquals(0b100000, model.uiState.value.draft.mask and 0b100000)
    }

    @Test
    fun `provider change refreshes the displayed true kernel value`() = runTest(dispatcher) {
        repository.current = status(
            mask = 4,
            release = "saved",
            unameApplied = true,
            unameScheduled = true,
        )
        val model = model()
        advanceUntilIdle()
        assertNull(model.uiState.value.currentUname)

        model.takeOver()
        advanceUntilIdle()

        assertEquals("6.6.118-android15", model.uiState.value.currentUname?.release)
        assertEquals("uts", model.uiState.value.status.provider)
    }

    @Test
    fun `scheduled SUSFS uname does not claim the active provider`() = runTest(dispatcher) {
        repository.current = status(mask = 4, release = "saved", unameScheduled = true)
        val model = model()
        advanceUntilIdle()

        assertEquals("none", model.uiState.value.status.provider)
        assertEquals("6.6.118-android15", model.uiState.value.currentUname?.release)
    }

    @Test
    fun `global switch saves scope without committing field drafts`() = runTest(dispatcher) {
        repository.current = status(mask = 4, release = "saved")
        val model = model()
        advanceUntilIdle()

        model.setField(2, "draft")
        model.setGlobalMode(true)
        advanceUntilIdle()

        assertEquals(1, repository.saves.size)
        assertEquals("saved", repository.saves.single().fields.release)
        assertEquals(UtsViewMode.Global, repository.saves.single().mode)
        assertEquals("draft", model.uiState.value.draft.release)
    }

    @Test
    fun `selected mode cannot enable with an empty target list`() = runTest(dispatcher) {
        repository.current = status(mask = 4, release = "saved", targets = emptyList())
        val model = model()
        advanceUntilIdle()

        model.setEnabled(true)
        advanceUntilIdle()

        assertEquals(0, repository.enableCalls)
    }

    @Test
    fun `using a boot slot changes only the draft`() = runTest(dispatcher) {
        repository.current = status(mask = 0)
        kernelFacts.value = testKernelFacts(
            testKernelSlot("a", "6.6.30-slot-a", "#1 A", "hash-a", current = true),
            testKernelSlot("b", "6.6.30-slot-b", "#1 B", "hash-b"),
        )
        val model = model()
        advanceUntilIdle()

        model.useBootSlot("b")

        assertEquals("6.6.30-slot-b", model.uiState.value.draft.release)
        assertEquals("#1 B", model.uiState.value.draft.version)
        assertEquals("b", model.uiState.value.draftSourceSlot)
        assertTrue(model.uiState.value.draftSourceIdentity!!.contains("hash-b"))
        assertTrue(repository.saves.isEmpty())
        assertEquals(0, repository.enableCalls)
        assertEquals("", model.uiState.value.status.fields.release)
    }

    @Test
    fun `manual edit clears the boot image draft provenance`() = runTest(dispatcher) {
        repository.current = status(mask = 0)
        kernelFacts.value = testKernelFacts(
            testKernelSlot("a", "6.6.30-slot-a", "#1 A", "hash-a", current = true),
        )
        val model = model()
        advanceUntilIdle()

        model.useBootSlot("a")
        model.setField(2, "manual")

        assertEquals("manual", model.uiState.value.draft.release)
        assertNull(model.uiState.value.draftSourceSlot)
        assertNull(model.uiState.value.draftSourceIdentity)
    }

    @Test
    fun `refreshing an updated image keeps the draft and exposes its old provenance`() = runTest(dispatcher) {
        repository.current = status(mask = 0)
        kernelFacts.value = testKernelFacts(
            testKernelSlot("a", "6.6.30-old", "#1 old", "hash-old", current = true),
        )
        val model = model()
        advanceUntilIdle()
        model.useBootSlot("a")
        val draftIdentity = model.uiState.value.draftSourceIdentity

        kernelFacts.value = testKernelFacts(
            testKernelSlot("a", "6.6.30-new", "#1 new", "hash-new", current = true),
        )
        model.refresh()
        advanceUntilIdle()

        assertEquals("6.6.30-old", model.uiState.value.draft.release)
        assertEquals(draftIdentity, model.uiState.value.draftSourceIdentity)
        assertTrue(model.uiState.value.kernelFacts!!.slot("a")!!.sourceIdentity != draftIdentity)
    }

    @Test
    fun `changing runtime uname never rewrites boot image original values`() = runTest(dispatcher) {
        var live = current.copy(release = "6.6.30-original")
        repository.current = status(mask = 0)
        kernelFacts.value = testKernelFacts(
            testKernelSlot("a", "6.6.30-image", "#1 image", "hash-a", current = true),
        )
        val model = UtsViewViewModel(
            repository = repository,
            kernelFactsSource = kernelFacts,
            readUname = { live },
            shellGeneration = { shellGenerationValue },
            getString = { "message" },
        )
        advanceUntilIdle()

        live = live.copy(release = "6.6.30-spoofed")
        model.refresh()
        advanceUntilIdle()

        assertEquals("6.6.30-spoofed", model.uiState.value.currentUname!!.release)
        assertEquals(
            "6.6.30-image",
            model.uiState.value.kernelFacts!!.slot("a")!!.release!!.value,
        )
    }

    @Test
    fun `app enumeration failure is visible and not marked loaded`() = runTest(dispatcher) {
        repository.current = status()
        repository.appsError = IllegalStateException("package service failed")
        val model = model()
        advanceUntilIdle()

        model.loadApps()
        advanceUntilIdle()

        assertEquals("package service failed", model.uiState.value.message)
        assertFalse(model.uiState.value.appsLoaded)
        assertFalse(model.uiState.value.appsLoading)
    }

    private fun status(
        enabled: Boolean = false,
        active: Boolean = false,
        mask: Int = 0,
        release: String = "",
        unameApplied: Boolean = false,
        unameScheduled: Boolean = false,
        mode: UtsViewMode = UtsViewMode.Selected,
        targets: List<UtsTarget> = listOf(UtsTarget("com.example.app", 10123)),
    ) = UtsViewStatus(
        supported = true,
        enabled = enabled,
        active = active,
        mode = mode,
        fieldMask = mask,
        fields = UtsFields(release = release, mask = mask),
        targets = targets,
        source = when {
            active -> "uts"
            unameApplied -> "susfs"
            else -> "none"
        },
        unameApplied = unameApplied,
        unameScheduled = unameScheduled,
    )

    private class FakeUtsDataSource : UtsDataSource {
        data class Saved(
            val fields: UtsFields,
            val enabled: Boolean,
            val mode: UtsViewMode,
            val targets: List<UtsTarget>,
        )

        var current = UtsViewStatus()
        var statusError: Throwable? = null
        var appsError: Throwable? = null
        var disableError: Throwable? = null
        var blockNextStatus: CompletableDeferred<Unit>? = null
        val saves = mutableListOf<Saved>()
        var disableCalls = 0
        var enableCalls = 0

        private suspend fun nextStatus(): UtsViewStatus {
            val snapshot = current
            blockNextStatus?.let {
                blockNextStatus = null
                it.await()
            }
            statusError?.let { throw it }
            return snapshot
        }

        override suspend fun status(): UtsViewStatus = nextStatus()

        override suspend fun loadApps(): List<UtsTargetApp> {
            appsError?.let { throw it }
            return emptyList()
        }

        override suspend fun save(
            fields: UtsFields,
            enabled: Boolean,
            mode: UtsViewMode,
            targets: List<UtsTarget>,
        ) {
            saves += Saved(fields, enabled, mode, targets)
            current = current.copy(
                fields = fields,
                fieldMask = fields.fieldMask(),
                mode = mode,
                targets = targets,
            )
        }

        override suspend fun enable() {
            enableCalls++
            current = current.copy(enabled = true, active = true)
        }

        override suspend fun disable() {
            disableCalls++
            disableError?.let { throw it }
            current = current.copy(enabled = false, active = false)
        }

        override suspend fun clear() {
            current = UtsViewStatus()
        }

        override suspend fun takeOver(
            fields: UtsFields,
            mode: UtsViewMode,
            targets: List<UtsTarget>,
        ) {
            current = current.copy(
                enabled = true,
                active = true,
                fields = fields,
                fieldMask = fields.fieldMask(),
                mode = mode,
                targets = targets,
                unameApplied = false,
                unameScheduled = false,
            )
        }
    }
}
