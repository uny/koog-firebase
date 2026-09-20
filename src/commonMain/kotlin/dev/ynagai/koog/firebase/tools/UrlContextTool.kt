package dev.ynagai.koog.firebase.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import dev.ynagai.firebase.ai.Tool
import dev.ynagai.koog.firebase.FirebaseMetadataKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A Koog tool that reads a web page through Gemini's URL context built-in tool, for agents that
 * also use their own function tools.
 *
 * Like [GoogleSearchTool], it exists because Firebase AI Logic cannot combine
 * `Tool.urlContext()` with function declarations in one request: the page is fetched and read
 * by a separate, tool-free request through [executor], and the model's answer to [Args.question]
 * about the page is returned as text. A page Gemini could not retrieve (paywall, unsafe, error)
 * yields an explanatory message rather than an exception so the agent can recover.
 *
 * @param executor Executor used for the URL-context request (typically the agent's own).
 * @param model Model used for the URL-context request.
 * @param name Tool name exposed to the agent's model.
 * @param systemPrompt Instruction for the URL-context request.
 */
class UrlContextTool(
    executor: PromptExecutor,
    model: LLModel,
    name: String = "fetch_url",
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) : SimpleTool<UrlContextTool.Args>(
    argsType = typeToken<Args>(),
    name = name,
    description = "Fetch a web page by URL and answer a question about its content, or summarize it. " +
        "Use it when the user gives a URL or when you need the content of a specific page.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The absolute http(s) URL of the page to read.")
        val url: String,
        @property:LLMDescription("What to extract or answer from the page. Defaults to a concise summary.")
        val question: String? = null,
    )

    private val runner = BuiltInToolRunner(executor, model, listOf(Tool.urlContext()))

    override suspend fun execute(args: Args): String {
        val task = args.question?.takeIf { it.isNotBlank() } ?: "Summarize the page concisely."
        val result = runner.run(id = "fetch_url", systemPrompt = systemPrompt, userPrompt = "URL: ${args.url}\n\n$task")
        val statuses = result.metadata?.get(FirebaseMetadataKeys.URL_CONTEXT_METADATA)?.jsonObject
            ?.get("urlMetadata")?.jsonArray
            ?.map { it.jsonObject["urlRetrievalStatus"]?.jsonPrimitive?.content ?: "UNSPECIFIED" }
            .orEmpty()
        val failed = statuses.isNotEmpty() && statuses.none { it == "SUCCESS" }
        return if (failed) {
            "Could not retrieve ${args.url} (status: ${statuses.distinct().joinToString()}). ${result.text}".trim()
        } else {
            result.text
        }
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT: String =
            "Read the page at the given URL and respond to the task using only its content. " +
                "If the page could not be retrieved, say so briefly."
    }
}
