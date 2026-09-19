package dev.ynagai.koog.firebase

import ai.koog.prompt.params.LLMParams
import dev.ynagai.firebase.ai.RetrievalConfig
import dev.ynagai.firebase.ai.ThinkingConfig
import dev.ynagai.firebase.ai.Tool
import kotlinx.serialization.json.JsonElement

/**
 * Firebase-specific [LLMParams] that additionally carries Gemini-only configuration: a
 * [ThinkingConfig] and the server-side built-in tools Firebase AI Logic offers.
 *
 * Build a prompt with this to control the model's thinking budget/level, e.g.:
 * ```kotlin
 * val prompt = prompt("id", params = FirebaseLLMParams(
 *     thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.HIGH, includeThoughts = true),
 * )) { ... }
 * ```
 *
 * Or to let the model ground its answer with Google Search and fetch referenced URLs:
 * ```kotlin
 * val prompt = prompt("id", params = FirebaseLLMParams(
 *     builtInTools = listOf(Tool.googleSearch(), Tool.urlContext()),
 * )) { ... }
 * ```
 *
 * Mirrors Koog's own `GoogleLLMParams` pattern: the client reads the extra config via
 * `prompt.params as? FirebaseLLMParams`.
 *
 * @property thinkingConfig Gemini thinking configuration, or `null` to keep the model default.
 * @property builtInTools Server-side tools executed by Gemini itself (no Koog tool round-trip):
 *   [Tool.googleSearch], [Tool.urlContext], [Tool.codeExecution], [Tool.googleMaps]. These are
 *   sent alongside any Koog function tools. [Tool.FunctionDeclarations] is not allowed here —
 *   declare Koog tools through the agent's tool registry instead.
 * @property retrievalConfig Optional configuration for retrieval tools such as Google Maps
 *   grounding (e.g. the user's location).
 */
class FirebaseLLMParams(
    temperature: Double? = null,
    maxTokens: Int? = null,
    numberOfChoices: Int? = null,
    speculation: String? = null,
    schema: LLMParams.Schema? = null,
    toolChoice: LLMParams.ToolChoice? = null,
    user: String? = null,
    additionalProperties: Map<String, JsonElement>? = null,
    val thinkingConfig: ThinkingConfig? = null,
    val builtInTools: List<Tool> = emptyList(),
    val retrievalConfig: RetrievalConfig? = null,
) : LLMParams(
    temperature = temperature,
    maxTokens = maxTokens,
    numberOfChoices = numberOfChoices,
    speculation = speculation,
    schema = schema,
    toolChoice = toolChoice,
    user = user,
    additionalProperties = additionalProperties,
) {
    init {
        require(builtInTools.none { it is Tool.FunctionDeclarations }) {
            "builtInTools must not contain Tool.FunctionDeclarations; register Koog tools via the tool registry instead."
        }
    }

    /**
     * Koog rewrites params through [LLMParams.copy] (e.g. `Prompt.withUpdatedParams`, the agent
     * session's `setToolChoice*`), so the override keeps the Firebase-specific fields instead of
     * degrading to a plain [LLMParams] and silently dropping them.
     */
    override fun copy(
        temperature: Double?,
        maxTokens: Int?,
        numberOfChoices: Int?,
        speculation: String?,
        schema: LLMParams.Schema?,
        toolChoice: LLMParams.ToolChoice?,
        user: String?,
        additionalProperties: Map<String, JsonElement>?,
    ): FirebaseLLMParams = FirebaseLLMParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
        thinkingConfig = thinkingConfig,
        builtInTools = builtInTools,
        retrievalConfig = retrievalConfig,
    )
}
