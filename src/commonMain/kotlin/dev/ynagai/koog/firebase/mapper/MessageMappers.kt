package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.Message
import dev.ynagai.firebase.ai.Content
import dev.ynagai.firebase.ai.TextPart

internal fun Message.toFirebase(): Content? = when (this) {
    is Message.User -> Content(
        role = "user",
        parts = listOf(TextPart(content))
    )

    is Message.Assistant -> Content(
        role = "model",
        parts = listOf(TextPart(content))
    )

    else -> null
}

internal fun List<Message>.toFirebase(): List<Content> = mapNotNull(Message::toFirebase)

internal fun List<Message>.extractSystemInstruction(): Content? {
    val systemMessages = filterIsInstance<Message.System>()
    if (systemMessages.isEmpty()) return null
    val combinedSystemPrompt = systemMessages.joinToString("\n") { it.content }
    return Content(
        parts = listOf(TextPart(combinedSystemPrompt)),
    )
}
