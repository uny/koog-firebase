package dev.ynagai.koog.firebase.mapper

import dev.ynagai.firebase.ai.SchemaType
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonSchemaConversionsTest {

    @Test
    fun mapsObjectWithPropertiesAndRequired() {
        val json = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("city") {
                    put("type", "string")
                    put("description", "City name")
                }
                putJsonObject("days") {
                    put("type", "integer")
                }
            }
            putJsonArray("required") { add("city") }
        }

        val schema = json.toFirebaseSchema()

        assertEquals(SchemaType.OBJECT, schema.type)
        assertEquals(setOf("city", "days"), schema.properties?.keys)
        assertEquals(SchemaType.STRING, schema.properties?.getValue("city")?.type)
        assertEquals("City name", schema.properties?.getValue("city")?.description)
        assertEquals(SchemaType.INTEGER, schema.properties?.getValue("days")?.type)
        assertEquals(listOf("city"), schema.requiredProperties)
    }

    @Test
    fun mapsArrayOfIntegers() {
        val json = buildJsonObject {
            put("type", "array")
            putJsonObject("items") { put("type", "integer") }
        }

        val schema = json.toFirebaseSchema()

        assertEquals(SchemaType.ARRAY, schema.type)
        assertEquals(SchemaType.INTEGER, schema.items?.type)
    }

    @Test
    fun typeUnionWithNullMarksSchemaNullable() {
        val json = buildJsonObject {
            putJsonArray("type") {
                add("string")
                add("null")
            }
        }

        val schema = json.toFirebaseSchema()

        assertEquals(SchemaType.STRING, schema.type)
        assertTrue(schema.nullable)
    }

    @Test
    fun mapsEnumValues() {
        val json = buildJsonObject {
            put("type", "string")
            putJsonArray("enum") {
                add("a")
                add("b")
            }
        }

        val schema = json.toFirebaseSchema()

        assertEquals(SchemaType.STRING, schema.type)
        assertEquals(listOf("a", "b"), schema.enumValues)
    }
}
