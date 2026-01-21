package dev.ynagai.koog.firebase

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import dev.ynagai.firebase.ai.FirebaseAI
import dev.ynagai.firebase.ai.GenerativeModel
import dev.ynagai.firebase.ai.TextPart
import dev.ynagai.koog.firebase.mapper.extractSystemInstruction
import dev.ynagai.koog.firebase.mapper.toFirebase
import dev.ynagai.koog.firebase.mapper.toKoog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

/**
 * LLMClient implementation for Firebase AI SDK.
 *
 * This client bridges Koog's LLMClient interface with Firebase AI's GenerativeModel API.
 *
 * @param firebaseAI The FirebaseAI instance to use for creating generative models
 */
class FirebaseLLMClient(
    private val firebaseAI: FirebaseAI,
    private val clock: Clock = Clock.System,
) : LLMClient {
    override fun llmProvider(): LLMProvider = FirebaseLLMProvider

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        if (tools.isNotEmpty()) {
            throw UnsupportedOperationException(
                "Firebase AI SDK does not support function calling / tools yet"
            )
        }
        return try {
            val generativeModel = createGenerativeModel(model, prompt.messages)
            val contents = prompt.messages.toFirebase()
            val response = generativeModel.generateContent(*contents.toTypedArray())
            response.toKoog(clock).firstOrNull() ?: emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = "Firebase AI request failed: ${e.message}",
                cause = e
            )
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        if (tools.isNotEmpty()) {
            throw UnsupportedOperationException(
                "Firebase AI SDK does not support function calling / tools yet"
            )
        }

        try {
            val generativeModel = createGenerativeModel(model, prompt.messages)
            val contents = prompt.messages.toFirebase()

            var lastMetaInfo: ResponseMetaInfo? = null
            var lastFinishReason: String? = null

            generativeModel.generateContentStream(*contents.toTypedArray())
                .collect { response ->
                    response.usageMetadata?.let {
                        lastMetaInfo = ResponseMetaInfo.create(
                            clock = clock,
                            totalTokensCount = it.totalTokenCount,
                            inputTokensCount = it.promptTokenCount,
                            outputTokensCount = it.candidatesTokenCount,
                        )
                    }
                    response.candidates.firstOrNull()?.let { candidate ->
                        candidate.finishReason?.let { lastFinishReason = it.name }
                        candidate.content.parts.forEach { part ->
                            when (part) {
                                is TextPart -> emit(StreamFrame.Append(part.text))
                                else -> Unit
                            }
                        }
                    }
                }

            emit(StreamFrame.End(lastFinishReason, lastMetaInfo ?: ResponseMetaInfo.create(clock)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LLMClientException(
                clientName = clientName,
                message = "Firebase AI streaming request failed: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        throw UnsupportedOperationException("Moderation is not supported by Firebase AI.")
    }

    override suspend fun models(): List<String> = listOf(
        FirebaseModels.Gemini2_5Flash.id,
        FirebaseModels.Gemini2_5Pro.id,
        FirebaseModels.Gemini2_0Flash.id,
        FirebaseModels.Gemini2_0FlashLite.id,
    )

    override fun close() {
        // No resources to close for Firebase AI client
    }

    /**
     * Creates a GenerativeModel with the appropriate configuration.
     *
     * @param model The Koog LLModel to use
     * @param messages The messages from the prompt (used to extract system instruction)
     * @return Configured GenerativeModel instance
     */
    private fun createGenerativeModel(model: LLModel, messages: List<Message>): GenerativeModel {
        val systemInstruction = messages.extractSystemInstruction()
        return firebaseAI.generativeModel(
            modelName = model.id,
            systemInstruction = systemInstruction
        )
    }
}
