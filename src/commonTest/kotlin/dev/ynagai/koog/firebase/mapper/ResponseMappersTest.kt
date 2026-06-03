package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.MessagePart
import ai.koog.utils.time.KoogClock
import dev.ynagai.firebase.ai.Candidate
import dev.ynagai.firebase.ai.Content
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.GenerateContentResponse
import dev.ynagai.firebase.ai.TextPart
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
