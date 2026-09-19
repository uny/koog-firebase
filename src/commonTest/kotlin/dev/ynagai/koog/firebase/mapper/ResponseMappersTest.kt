package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import dev.ynagai.firebase.ai.Candidate
import dev.ynagai.firebase.ai.CodeExecutionOutcome
import dev.ynagai.firebase.ai.CodeExecutionResultPart
import dev.ynagai.firebase.ai.Content
import dev.ynagai.firebase.ai.ExecutableCodePart
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.GenerateContentResponse
import dev.ynagai.firebase.ai.GroundingChunk
import dev.ynagai.firebase.ai.GroundingMetadata
import dev.ynagai.firebase.ai.GroundingSupport
import dev.ynagai.firebase.ai.InlineDataPart
import dev.ynagai.firebase.ai.SearchEntryPoint
import dev.ynagai.firebase.ai.Segment
import dev.ynagai.firebase.ai.TextPart
import dev.ynagai.firebase.ai.UrlContextMetadata
import dev.ynagai.firebase.ai.UrlMetadata
import dev.ynagai.firebase.ai.UrlRetrievalStatus
import dev.ynagai.firebase.ai.WebGroundingChunk
import dev.ynagai.koog.firebase.FirebaseMetadataKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ResponseMappersTest {

    @Test
    fun mapsTextCandidateToAssistant() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(role = "model", parts = listOf(TextPart("hello"))),
                ),
            ),
        )

        val assistants = response.toKoog(KoogClock.System)

        assertEquals(1, assistants.size)
        assertEquals(
            listOf("hello"),
            assistants[0].parts.filterIsInstance<MessagePart.Text>().map { it.text },
        )
    }

    @Test
    fun emptyPartsFallBackToEmptyText() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(content = Content(role = "model", parts = emptyList())),
            ),
        )

        val assistants = response.toKoog(KoogClock.System)

        assertEquals(1, assistants.size)
        assertEquals(listOf(""), assistants[0].parts.filterIsInstance<MessagePart.Text>().map { it.text })
    }

    @Test
    fun mapsFunctionCallToToolCall() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        role = "model",
                        parts = listOf(
                            FunctionCallPart(name = "get_weather", args = mapOf("city" to "Tokyo"), id = "call_1"),
                        ),
                    ),
                ),
            ),
        )

        val assistants = response.toKoog(KoogClock.System)

        val call = assistants.single().parts.filterIsInstance<MessagePart.Tool.Call>().single()
        assertEquals("get_weather", call.tool)
        assertEquals("Tokyo", call.argsJson.getValue("city").jsonPrimitive.content)
        assertEquals("call_1", call.id)
    }

    @Test
    fun mapsThoughtTextToReasoningAndKeepsPlainText() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        role = "model",
                        parts = listOf(
                            TextPart(text = "let me think", isThought = true),
                            TextPart(text = "the answer is 42"),
                        ),
                    ),
                ),
            ),
        )

        val assistant = response.toKoog(KoogClock.System).single()

        val reasoning = assistant.parts.filterIsInstance<MessagePart.Reasoning>().single()
        assertEquals(listOf("let me think"), reasoning.content)
        assertEquals(
            listOf("the answer is 42"),
            assistant.parts.filterIsInstance<MessagePart.Text>().map { it.text },
        )
    }

    @Test
    fun streamsThoughtTextAsReasoningDelta() {
        val frame = TextPart(text = "let me think", isThought = true).toStreamFrame()

        val reasoning = assertIs<StreamFrame.ReasoningDelta>(frame)
        assertEquals("let me think", reasoning.text)
    }

    @Test
    fun streamsPlainTextAsTextDelta() {
        val frame = TextPart(text = "the answer is 42").toStreamFrame()

        val text = assertIs<StreamFrame.TextDelta>(frame)
        assertEquals("the answer is 42", text.text)
    }

    @Test
    fun streamsFunctionCallAsToolCallComplete() {
        val frame = FunctionCallPart(name = "get_weather", args = mapOf("city" to "Tokyo"), id = "call_1")
            .toStreamFrame()

        val toolCall = assertIs<StreamFrame.ToolCallComplete>(frame)
        assertEquals("get_weather", toolCall.name)
        assertEquals("call_1", toolCall.id)
        assertEquals("Tokyo", Json.parseToJsonElement(toolCall.content).jsonObject.getValue("city").jsonPrimitive.content)
    }

    @Test
    fun streamsUnsupportedPartAsNull() {
        val frame = InlineDataPart(mimeType = "image/png", data = byteArrayOf(1, 2, 3)).toStreamFrame()

        assertNull(frame)
    }

    @Test
    fun metadataIsEmptyWithoutBuiltInToolResults() {
        val response = GenerateContentResponse(
            candidates = listOf(Candidate(content = Content(role = "model", parts = listOf(TextPart("hi"))))),
        )

        assertNull(response.toKoog(KoogClock.System).single().metaInfo.metadata)
    }

    @Test
    fun exposesGroundingMetadataPerCandidate() {
        val grounding = GroundingMetadata(
            webSearchQueries = listOf("weather tokyo"),
            searchEntryPoint = SearchEntryPoint(renderedContent = "<div>suggestions</div>"),
            groundingChunks = listOf(
                GroundingChunk(web = WebGroundingChunk(uri = "https://example.com", title = "Example", domain = "example.com")),
            ),
            groundingSupports = listOf(
                GroundingSupport(segment = Segment(0, 0, 5, "Sunny"), groundingChunkIndices = listOf(0)),
            ),
        )
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(content = Content(role = "model", parts = listOf(TextPart("Sunny"))), groundingMetadata = grounding),
                Candidate(content = Content(role = "model", parts = listOf(TextPart("Rainy")))),
            ),
        )

        val assistants = response.toKoog(KoogClock.System)

        val json = assistants[0].metaInfo.metadata!!.getValue(FirebaseMetadataKeys.GROUNDING_METADATA).jsonObject
        assertEquals("weather tokyo", json.getValue("webSearchQueries").jsonArray.single().jsonPrimitive.content)
        assertEquals(
            "<div>suggestions</div>",
            json.getValue("searchEntryPoint").jsonObject.getValue("renderedContent").jsonPrimitive.content,
        )
        val web = json.getValue("groundingChunks").jsonArray.single().jsonObject.getValue("web").jsonObject
        assertEquals("https://example.com", web.getValue("uri").jsonPrimitive.content)
        assertEquals("example.com", web.getValue("domain").jsonPrimitive.content)
        val support = json.getValue("groundingSupports").jsonArray.single().jsonObject
        assertEquals("Sunny", support.getValue("segment").jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(0, support.getValue("groundingChunkIndices").jsonArray.single().jsonPrimitive.content.toInt())
        assertNull(assistants[0].metaInfo.metadata!![FirebaseMetadataKeys.URL_CONTEXT_METADATA])
        assertNull(assistants[1].metaInfo.metadata)
    }

    @Test
    fun exposesUrlContextMetadata() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(role = "model", parts = listOf(TextPart("summary"))),
                    urlContextMetadata = UrlContextMetadata(
                        urlMetadata = listOf(UrlMetadata("https://example.com/doc", UrlRetrievalStatus.SUCCESS)),
                    ),
                ),
            ),
        )

        val metadata = response.toKoog(KoogClock.System).single().metaInfo.metadata!!

        val entry = metadata.getValue(FirebaseMetadataKeys.URL_CONTEXT_METADATA).jsonObject
            .getValue("urlMetadata").jsonArray.single().jsonObject
        assertEquals("https://example.com/doc", entry.getValue("retrievedUrl").jsonPrimitive.content)
        assertEquals("SUCCESS", entry.getValue("urlRetrievalStatus").jsonPrimitive.content)
    }

    @Test
    fun toolMetadataJsonIsNullWhenBothAbsent() {
        assertNull(toolMetadataJson(null, null))
    }

    @Test
    fun mapsCodeExecutionPartsToText() {
        val response = GenerateContentResponse(
            candidates = listOf(
                Candidate(
                    content = Content(
                        role = "model",
                        parts = listOf(
                            ExecutableCodePart(language = "PYTHON", code = "print(1)"),
                            CodeExecutionResultPart(outcome = CodeExecutionOutcome.OK, output = "1\n"),
                            ExecutableCodePart(language = "PYTHON", code = "x = 2", isThought = true),
                        ),
                    ),
                ),
            ),
        )

        val parts = response.toKoog(KoogClock.System).single().parts

        assertEquals(
            listOf("```python\nprint(1)\n```", "1\n"),
            parts.filterIsInstance<MessagePart.Text>().map { it.text },
        )
        assertEquals(listOf("```python\nx = 2\n```"), parts.filterIsInstance<MessagePart.Reasoning>().flatMap { it.content })
    }

    @Test
    fun streamsCodeExecutionPartsAsTextDeltas() {
        val code = assertIs<StreamFrame.TextDelta>(ExecutableCodePart(language = "PYTHON", code = "print(1)").toStreamFrame())
        assertEquals("```python\nprint(1)\n```", code.text)

        val result = assertIs<StreamFrame.TextDelta>(
            CodeExecutionResultPart(outcome = CodeExecutionOutcome.OK, output = "1").toStreamFrame(),
        )
        assertEquals("1", result.text)
    }
}
