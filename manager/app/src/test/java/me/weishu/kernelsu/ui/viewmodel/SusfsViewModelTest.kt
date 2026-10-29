package me.weishu.kernelsu.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.weishu.kernelsu.data.boot.FakeKernelFactsSource
import me.weishu.kernelsu.data.boot.testKernelFacts
import me.weishu.kernelsu.data.boot.testKernelSlot
import me.weishu.kernelsu.data.susfs.SusfsDataSource
import me.weishu.kernelsu.data.susfs.SusfsStatus
import me.weishu.kernelsu.data.uts.UtsDataSource
import me.weishu.kernelsu.data.uts.UtsFields
import me.weishu.kernelsu.data.uts.UtsTarget
import me.weishu.kernelsu.data.uts.UtsTargetApp
import me.weishu.kernelsu.data.uts.UtsViewMode
import me.weishu.kernelsu.data.uts.UtsViewStatus
import me.weishu.kernelsu.ui.util.RootShellStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SusfsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSusfs()
    private val uts = FakeUts()
    private val kernelFacts = FakeKernelFactsSource()
    private var generation = 1L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = SusfsViewModel(
        repository = repository,
        utsRepository = uts,
        kernelFactsSource = kernelFacts,
        rootStatus = flowOf(RootShellStatus.Ready),
        rootGeneration = flowOf(1L),
        shellGeneration = { generation },
    )

    @Test
    fun `saving uname never changes UTS`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.save("""{"settings":{"uname":{"release":"6.6.0","version":"#1"}}}""")
        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
        assertEquals(0, uts.disableCalls)
        assertEquals(0, uts.enableCalls)
        assertEquals(0, uts.clearCalls)
    }

    @Test
    fun `late stale refresh cannot overwrite newer status`() = runTest(dispatcher) {
        val stale = CompletableDeferred<Unit>()
        repository.blockNextStatus = stale
        repository.statusValue = repository.statusValue.copy(management = "saved")
        val model = model()
        runCurrent()

        repository.statusValue = repository.statusValue.copy(management = "applied")
        model.refresh()
        advanceUntilIdle()
        stale.complete(Unit)
        advanceUntilIdle()

        assertEquals("applied", model.state.value.status.management)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun `boot image facts remain available when the SUSFS config is invalid`() = runTest(dispatcher) {
        repository.statusValue = SusfsStatus(protocol = "compatible", error = "invalid config")
        kernelFacts.value = testKernelFacts(
            testKernelSlot("a", "6.6.30-a", "#1 A", "hash-a", current = true),
        )

        val model = model()
        advanceUntilIdle()

        assertEquals("6.6.30-a", model.state.value.kernelFacts!!.slot("a")!!.release!!.value)
        assertEquals(null, model.state.value.kernelFactsError)
    }

    private class FakeSusfs : SusfsDataSource {
        var statusValue = SusfsStatus(protocol = "compatible")
        var blockNextStatus: CompletableDeferred<Unit>? = null
        val saved = mutableListOf<String>()

        override suspend fun status(): SusfsStatus {
            val snapshot = statusValue
            blockNextStatus?.let {
                blockNextStatus = null
                it.await()
            }
            return snapshot
        }

        override suspend fun export() = """{"automatic":false,"settings":{},"rules":[],"lkm":{}}"""
        override suspend fun save(text: String) {
            saved += text
        }

        override suspend fun apply() =
            """{"status":"applied","applied":[],"failures":{},"pending_reboot":[]}"""
    }

    private class FakeUts : UtsDataSource {
        var disableCalls = 0
        var enableCalls = 0
        var clearCalls = 0

        override suspend fun status() = UtsViewStatus()
        override suspend fun loadApps(): List<UtsTargetApp> = emptyList()
        override suspend fun save(
            fields: UtsFields,
            enabled: Boolean,
            mode: UtsViewMode,
            targets: List<UtsTarget>,
        ) = Unit

        override suspend fun enable() {
            enableCalls++
        }

        override suspend fun disable() {
            disableCalls++
        }

        override suspend fun clear() {
            clearCalls++
        }

        override suspend fun takeOver(
            fields: UtsFields,
            mode: UtsViewMode,
            targets: List<UtsTarget>,
        ) = Unit
    }
}
