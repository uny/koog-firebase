package dev.ynagai.koog.firebase

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import dev.ynagai.firebase.ai.FirebaseAI
import dev.ynagai.firebase.ai.GenerativeModel
import dev.ynagai.koog.firebase.mapper.extractSystemInstruction
import dev.ynagai.koog.firebase.mapper.toFirebase
import dev.ynagai.koog.firebase.mapper.toKoog
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

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
        val generativeModel = createGenerativeModel(model, prompt.messages)
        val contents = prompt.messages.toFirebase()
        val response = generativeModel.generateContent(*contents.toTypedArray())
        return response.toKoog(clock).first()
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        if (tools.isNotEmpty()) {
            throw UnsupportedOperationException(
                "Firebase AI SDK does not support function calling / tools yet"
            )
        }
        TODO()
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        throw UnsupportedOperationException("Moderation is not supported by Firebase AI.")
    }

    override suspend fun models(): List<String> {
        return listOf(
            FirebaseModels.Gemini2_5Flash.id,
        )
    }

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
