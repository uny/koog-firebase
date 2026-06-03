package dev.ynagai.koog.firebase.mapper

import dev.ynagai.firebase.ai.Schema
import dev.ynagai.firebase.ai.SchemaType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Converts a JSON Schema document (as used by Koog's structured-output support) into the Firebase
 * [Schema] model. Only the subset of JSON Schema that Gemini understands is mapped; unknown keywords
 * are ignored and a missing/unknown type falls back to [SchemaType.STRING].
 */
internal fun JsonObject.toFirebaseSchema(): Schema {
    val (typeName, nullableFromType) = (this["type"]).extractType()
    val description = (this["description"] as? JsonPrimitive)?.contentOrNull
    val format = (this["format"] as? JsonPrimitive)?.contentOrNull
    val nullable = nullableFromType || ((this["nullable"] as? JsonPrimitive)?.booleanOrNull == true)
    val enumValues = (this["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    return when (typeName) {
        "integer", "number", "boolean" -> Schema(
            type = typeName.toSchemaType(),
            description = description,
            format = format,
            nullable = nullable,
        )

        "array" -> Schema(
            type = SchemaType.ARRAY,
            description = description,
            nullable = nullable,
            items = (this["items"] as? JsonObject)?.toFirebaseSchema() ?: Schema.string(),
        )

        "object" -> Schema(
            type = SchemaType.OBJECT,
            description = description,
            nullable = nullable,
            properties = (this["properties"] as? JsonObject)
                ?.mapNotNull { (key, value) -> (value as? JsonObject)?.let { key to it.toFirebaseSchema() } }
                ?.toMap(),
            requiredProperties = (this["required"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )

        else -> Schema(
            type = SchemaType.STRING,
            description = description,
            format = format,
            nullable = nullable,
            enumValues = enumValues,
        )
    }
}

private fun String.toSchemaType(): SchemaType = when (this) {
    "integer" -> SchemaType.INTEGER
    "number" -> SchemaType.NUMBER
    "boolean" -> SchemaType.BOOLEAN
    else -> SchemaType.STRING
}

/**
 * Resolves a JSON Schema `type` keyword that may be either a single type string or an array of
 * types. Returns the primary (non-"null") type and whether "null" was part of the type union.
 */
private fun JsonElement?.extractType(): Pair<String?, Boolean> = when (this) {
    is JsonPrimitive -> contentOrNull to false
    is JsonArray -> {
        val types = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        types.firstOrNull { it != "null" } to types.contains("null")
    }
    else -> null to false
}
