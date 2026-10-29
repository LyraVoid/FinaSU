package me.weishu.kernelsu.data.boot

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.RootShell
import java.util.concurrent.TimeUnit

interface KernelFactsSource {
    suspend fun load(): KernelFacts
}

/** One bounded, read-only probe shared by SUSFS and UTS View. */
class KernelFactsRepository(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KernelFactsSource {
    override suspend fun load(): KernelFacts = withContext(dispatcher) {
        val shell = withTimeout(15_000L) { RootShell.awaitRoot() }
        val path = ksuApp.applicationInfo.nativeLibraryDir + "/libksud.so"
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val future = shell.newJob()
            .add("${quote(path)} boot-info kernel-facts")
            .to(output, errors)
            .enqueue()
        val result = try {
            future.get(45, TimeUnit.SECONDS)
        } finally {
            if (!future.isDone) future.cancel(false)
        }
        check(result.isSuccess) {
            errors.joinToString("\n").ifEmpty { "Boot facts failed (${result.code})" }
        }
        KernelFacts.parse(output.joinToString("\n"))
    }
}
