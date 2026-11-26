package com.zelvior.salt.core.plugins

import kotlinx.serialization.Serializable

/**
 * Phase 2 (Modular Plugin Engine) — declarative JSON/webhook tile schema scaffold.
 * Users import lightweight JSON-defined tiles that render natively inside the Compose grid.
 */
interface PluginRegistry {
    // suspend fun load(definition: PluginDefinition)
    // suspend fun renderAll(): List<PluginTile>
}

@Serializable
data class PluginDefinition(
    val id: String,
    val title: String,
    val type: PluginType,
    val endpoint: String? = null,
    val method: String = "GET",
    val body: String? = null
)

@Serializable
enum class PluginType { STATUS_CHECK, WEBHOOK, TIMER, TASKER }
