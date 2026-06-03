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
    is Message.User -> parts.toFirebaseContent(role = "user")
    is Message.Assistant -> parts.toFirebaseContent(role = "model")
    else -> null
}

/**
 * Maps message parts to a Firebase [Content]. Firebase rejects a [Content] with no parts, so a
 * message whose parts are all unsupported (e.g. an attachment-only user message or a
 * reasoning-only assistant message) is skipped by returning `null` rather than emitting an
 * invalid empty content.
 */
private fun List<MessagePart>.toFirebaseContent(role: String): Content? {
    val firebaseParts = mapNotNull { it.toFirebasePart() }
    return if (firebaseParts.isEmpty()) null else Content(role = role, parts = firebaseParts)
}

private fun MessagePart.toFirebasePart(): Part? = when (this) {
    is MessagePart.Text -> TextPart(text)
    is MessagePart.Tool.Call -> FunctionCallPart(name = tool, args = argsJson.toAnyMap())
    is MessagePart.Tool.Result -> FunctionResponsePart(name = tool, response = output.toResponseMap())
    else -> null
}

/**
 * Firebase expects a structured object for a function response. A JSON object is forwarded as-is.
 * A non-object JSON value (array, number, boolean) is preserved structurally under a "result" key,
 * and a value that is not valid JSON is wrapped as the raw string under "result".
 */
private fun String.toResponseMap(): Map<String, Any?> {
    val parsed = runCatching { Json.parseToJsonElement(this) }.getOrNull()
    return when (parsed) {
        is JsonObject -> parsed.toAnyMap()
        null -> mapOf("result" to this)
        else -> mapOf("result" to parsed.toAnyValue())
    }
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
