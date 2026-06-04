package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.GenerateContentResponse
import dev.ynagai.firebase.ai.Part
import dev.ynagai.firebase.ai.TextPart

/** Converts a Firebase [GenerateContentResponse] into Koog [Message.Assistant]s, mapping text and tool-call parts. */
internal fun GenerateContentResponse.toKoog(clock: KoogClock): List<Message.Assistant> {
    val inputTokensCount = usageMetadata?.promptTokenCount
    val outputTokensCount = usageMetadata?.candidatesTokenCount
    val totalTokensCount = usageMetadata?.totalTokenCount
    val metaInfo = ResponseMetaInfo.create(
        clock,
        totalTokensCount = totalTokensCount,
        inputTokensCount = inputTokensCount,
        outputTokensCount = outputTokensCount,
    )
    return candidates.map { candidate ->
        val responseParts: List<MessagePart.ResponsePart> = candidate.content.parts.mapNotNull { part ->
            when (part) {
                is TextPart -> if (part.isThought) {
                    MessagePart.Reasoning(part.text)
                } else {
                    MessagePart.Text(part.text)
                }
                is FunctionCallPart -> MessagePart.Tool.Call(
                    id = part.id,
                    tool = part.name,
                    args = part.args.toJsonObject(),
                )
                else -> null
            }
        }
        Message.Assistant(
            parts = responseParts.ifEmpty { listOf(MessagePart.Text("")) },
            metaInfo = metaInfo,
            finishReason = candidate.finishReason?.name,
        )
    }
}

/**
 * Maps a single streamed Firebase [Part] to a Koog [StreamFrame], or `null` if the part has no
 * streaming equivalent. Mirrors [toKoog]'s part handling: a thought [TextPart] becomes a
 * [StreamFrame.ReasoningDelta], other text a [StreamFrame.TextDelta], and a function call a
 * [StreamFrame.ToolCallComplete].
 */
internal fun Part.toStreamFrame(): StreamFrame? = when (this) {
    is TextPart -> if (isThought) {
        StreamFrame.ReasoningDelta(text = text)
    } else {
        StreamFrame.TextDelta(text)
    }
    is FunctionCallPart -> StreamFrame.ToolCallComplete(
        id = id,
        name = name,
        content = args.toJsonObject().toString(),
    )
    else -> null
}
