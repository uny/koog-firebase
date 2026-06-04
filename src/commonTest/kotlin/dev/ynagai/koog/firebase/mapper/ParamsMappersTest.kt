package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.params.LLMParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParamsMappersTest {

    @Test
    fun returnsNullWhenNoSupportedParamsAreSet() {
        assertNull(LLMParams().toGenerationConfig())
    }

    @Test
    fun mapsTemperatureMaxTokensAndChoiceCount() {
        val config = LLMParams(
            temperature = 0.5,
            maxTokens = 128,
            numberOfChoices = 2,
        ).toGenerationConfig()

        assertNotNull(config)
        assertEquals(0.5f, config.temperature)
        assertEquals(128, config.maxOutputTokens)
        assertEquals(2, config.candidateCount)
    }

    @Test
    fun mapsTemperatureOnly() {
        val config = LLMParams(temperature = 0.2).toGenerationConfig()

        assertNotNull(config)
        assertEquals(0.2f, config.temperature)
        assertNull(config.maxOutputTokens)
        assertNull(config.candidateCount)
    }
}
