package dev.ynagai.koog.firebase

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

/**
 * Pre-configured LLModel instances for Firebase AI models.
 */
object FirebaseModels : LLModelDefinitions {
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Completion,
    )

    /**
     * Gemini 2.5 Flash - Fast and efficient model with speculation support.
     */
    val Gemini2_5Flash = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-2.5-flash",
        capabilities = standardCapabilities + LLMCapability.Speculation,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 2.5 Pro - High-capability model with speculation support.
     */
    val Gemini2_5Pro = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-2.5-pro",
        capabilities = standardCapabilities + LLMCapability.Speculation,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 2.0 Flash - Fast model with image vision support.
     */
    val Gemini2_0Flash = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-2.0-flash",
        capabilities = standardCapabilities + LLMCapability.Vision.Image,
        contextLength = 1_048_576,
        maxOutputTokens = 8_192,
    )

    /**
     * Gemini 2.0 Flash Lite - Lightweight and efficient model.
     */
    val Gemini2_0FlashLite = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-2.0-flash-lite",
        capabilities = standardCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 8_192,
    )

    private val supportedModels: List<LLModel> = listOf(
        Gemini2_5Flash,
        Gemini2_5Pro,
        Gemini2_0Flash,
        Gemini2_0FlashLite,
    )

    private val customModels: MutableList<LLModel> = mutableListOf()

    override val models: List<LLModel>
        get() = supportedModels + customModels

    override fun addCustomModel(model: LLModel) {
        require(model.provider == FirebaseLLMProvider) { "Model provider must be Firebase" }
        customModels.add(model)
    }
}
