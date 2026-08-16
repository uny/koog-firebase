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
        LLMCapability.ToolChoice,
        LLMCapability.Thinking,
        LLMCapability.Schema.JSON.Basic,
        LLMCapability.Schema.JSON.Standard,
        // Gemini models accept image, video, audio, and document (e.g. PDF) input.
        LLMCapability.Vision.Image,
        LLMCapability.Vision.Video,
        LLMCapability.Audio,
        LLMCapability.Document,
    )

    /**
     * Gemini 3.7 Flash - Latest stable Gemini 3.x Flash model.
     *
     * `temperature` is deprecated and ignored by this model per
     * https://firebase.google.com/docs/ai-logic/model-parameters, so it is excluded from
     * [standardCapabilities] here.
     */
    val Gemini3_7Flash = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-3.7-flash",
        capabilities = standardCapabilities - LLMCapability.Temperature + LLMCapability.Speculation,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 3.6 Flash - Previous stable Gemini 3.x Flash model.
     *
     * `temperature` is deprecated and ignored by this model per
     * https://firebase.google.com/docs/ai-logic/model-parameters, so it is excluded from
     * [standardCapabilities] here.
     */
    val Gemini3_6Flash = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-3.6-flash",
        capabilities = standardCapabilities - LLMCapability.Temperature + LLMCapability.Speculation,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 3 Flash (preview) - Preview version of the Gemini 3.x Flash line.
     */
    val Gemini3FlashPreview = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-3-flash-preview",
        capabilities = standardCapabilities + LLMCapability.Speculation,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 3.5 Flash - Previous stable Gemini 3.x Flash model.
     */
    val Gemini3_5Flash = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-3.5-flash",
        capabilities = standardCapabilities + LLMCapability.Speculation,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    /**
     * Gemini 3.5 Flash-Lite - High-volume, cost-sensitive workhorse model.
     *
     * `temperature` is deprecated and ignored by this model per
     * https://firebase.google.com/docs/ai-logic/model-parameters, so it is excluded from
     * [standardCapabilities] here.
     */
    val Gemini3_5FlashLite = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-3.5-flash-lite",
        capabilities = standardCapabilities - LLMCapability.Temperature,
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
    @Deprecated("Gemini 2.5 models are scheduled to retire in October 2026. Migrate to a Gemini 3.x model.")
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
    @Deprecated("Gemini 2.5 models are scheduled to retire in October 2026. Migrate to a Gemini 3.x model.")
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
    @Deprecated("Gemini 2.5 models are scheduled to retire in October 2026. Migrate to a Gemini 3.x model.")
    val Gemini2_5FlashLite = LLModel(
        provider = FirebaseLLMProvider,
        id = "gemini-2.5-flash-lite",
        capabilities = standardCapabilities,
        contextLength = 1_048_576,
        maxOutputTokens = 65_536,
    )

    @Suppress("DEPRECATION")
    private val supportedModels: List<LLModel> = listOf(
        Gemini3_7Flash,
        Gemini3_6Flash,
        Gemini3FlashPreview,
        Gemini3_5Flash,
        Gemini3_5FlashLite,
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
