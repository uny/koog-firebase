package dev.ynagai.koog.firebase

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import dev.ynagai.firebase.ai.FirebaseAI
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.GenerationConfig
import dev.ynagai.firebase.ai.GenerativeModel
import dev.ynagai.firebase.ai.TextPart
import dev.ynagai.koog.firebase.mapper.extractSystemInstruction
import dev.ynagai.koog.firebase.mapper.toFirebase
import dev.ynagai.koog.firebase.mapper.toFirebaseTools
import dev.ynagai.koog.firebase.mapper.toGenerationConfig
import dev.ynagai.koog.firebase.mapper.toJsonObject
import dev.ynagai.koog.firebase.mapper.toKoog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * LLMClient implementation for Firebase AI SDK.
 *
 * @param firebaseAI The FirebaseAI instance to use for creating generative models
 */
class FirebaseLLMClient(
    private val firebaseAI: FirebaseAI,
    private val clock: KoogClock = KoogClock.System,
) : LLMClient() {
    override fun llmProvider(): LLMProvider = FirebaseLLMProvider

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant = generateContent(prompt, model, tools).first()

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Assistant> = generateContent(prompt, model, tools)

    /**
     * Runs a single (non-streaming) generation and maps every returned candidate to a Koog
     * [Message.Assistant]. The result is guaranteed non-empty: when Firebase returns no content
     * an [LLMClientException] is thrown instead. Shared by [execute] (first candidate) and
     * [executeMultipleChoices] (all candidates).
     */
    private suspend fun generateContent(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Assistant> {
        return try {
            val generativeModel = createGenerativeModel(model, prompt, tools)
            val contents = prompt.messages.toFirebase()
            val response = generativeModel.generateContent(*contents.toTypedArray())
            response.toKoog(clock).ifEmpty {
                val blockReason = response.promptFeedback?.blockReason?.name
                throw LLMClientException(
                    clientName = clientName,
                    message = "Firebase AI returned no content" +
                        (blockReason?.let { " (blocked: $it)" } ?: ""),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LLMClientException) {
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
        try {
            val generativeModel = createGenerativeModel(model, prompt, tools)
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
                                is TextPart -> emit(StreamFrame.TextDelta(part.text))
                                is FunctionCallPart -> emit(
                                    StreamFrame.ToolCallComplete(
                                        id = part.id,
                                        name = part.name,
                                        content = part.args.toJsonObject().toString(),
                                    )
                                )
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

    override suspend fun models(): List<LLModel> = FirebaseModels.models

    override fun close() {
        // No resources to close for Firebase AI client
    }

    private fun createGenerativeModel(
        model: LLModel,
        prompt: Prompt,
        tools: List<ToolDescriptor>,
    ): GenerativeModel {
        val systemInstruction = prompt.messages.extractSystemInstruction()
        val generationConfig: GenerationConfig? = prompt.params.toGenerationConfig()
        return firebaseAI.generativeModel(
            modelName = model.id,
            generationConfig = generationConfig,
            systemInstruction = systemInstruction,
            tools = tools.toFirebaseTools().ifEmpty { null },
        )
    }
}
