package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import dev.ynagai.firebase.ai.GenerateContentResponse
import dev.ynagai.firebase.ai.TextPart
import kotlin.time.Clock

internal fun GenerateContentResponse.toKoog(clock: Clock): List<List<Message.Response>> {
    val inputTokensCount = usageMetadata?.promptTokenCount
    val outputTokensCount = usageMetadata?.candidatesTokenCount
    val totalTokensCount = usageMetadata?.totalTokenCount
    val metaInfo = ResponseMetaInfo.create(
        clock,
        totalTokensCount = totalTokensCount,
        inputTokensCount = inputTokensCount,
        outputTokensCount = outputTokensCount
    )
    return candidates.map { candidate ->
        val responses = mutableListOf<Message.Response>()
        candidate.content.parts.forEach { part ->
            when (part) {
                is TextPart -> {
                    responses.add(
                        Message.Assistant(
                            content = part.text,
                            metaInfo = metaInfo
                        )
                    )
                }
                else -> Unit // InlineDataPart等は現時点でスキップ
            }
        }
        if (responses.isEmpty()) {
            responses.add(Message.Assistant(content = "", metaInfo = metaInfo))
        }
        responses
    }
}
