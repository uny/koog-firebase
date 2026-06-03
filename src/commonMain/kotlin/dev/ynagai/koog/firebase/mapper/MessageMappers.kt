package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import dev.ynagai.firebase.ai.Content
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.FunctionResponsePart
import dev.ynagai.firebase.ai.Part
import dev.ynagai.firebase.ai.TextPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal fun Message.toFirebase(): Content? = when (this) {
    is Message.User -> Content(
        role = "user",
        parts = parts.mapNotNull { it.toFirebasePart() },
    )

    is Message.Assistant -> Content(
        role = "model",
        parts = parts.mapNotNull { it.toFirebasePart() },
    )

    else -> null
}

private fun MessagePart.toFirebasePart(): Part? = when (this) {
    is MessagePart.Text -> TextPart(text)
    is MessagePart.Tool.Call -> FunctionCallPart(name = tool, args = argsJson.toAnyMap())
    is MessagePart.Tool.Result -> FunctionResponsePart(name = tool, response = output.toResponseMap())
    else -> null
}

/**
 * Firebase expects a structured object for a function response. If the tool output is itself a
 * JSON object we forward it as-is, otherwise we wrap the raw string under a "result" key.
 */
private fun String.toResponseMap(): Map<String, Any?> {
    val parsed = runCatching { Json.parseToJsonElement(this) }.getOrNull()
    return if (parsed is JsonObject) parsed.toAnyMap() else mapOf("result" to this)
}

internal fun List<Message>.toFirebase(): List<Content> = mapNotNull(Message::toFirebase)

internal fun List<Message>.extractSystemInstruction(): Content? {
    val systemMessages = filterIsInstance<Message.System>()
    if (systemMessages.isEmpty()) return null
    val combinedSystemPrompt = systemMessages.joinToString("\n") { it.textContent() }
    return Content(
        parts = listOf(TextPart(combinedSystemPrompt)),
    )
}
