package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.params.LLMParams
import dev.ynagai.firebase.ai.SchemaType
import dev.ynagai.firebase.ai.ThinkingConfig
import dev.ynagai.firebase.ai.ThinkingLevel
import dev.ynagai.koog.firebase.FirebaseLLMParams
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

    @Test
    fun mapsResponseSchemaAndForcesJsonMimeType() {
        val schema = LLMParams.Schema.JSON.Standard(
            name = "result",
            schema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("answer") { put("type", "string") }
                }
            },
        )

        val config = LLMParams(schema = schema).toGenerationConfig()

        assertNotNull(config)
        assertEquals("application/json", config.responseMimeType)
        assertEquals(SchemaType.OBJECT, config.responseSchema?.type)
        assertEquals(SchemaType.STRING, config.responseSchema?.properties?.getValue("answer")?.type)
    }

    @Test
    fun mapsThinkingConfigFromFirebaseParams() {
        val thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.HIGH, includeThoughts = true)

        val config = FirebaseLLMParams(thinkingConfig = thinkingConfig).toGenerationConfig()

        assertNotNull(config)
        assertEquals(thinkingConfig, config.thinkingConfig)
    }

    @Test
    fun baseParamsHaveNoThinkingConfig() {
        val config = LLMParams(temperature = 0.3).toGenerationConfig()

        assertNotNull(config)
        assertNull(config.thinkingConfig)
    }
}
