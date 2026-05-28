package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.dsl.prompt
import dev.ynagai.firebase.ai.TextPart
import kotlin.test.Test
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
}
