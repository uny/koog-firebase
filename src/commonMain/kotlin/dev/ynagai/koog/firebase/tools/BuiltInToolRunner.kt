package dev.ynagai.koog.firebase.tools

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import dev.ynagai.firebase.ai.Tool
import dev.ynagai.koog.firebase.FirebaseLLMParams
import dev.ynagai.koog.firebase.FirebaseMetadataKeys
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Runs a single, tool-free Gemini request that uses the given server-side [builtInTools], so a
 * Koog function tool can wrap a built-in tool. Gemini rejects a request that declares built-in
 * tools together with function declarations (see `FirebaseLLMParams.builtInTools`), which is why
 * the wrapping tools in this package make a separate request instead of sharing the agent's.
 */
internal class BuiltInToolRunner(
    private val executor: PromptExecutor,
    private val model: LLModel,
    private val builtInTools: List<Tool>,
) {
    class Result(val text: String, val metadata: JsonObject?)

    /**
     * Sends [userPrompt] (with [systemPrompt] as the system instruction unless blank) and returns
     * the response text. A response without any text (e.g. finish reason `SAFETY` or
     * `MAX_TOKENS` before the first token) yields a short explanatory text instead of an empty
     * string, so the agent can tell "no answer" from an empty answer.
     */
    suspend fun run(id: String, systemPrompt: String?, userPrompt: String): Result {
        val prompt = prompt(id, params = FirebaseLLMParams(builtInTools = builtInTools)) {
            systemPrompt?.takeIf { it.isNotBlank() }?.let { system(it) }
            user(userPrompt)
        }
        val response: Message.Assistant = executor.execute(prompt, model, emptyList())
        val text = response.parts.filterIsInstance<MessagePart.Text>().joinToString("") { it.text }
            .ifBlank { "The model returned no answer (finish reason: ${response.finishReason ?: "unknown"})." }
        return Result(text, response.metaInfo.metadata)
    }
}

/** Renders the web sources of a grounding-metadata object as a Markdown list, or `null` if none. */
internal fun JsonObject?.groundingSourcesMarkdown(): String? {
    val chunks = this?.get(FirebaseMetadataKeys.GROUNDING_METADATA)?.jsonObject
        ?.get("groundingChunks")?.jsonArray ?: return null
    val lines = chunks.mapNotNull { chunk ->
        val web = chunk.jsonObject["web"]?.jsonObject ?: return@mapNotNull null
        val uri = web["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val title = web["title"]?.jsonPrimitive?.content
        if (title != null) "- $title: $uri" else "- $uri"
    }
    return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}
