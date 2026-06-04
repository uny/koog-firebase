package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import dev.ynagai.firebase.ai.Candidate
import dev.ynagai.firebase.ai.Content
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.GenerateContentResponse
import dev.ynagai.firebase.ai.InlineDataPart
import dev.ynagai.firebase.ai.TextPart
import kotlinx.serialization.json.Json
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
}
