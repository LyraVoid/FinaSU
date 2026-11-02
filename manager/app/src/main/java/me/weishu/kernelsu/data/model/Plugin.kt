package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

/** A configuration control declared by a plugin manifest. */
@Immutable
data class PluginConfigField(
    val key: String,
    val label: String,
    val labels: Map<String, String> = emptyMap(),
    val type: String,
    val default: String?,
    val options: List<String>,
)

/** The callback a plugin exposes as its quick action. */
@Immutable
data class PluginQuickAction(
    val function: String,
    val label: String,
    val labels: Map<String, String> = emptyMap(),
)

/** An installed plugin as reported by the manager daemon. */
@Immutable
data class Plugin(
    val id: String,
    val name: String,
    val names: Map<String, String> = emptyMap(),
    val author: String,
    val version: String,
    val description: String,
    val descriptions: Map<String, String> = emptyMap(),
    val license: String,
    val enabled: Boolean,
    val entry: String,
    val quickAction: PluginQuickAction?,
    val config: List<PluginConfigField>,
    val depends: List<String>,
    val callbacks: List<String>,
    /** Set when the package cannot be read, in which case the other fields are empty. */
    val has_manifest: Boolean = true, 
    val has_action: Boolean = false,
    val error: String?,
)
