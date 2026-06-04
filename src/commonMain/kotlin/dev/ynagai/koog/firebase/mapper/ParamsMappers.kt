package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.params.LLMParams
import dev.ynagai.firebase.ai.GenerationConfig

/**
 * Maps Koog [LLMParams] to a Firebase [GenerationConfig]. Returns `null` when none of the supported
 * parameters are set so the caller can omit the config entirely and keep the model defaults.
 */
internal fun LLMParams.toGenerationConfig(): GenerationConfig? {
    if (temperature == null && maxTokens == null && numberOfChoices == null) {
        return null
    }

    return GenerationConfig(
        temperature = temperature?.toFloat(),
        maxOutputTokens = maxTokens,
        candidateCount = numberOfChoices,
    )
}
