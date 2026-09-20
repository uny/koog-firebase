package dev.ynagai.koog.firebase.tools

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import dev.ynagai.firebase.ai.Tool
import dev.ynagai.koog.firebase.FirebaseLLMParams
import dev.ynagai.koog.firebase.FirebaseMetadataKeys
import dev.ynagai.koog.firebase.FirebaseModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records the prompt it receives and replies with a canned assistant message. */
private class FakeExecutor(
    private val reply: String,
    private val metadata: JsonObject? = null,
    private val finishReason: String? = null,
) : PromptExecutor() {
    var lastPrompt: Prompt? = null
    var lastTools: List<ToolDescriptor>? = null

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        lastPrompt = prompt
        lastTools = tools
        return Message.Assistant(
            parts = listOf(MessagePart.Text(reply)),
            metaInfo = ResponseMetaInfo.create(KoogClock.System, metadata = metadata),
            finishReason = finishReason,
        )
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = emptyFlow()

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("unused")

    override fun close() {}
}

private fun Prompt.userText(): String =
    messages.filterIsInstance<Message.User>().single().parts.filterIsInstance<MessagePart.Text>().joinToString("") { it.text }

private fun Prompt.systemText(): String? =
    messages.filterIsInstance<Message.System>().singleOrNull()?.parts?.filterIsInstance<MessagePart.Text>()?.joinToString("") { it.text }

private fun groundingMetadata(vararg sources: Pair<String, String?>): JsonObject = buildJsonObject {
    putJsonObject(FirebaseMetadataKeys.GROUNDING_METADATA) {
        putJsonArray("webSearchQueries") { }
        putJsonObject("searchEntryPoint") { put("renderedContent", "<div/>") }
        putJsonArray("groundingChunks") {
            // A maps-only chunk, as GroundingMappers emits for Google Maps grounding: must be skipped.
            add(buildJsonObject { putJsonObject("maps") { put("uri", "https://maps.example"); put("title", "Place") } })
            sources.forEach { (uri, title) ->
                add(buildJsonObject { putJsonObject("web") { put("uri", uri); title?.let { put("title", it) } } })
            }
        }
    }
}

private fun urlContextMetadata(vararg statuses: String): JsonObject = buildJsonObject {
    putJsonObject(FirebaseMetadataKeys.URL_CONTEXT_METADATA) {
        putJsonArray("urlMetadata") {
            statuses.forEach { add(buildJsonObject { put("retrievedUrl", "https://example.com"); put("urlRetrievalStatus", it) }) }
        }
    }
}

class BuiltInWrapperToolsTest {
    private val model = FirebaseModels.Gemini3_7Flash

    @Test
    fun googleSearchRunsToolFreeGroundedRequest() = runTest {
        val executor = FakeExecutor("Sunny in Tokyo.", groundingMetadata("https://a.example" to "A", "https://b.example" to null))
        var seen: JsonObject? = null
        val tool = GoogleSearchTool(executor, model, onGroundingMetadata = { seen = it })

        val result = tool.execute(GoogleSearchTool.Args("weather in Tokyo"))

        assertEquals("Sunny in Tokyo.\n\nSources:\n- A: https://a.example\n- https://b.example", result)
        assertEquals(emptyList(), executor.lastTools)
        val params = assertIs<FirebaseLLMParams>(executor.lastPrompt!!.params)
        assertEquals(listOf(Tool.GoogleSearch), params.builtInTools)
        assertEquals("weather in Tokyo", executor.lastPrompt!!.userText())
        assertEquals("google_search", executor.lastPrompt!!.id)
        assertEquals(GoogleSearchTool.DEFAULT_SYSTEM_PROMPT, executor.lastPrompt!!.systemText())
        assertEquals("<div/>", seen?.get("searchEntryPoint")?.jsonObject?.get("renderedContent")?.jsonPrimitive?.content)
    }

    @Test
    fun googleSearchUsesCustomNameAndSystemPromptAndSkipsEmptySources() = runTest {
        val executor = FakeExecutor("Answer.", groundingMetadata())
        val tool = GoogleSearchTool(executor, model, name = "news_search", systemPrompt = "Be brief.")

        assertEquals("Answer.", tool.execute(GoogleSearchTool.Args("q")))
        assertEquals("news_search", executor.lastPrompt!!.id)
        assertEquals("Be brief.", executor.lastPrompt!!.systemText())
    }

    @Test
    fun blankSystemPromptSendsNoSystemMessage() = runTest {
        val executor = FakeExecutor("Answer.")
        GoogleSearchTool(executor, model, systemPrompt = " ").execute(GoogleSearchTool.Args("q"))

        assertNull(executor.lastPrompt!!.systemText())
    }

    @Test
    fun emptyAnswerReportsFinishReason() = runTest {
        val executor = FakeExecutor("", finishReason = "SAFETY")

        assertEquals(
            "The model returned no answer (finish reason: SAFETY).",
            GoogleSearchTool(executor, model).execute(GoogleSearchTool.Args("q")),
        )
    }

    @Test
    fun googleSearchWithoutGroundingReturnsPlainText() = runTest {
        val executor = FakeExecutor("No idea.")
        var seen: JsonObject? = null
        val tool = GoogleSearchTool(executor, model, name = "web_search", onGroundingMetadata = { seen = it })

        assertEquals("No idea.", tool.execute(GoogleSearchTool.Args("q")))
        assertEquals("web_search", tool.name)
        assertNull(seen)
    }

    @Test
    fun urlContextRunsToolFreeRequestWithUrlAndTask() = runTest {
        val executor = FakeExecutor("A page about cats.", urlContextMetadata("SUCCESS"))
        val tool = UrlContextTool(executor, model)

        val result = tool.execute(UrlContextTool.Args("https://example.com", "What is it about?"))

        assertEquals("A page about cats.", result)
        assertEquals(emptyList(), executor.lastTools)
        val params = assertIs<FirebaseLLMParams>(executor.lastPrompt!!.params)
        assertEquals(listOf(Tool.UrlContext), params.builtInTools)
        assertEquals(
            "URL: https://example.com\n\nWhat is it about?",
            executor.lastPrompt!!.userText(),
        )
        assertEquals("fetch_url", executor.lastPrompt!!.id)
        assertEquals(UrlContextTool.DEFAULT_SYSTEM_PROMPT, executor.lastPrompt!!.systemText())
    }

    @Test
    fun urlContextWithoutMetadataReportsNoFetchAttempted() = runTest {
        val executor = FakeExecutor("From memory.")
        val tool = UrlContextTool(executor, model, name = "read_page")

        val result = tool.execute(UrlContextTool.Args("example.com", "  "))

        assertEquals(
            "Could not retrieve example.com: no fetch was attempted (use an absolute http(s) URL). From memory.",
            result,
        )
        assertTrue(executor.lastPrompt!!.userText().endsWith("Summarize the page concisely."))
        assertEquals("read_page", executor.lastPrompt!!.id)
    }

    @Test
    fun urlContextWithMixedStatusesKeepsAnswerAndNotesFailures() = runTest {
        val executor = FakeExecutor("Compared.", urlContextMetadata("SUCCESS", "PAYWALL", "PAYWALL"))

        assertEquals(
            "Note: some referenced URLs could not be retrieved (status: PAYWALL). Compared.",
            UrlContextTool(executor, model).execute(UrlContextTool.Args("https://example.com", "Compare")),
        )
    }

    @Test
    fun urlContextDefaultsToSummaryAndReportsRetrievalFailure() = runTest {
        val executor = FakeExecutor("I could not access the page.", urlContextMetadata("PAYWALL", "ERROR"))
        val tool = UrlContextTool(executor, model)

        val result = tool.execute(UrlContextTool.Args("https://example.com"))

        assertTrue(executor.lastPrompt!!.userText().endsWith("Summarize the page concisely."))
        assertEquals("Could not retrieve https://example.com (status: PAYWALL, ERROR). I could not access the page.", result)
    }

    @Test
    fun toolDescriptorsExposeArguments() {
        val executor = FakeExecutor("")
        val search = GoogleSearchTool(executor, model).descriptor
        val fetch = UrlContextTool(executor, model).descriptor

        assertEquals("google_search", search.name)
        assertEquals(listOf("query"), search.requiredParameters.map { it.name })
        assertEquals("fetch_url", fetch.name)
        assertEquals(listOf("url"), fetch.requiredParameters.map { it.name })
        assertEquals(listOf("question"), fetch.optionalParameters.map { it.name })
    }
}
