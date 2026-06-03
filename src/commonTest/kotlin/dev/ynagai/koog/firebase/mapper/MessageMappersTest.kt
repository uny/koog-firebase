package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import dev.ynagai.firebase.ai.FileDataPart
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.FunctionResponsePart
import dev.ynagai.firebase.ai.InlineDataPart
import dev.ynagai.firebase.ai.TextPart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageMappersTest {

    @Test
    fun userMessageMapsToUserRole() {
        val messages = prompt("test") { user("hello") }.messages
        val contents = messages.toFirebase()

        assertEquals(1, contents.size)
        assertEquals("user", contents[0].role)
        assertEquals(listOf("hello"), contents[0].parts.filterIsInstance<TextPart>().map { it.text })
    }

    @Test
    fun assistantMessageMapsToModelRole() {
        val messages = prompt("test") {
            user("hello")
            assistant("hi there")
        }.messages
        val contents = messages.toFirebase()

        assertEquals(2, contents.size)
        assertEquals("model", contents[1].role)
        assertEquals(listOf("hi there"), contents[1].parts.filterIsInstance<TextPart>().map { it.text })
    }

    @Test
    fun systemMessagesAreNotIncludedInContents() {
        val messages = prompt("test") {
            system("you are helpful")
            user("hello")
        }.messages
        val contents = messages.toFirebase()

        assertEquals(1, contents.size)
        assertEquals("user", contents[0].role)
    }

    @Test
    fun extractSystemInstructionCombinesSystemMessages() {
        val messages = prompt("test") {
            system("line one")
            system("line two")
            user("hello")
        }.messages

        val instruction = messages.extractSystemInstruction()

        assertTrue(instruction != null)
        assertEquals(
            listOf("line one\nline two"),
            instruction.parts.filterIsInstance<TextPart>().map { it.text },
        )
    }

    @Test
    fun extractSystemInstructionReturnsNullWithoutSystemMessages() {
        val messages = prompt("test") { user("hello") }.messages

        assertNull(messages.extractSystemInstruction())
    }

    @Test
    fun assistantToolCallMapsToFunctionCallPart() {
        val message = Message.Assistant(
            parts = listOf(
                MessagePart.Tool.Call(
                    id = "1",
                    tool = "get_weather",
                    args = buildJsonObject { put("city", "Tokyo") },
                ),
            ),
            metaInfo = ResponseMetaInfo.Empty,
        )

        val content = listOf<Message>(message).toFirebase().single()

        assertEquals("model", content.role)
        val call = content.parts.filterIsInstance<FunctionCallPart>().single()
        assertEquals("get_weather", call.name)
        assertEquals("Tokyo", call.args["city"])
        assertEquals("1", call.id)
    }

    @Test
    fun userToolResultWithJsonObjectMapsToFunctionResponsePart() {
        val message = Message.User(
            parts = listOf(
                MessagePart.Tool.Result(
                    id = "1",
                    tool = "get_weather",
                    output = """{"temperature":20}""",
                ),
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val content = listOf<Message>(message).toFirebase().single()

        assertEquals("user", content.role)
        val response = content.parts.filterIsInstance<FunctionResponsePart>().single()
        assertEquals("get_weather", response.name)
        assertEquals(20L, response.response["temperature"])
        assertEquals("1", response.id)
    }

    @Test
    fun binaryImageAttachmentMapsToInlineDataPart() {
        val message = Message.User(
            parts = listOf(
                MessagePart.Text("look at this"),
                MessagePart.Attachment(
                    source = AttachmentSource.Image(
                        content = AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3)),
                        format = "png",
                    ),
                ),
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val content = listOf<Message>(message).toFirebase().single()

        assertEquals("user", content.role)
        assertEquals(listOf("look at this"), content.parts.filterIsInstance<TextPart>().map { it.text })
        val inline = content.parts.filterIsInstance<InlineDataPart>().single()
        assertEquals("image/png", inline.mimeType)
        assertContentEquals(byteArrayOf(1, 2, 3), inline.data)
    }

    @Test
    fun urlFileAttachmentMapsToFileDataPart() {
        val message = Message.User(
            parts = listOf(
                MessagePart.Attachment(
                    source = AttachmentSource.File(
                        content = AttachmentContent.URL("gs://bucket/doc.pdf"),
                        format = "pdf",
                        mimeType = "application/pdf",
                    ),
                ),
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val content = listOf<Message>(message).toFirebase().single()

        val fileData = content.parts.filterIsInstance<FileDataPart>().single()
        assertEquals("application/pdf", fileData.mimeType)
        assertEquals("gs://bucket/doc.pdf", fileData.uri)
    }

    @Test
    fun userToolResultWithPlainStringIsWrapped() {
        val message = Message.User(
            parts = listOf(
                MessagePart.Tool.Result(id = "1", tool = "echo", output = "done"),
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val content = listOf<Message>(message).toFirebase().single()

        val response = content.parts.filterIsInstance<FunctionResponsePart>().single()
        assertEquals(mapOf("result" to "done"), response.response)
    }

    @Test
    fun userToolResultWithJsonArrayPreservesStructure() {
        val message = Message.User(
            parts = listOf(
                MessagePart.Tool.Result(id = "1", tool = "list", output = "[1,2,3]"),
            ),
            metaInfo = RequestMetaInfo.Empty,
        )

        val content = listOf<Message>(message).toFirebase().single()

        val response = content.parts.filterIsInstance<FunctionResponsePart>().single()
        assertEquals(mapOf("result" to listOf(1L, 2L, 3L)), response.response)
    }

    @Test
    fun assistantWithOnlyUnsupportedPartsIsSkipped() {
        val message = Message.Assistant(
            parts = listOf(MessagePart.Reasoning("thinking")),
            metaInfo = ResponseMetaInfo.Empty,
        )

        assertTrue(listOf<Message>(message).toFirebase().isEmpty())
    }
}
