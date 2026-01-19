package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import dev.ynagai.firebase.ai.GenerateContentResponse
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

internal fun GenerateContentResponse.toKoog(clock: Clock): List<List<Message.Response>> {
    // Extract token count from the response
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
        }
        responses
    }
}
