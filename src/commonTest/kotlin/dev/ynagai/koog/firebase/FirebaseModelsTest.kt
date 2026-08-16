package dev.ynagai.koog.firebase

import ai.koog.prompt.llm.LLMCapability
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseModelsTest {

    @Test
    fun modelsExposesAllNewGemini3xPresets() {
        val ids = FirebaseModels.models.map { it.id }
        assertContains(ids, "gemini-3.6-flash")
        assertContains(ids, "gemini-3-flash-preview")
        assertContains(ids, "gemini-3.5-flash-lite")
    }

    @Test
    fun gemini3_6FlashOmitsDeprecatedTemperatureParameter() {
        assertFalse(FirebaseModels.Gemini3_6Flash.capabilities.orEmpty().contains(LLMCapability.Temperature))
        assertTrue(FirebaseModels.Gemini3_6Flash.capabilities.orEmpty().contains(LLMCapability.Speculation))
    }

    @Test
    fun gemini3_5FlashLiteOmitsDeprecatedTemperatureParameter() {
        assertFalse(FirebaseModels.Gemini3_5FlashLite.capabilities.orEmpty().contains(LLMCapability.Temperature))
    }
}
