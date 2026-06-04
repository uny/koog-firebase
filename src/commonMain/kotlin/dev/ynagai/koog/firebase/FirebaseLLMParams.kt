package dev.ynagai.koog.firebase

import ai.koog.prompt.params.LLMParams
import dev.ynagai.firebase.ai.ThinkingConfig
import kotlinx.serialization.json.JsonElement

/**
 * Firebase-specific [LLMParams] that additionally carries a Gemini [ThinkingConfig].
 *
 * Build a prompt with this to control the model's thinking budget/level, e.g.:
 * ```kotlin
 * val prompt = prompt("id", params = FirebaseLLMParams(
 *     thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.HIGH, includeThoughts = true),
 * )) { ... }
 * ```
 *
 * Mirrors Koog's own `GoogleLLMParams` pattern: the client reads the extra config via
 * `prompt.params as? FirebaseLLMParams`.
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
) : LLMParams(
    temperature = temperature,
    maxTokens = maxTokens,
    numberOfChoices = numberOfChoices,
    speculation = speculation,
    schema = schema,
    toolChoice = toolChoice,
    user = user,
    additionalProperties = additionalProperties,
)
