package dev.ynagai.koog.firebase.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import dev.ynagai.firebase.ai.Tool
import dev.ynagai.koog.firebase.FirebaseLLMParams
import dev.ynagai.koog.firebase.FirebaseMetadataKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A Koog tool that answers a query with Google Search grounding, for agents that also use their
 * own function tools.
 *
 * Firebase AI Logic cannot send `Tool.googleSearch()` in the same request as function
 * declarations (Gemini requires `include_server_side_tool_invocations`, which the Firebase SDK
 * does not expose), so this tool runs a separate, tool-free grounded request through [executor]
 * and returns the grounded answer plus its web sources as text. The agent loop stays plain
 * function calling.
 *
 * Google's terms require apps that use Google Search grounding to display the Search
 * Suggestions from `searchEntryPoint.renderedContent`; [onGroundingMetadata] receives the
 * `groundingMetadata` object of each call (the value under
 * [FirebaseMetadataKeys.GROUNDING_METADATA]) so the app can do so.
 *
 * ```kotlin
 * val toolRegistry = ToolRegistry {
 *     tool(GoogleSearchTool(executor, FirebaseModels.Gemini3_7Flash, onGroundingMetadata = { grounding ->
 *         val html = grounding["searchEntryPoint"]?.jsonObject?.get("renderedContent")?.jsonPrimitive?.content
 *         // hand `html` to the UI on the main thread
 *     }))
 *     tool(MyOwnTool())
 * }
 * ```
 *
 * @param executor Executor used for the grounded request (typically the agent's own). It must be
 *   backed by this library's Firebase client; any other executor ignores [FirebaseLLMParams.builtInTools]
 *   and the tool would return an ungrounded answer.
 * @param model Model used for the grounded request.
 * @param name Tool name exposed to the agent's model; also used as the grounded prompt's id.
 * @param systemPrompt Instruction for the grounded request; blank sends no system instruction.
 * @param onGroundingMetadata Receives the `groundingMetadata` object of each call that returned one.
 *   It is invoked on the coroutine that executes the tool (not the main thread, and possibly
 *   concurrently when the agent runs tool calls in parallel), and an exception thrown from it fails
 *   the tool call — dispatch UI work to the main thread instead of doing it inline.
 */
class GoogleSearchTool(
    executor: PromptExecutor,
    model: LLModel,
    name: String = "google_search",
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val onGroundingMetadata: ((JsonObject) -> Unit)? = null,
) : SimpleTool<GoogleSearchTool.Args>(
    argsType = typeToken<Args>(),
    name = name,
    description = "Search the web with Google Search and return an up-to-date, factual answer with its sources. " +
        "Use it for current events, facts you are unsure about, and anything that may have changed recently.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The question or search query, in natural language.")
        val query: String,
    )

    private val runner = BuiltInToolRunner(executor, model, listOf(Tool.googleSearch()))

    override suspend fun execute(args: Args): String {
        val result = runner.run(id = name, systemPrompt = systemPrompt, userPrompt = args.query)
        result.metadata?.get(FirebaseMetadataKeys.GROUNDING_METADATA)?.jsonObject?.let { onGroundingMetadata?.invoke(it) }
        val sources = result.metadata.groundingSourcesMarkdown()
        return if (sources == null) result.text else "${result.text}\n\nSources:\n$sources"
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT: String =
            "Answer the user's query using Google Search. Be factual and specific; include dates, numbers " +
                "and names where relevant. If the search results do not answer the query, say so."
    }
}
