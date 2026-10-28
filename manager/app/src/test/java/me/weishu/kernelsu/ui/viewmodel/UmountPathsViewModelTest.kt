package me.weishu.kernelsu.ui.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.weishu.kernelsu.data.umount.UmountPathEntry
import me.weishu.kernelsu.data.umount.UmountPathSource
import me.weishu.kernelsu.data.umount.UmountPathsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UmountPathsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `failed config refresh keeps the last successful entries read only`() =
        runTest(dispatcher) {
            val model = UmountPathsViewModel(repository)
            advanceUntilIdle()
            val loaded = model.uiState.value.paths
            assertFalse(loaded.isEmpty())

            repository.config = Result.failure(IllegalStateException("corrupt"))
            model.refresh()
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(loaded.map { it.path }, state.paths.map { it.path })
            assertTrue(state.paths.all { it.source == UmountPathSource.UNKNOWN })
            assertTrue(state.readError)
            assertFalse(state.canWrite)
        }

    @Test
    fun `old kernel remains writable while showing unsupported`() = runTest(dispatcher) {
        repository.kernel = Result.failure(IllegalStateException("ENOTTY"))
        val model = UmountPathsViewModel(repository)
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.kernelSupported)
        assertTrue(state.configReadable)
        assertTrue(state.canWrite)
        assertEquals(listOf("/a"), state.custom.map { it.path })
    }

    private class FakeRepository : UmountPathsRepository {
        var config: Result<List<UmountPathEntry>> =
            Result.success(listOf(UmountPathEntry("/a", 2)))
        var kernel: Result<List<UmountPathEntry>> =
            Result.success(listOf(UmountPathEntry("/b", 2)))

        override suspend fun loadConfig() = config
        override suspend fun loadKernel() = kernel
        override suspend fun add(path: String) = Result.success(Unit)
        override suspend fun delete(path: String) = Result.success(Unit)
        override suspend fun clear() = Result.success(Unit)
    }
}
