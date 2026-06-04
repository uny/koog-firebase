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

    @Test
    fun resolvesRefAgainstDefs() {
        // Shape produced by Koog's Standard generator: nested types are referenced via `$ref` and
        // their definitions live under a top-level `$defs` block.
        val json = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("address") { put("\$ref", "#/\$defs/Address") }
            }
            putJsonArray("required") { add("address") }
            putJsonObject("\$defs") {
                putJsonObject("Address") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("city") { put("type", "string") }
                    }
                }
            }
        }

        val schema = json.toFirebaseSchema()

        val address = schema.properties?.getValue("address")
        assertEquals(SchemaType.OBJECT, address?.type)
        assertEquals(SchemaType.STRING, address?.properties?.getValue("city")?.type)
    }

    @Test
    fun recursiveRefDoesNotOverflow() {
        // A self-referential type cannot be expressed by Gemini's flat schema; the recursive branch
        // is cut off rather than expanded forever.
        val json = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("next") { put("\$ref", "#/\$defs/Node") }
            }
            putJsonObject("\$defs") {
                putJsonObject("Node") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("next") { put("\$ref", "#/\$defs/Node") }
                    }
                }
            }
        }

        val schema = json.toFirebaseSchema()

        // The first level expands to an object; deeper self-references collapse to a string stub.
        val next = schema.properties?.getValue("next")
        assertEquals(SchemaType.OBJECT, next?.type)
        assertEquals(SchemaType.STRING, next?.properties?.getValue("next")?.type)
    }

    @Test
    fun collapsesNullableOneOfUnion() {
        // kotlinx.serialization renders nullable references as a `oneOf` with a `null` branch.
        val json = buildJsonObject {
            putJsonArray("oneOf") {
                add(buildJsonObject { put("type", "string") })
                add(buildJsonObject { put("type", "null") })
            }
        }

        val schema = json.toFirebaseSchema()

        assertEquals(SchemaType.STRING, schema.type)
        assertTrue(schema.nullable)
    }

    @Test
    fun collapsesAnyOfToFirstBranch() {
        val json = buildJsonObject {
            putJsonArray("anyOf") {
                add(buildJsonObject { put("type", "integer") })
                add(buildJsonObject { put("type", "string") })
            }
        }

        val schema = json.toFirebaseSchema()

        assertEquals(SchemaType.INTEGER, schema.type)
    }
}
