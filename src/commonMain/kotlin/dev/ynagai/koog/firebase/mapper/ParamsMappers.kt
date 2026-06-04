package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.params.LLMParams
import dev.ynagai.firebase.ai.GenerationConfig
import dev.ynagai.koog.firebase.FirebaseLLMParams

/**
 * Maps Koog [LLMParams] to a Firebase [GenerationConfig]. Returns `null` when none of the supported
 * parameters are set so the caller can omit the config entirely and keep the model defaults.
 */
internal fun LLMParams.toGenerationConfig(): GenerationConfig? {
    val responseSchema = (schema as? LLMParams.Schema.JSON)?.schema?.toFirebaseSchema()
    // Gemini requires a JSON MIME type whenever a response schema is supplied.
    val responseMimeType = if (responseSchema != null) "application/json" else null
    val thinkingConfig = (this as? FirebaseLLMParams)?.thinkingConfig

    if (temperature == null &&
        maxTokens == null &&
        numberOfChoices == null &&
        responseSchema == null &&
        thinkingConfig == null
    ) {
        return null
    }

    return GenerationConfig(
        temperature = temperature?.toFloat(),
        maxOutputTokens = maxTokens,
        candidateCount = numberOfChoices,
        responseMimeType = responseMimeType,
        responseSchema = responseSchema,
        thinkingConfig = thinkingConfig,
    )
}
