package dev.ynagai.koog.firebase

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

/**
 * Pre-configured LLModel instances for Firebase AI models.
 */
object FirebaseModels : LLModelDefinitions {
    /**
     * Gemini 2.0 Flash - Fast and efficient model.
     */
    val Gemini2_5Flash = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-2.5-flash",
        capabilities = listOf(LLMCapability.Speculation, LLMCapability.Temperature),
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )
}
