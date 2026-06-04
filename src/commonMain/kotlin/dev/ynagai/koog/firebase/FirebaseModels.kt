package dev.ynagai.koog.firebase

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

/**
 * Pre-configured LLModel instances for Firebase AI models.
 *
 * Model list is aligned with https://firebase.google.com/docs/ai-logic/models.
 */
object FirebaseModels : LLModelDefinitions {
    private val standardCapabilities: List<LLMCapability> = listOf(
        LLMCapability.Temperature,
        LLMCapability.Completion,
        LLMCapability.MultipleChoices,
        LLMCapability.Tools,
        // Gemini models accept image, video, audio, and document (e.g. PDF) input.
        LLMCapability.Vision.Image,
        LLMCapability.Vision.Video,
        LLMCapability.Audio,
        LLMCapability.Document,
    )

    /**
     * Gemini 3.5 Flash - Frontier-class Flash model.
     */
    val Gemini3_5Flash = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-3.5-flash",
        capabilities = standardCapabilities + LLMCapability.Speculation,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 3.1 Pro - Advanced reasoning model (preview).
     */
    val Gemini3_1Pro = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-3.1-pro-preview",
        capabilities = standardCapabilities + LLMCapability.Speculation,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 3.1 Flash-Lite - Ultra-fast, budget-friendly model.
     */
    val Gemini3_1FlashLite = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-3.1-flash-lite",
        capabilities = standardCapabilities,
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
     * Gemini 2.5 Flash-Lite - Budget-friendly Flash variant.
     */
    val Gemini2_5FlashLite = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-2.5-flash-lite",
        capabilities = standardCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    private val supportedModels: List<LLModel> = listOf(
        Gemini3_5Flash,
        Gemini3_1Pro,
        Gemini3_1FlashLite,
        Gemini2_5Pro,
        Gemini2_5Flash,
        Gemini2_5FlashLite,
    )

    private val customModels: MutableList<LLModel> = mutableListOf()

    override val models: List<LLModel>
        get() = supportedModels + customModels

    override fun addCustomModel(model: LLModel) {
        require(model.provider == FirebaseLLMProvider) { "Model provider must be Firebase" }
        customModels.add(model)
    }
}
