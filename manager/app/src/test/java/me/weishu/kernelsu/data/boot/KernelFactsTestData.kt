package me.weishu.kernelsu.data.boot

class FakeKernelFactsSource(
    var value: KernelFacts = KernelFacts(),
) : KernelFactsSource {
    var error: Throwable? = null

    override suspend fun load(): KernelFacts {
        error?.let { throw it }
        return value
    }
}

fun testKernelFacts(vararg slots: KernelSlotFacts): KernelFacts = KernelFacts(
    parser = "finasu-boot-facts/1",
    currentSlot = slots.firstOrNull(KernelSlotFacts::current)?.id,
    slots = slots.toList(),
    readAt = "2026-10-29T00:00:00Z",
)

fun testKernelSlot(
    id: String,
    release: String?,
    version: String?,
    hash: String,
    current: Boolean = false,
    exists: Boolean = true,
    readable: Boolean = true,
    reason: String? = null,
): KernelSlotFacts = KernelSlotFacts(
    id = id,
    current = current,
    exists = exists,
    readable = readable,
    confidence = if (release != null && version != null) "high" else "unavailable",
    reason = reason,
    images = listOf(
        KernelImageFacts(
            role = "boot",
            name = "boot_$id",
            path = "/dev/block/by-name/boot_$id",
            exists = exists,
            readable = readable,
            format = if (readable) "boot_v4" else "unknown",
            headerVersion = if (readable) 4 else null,
            payloadSize = if (readable) 1024 else null,
            sha256 = hash.takeIf { readable },
            avb = null,
            reason = reason,
        ),
    ),
    release = release?.let { KernelFieldValue(it, "boot_$id:linux_banner", "high") },
    version = version?.let { KernelFieldValue(it, "boot_$id:linux_banner", "high") },
    cmdline = emptyList(),
    bootconfig = emptyList(),
    missingFields = buildList {
        if (release == null) add("release")
        if (version == null) add("version")
    },
)
