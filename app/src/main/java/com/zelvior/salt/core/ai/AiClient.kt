package com.zelvior.salt.core.ai

import kotlinx.serialization.Serializable

/**
 * Phase 2 (AI Context & Action Layer) — interface scaffold for an OpenAI-compatible
 * streaming client (SSE). A Ktor-backed implementation will land here.
 */
interface AiClient {
    // Feature A — One-Line Digest
    // fun digest(notifications: List<NotificationSummary>): Flow<String>

    // Feature B — Command Pill (natural language -> structured tool-call)
    // fun command(input: String): Flow<ToolCall>
}

@Serializable
data class ToolCall(
    val action: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class NotificationSummary(
    val packageName: String,
    val title: String,
    val text: String
)
