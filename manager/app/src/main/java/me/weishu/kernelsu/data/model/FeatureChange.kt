package me.weishu.kernelsu.data.model

data class FeatureChange(
    val id: Int,
    val name: String,
    val expected: Long,
    val actual: Long,
)
