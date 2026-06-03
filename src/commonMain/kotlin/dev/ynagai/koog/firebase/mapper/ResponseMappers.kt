package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.GenerateContentResponse
import dev.ynagai.firebase.ai.TextPart

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
                is TextPart -> MessagePart.Text(part.text)
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
