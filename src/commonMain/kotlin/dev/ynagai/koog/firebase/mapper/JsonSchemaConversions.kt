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
 *
 * Koog's `Standard` schema generator emits `$ref`/`$defs` for nested and reused types, whereas the
 * `Basic` generator inlines everything. Gemini's [Schema] model is a flat tree with no notion of
 * references, so this converter resolves `$ref` against the document's `$defs` while flattening, and
 * collapses `oneOf`/`anyOf` unions to the closest representable shape.
 */
internal fun JsonObject.toFirebaseSchema(): Schema {
    val definitions = (this[DEFS] as? JsonObject) ?: JsonObject(emptyMap())
    return toFirebaseSchema(definitions, emptySet())
}

/**
 * @param definitions the document's `$defs` block, used to resolve `$ref` nodes.
 * @param visitedRefs refs already expanded on the current branch, to break recursive schemas that
 *        Gemini's flat schema model cannot express.
 */
private fun JsonObject.toFirebaseSchema(
    definitions: JsonObject,
    visitedRefs: Set<String>,
): Schema {
    // Resolve a JSON Schema reference (e.g. `{"$ref": "#/$defs/Address"}`) against the definitions.
    (this[REF] as? JsonPrimitive)?.contentOrNull?.let { ref ->
        val target = definitions.resolveRef(ref)
        // A missing or already-visited (recursive) ref degrades to a plain string placeholder.
        if (target == null || ref in visitedRefs) return Schema.string()
        // Koog attaches the property-level description as a sibling of `$ref`; keep it (it is more
        // specific than the referenced type's own description).
        return target.toFirebaseSchema(definitions, visitedRefs + ref).withDescriptionOf(this)
    }

    // A `oneOf`/`anyOf` union (a nullable or polymorphic type) is collapsed to its first non-null
    // branch, the closest representation Gemini's schema model supports.
    ((this[ONE_OF] as? JsonArray) ?: (this[ANY_OF] as? JsonArray))?.let { union ->
        val branches = union.mapNotNull { it as? JsonObject }
        val nullableFromUnion = branches.any { (it[TYPE] as? JsonPrimitive)?.contentOrNull == "null" }
        branches.firstOrNull { (it[TYPE] as? JsonPrimitive)?.contentOrNull != "null" }?.let { primary ->
            val resolved = primary.toFirebaseSchema(definitions, visitedRefs)
            val withNullable = if (nullableFromUnion && !resolved.nullable) resolved.copy(nullable = true) else resolved
            // The description lives on the union node, not the branches; keep it.
            return withNullable.withDescriptionOf(this)
        }
    }

    val (typeName, nullableFromType) = (this[TYPE]).extractType()
    val description = (this[DESCRIPTION] as? JsonPrimitive)?.contentOrNull
    val format = (this["format"] as? JsonPrimitive)?.contentOrNull
    val nullable = nullableFromType || ((this["nullable"] as? JsonPrimitive)?.booleanOrNull == true)
    val enumValues = (this[ENUM] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

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
            items = (this["items"] as? JsonObject)?.toFirebaseSchema(definitions, visitedRefs)
                ?: Schema.string(),
        )

        "object" -> Schema(
            type = SchemaType.OBJECT,
            description = description,
            nullable = nullable,
            properties = (this[PROPERTIES] as? JsonObject)
                ?.mapNotNull { (key, value) ->
                    (value as? JsonObject)?.let { key to it.toFirebaseSchema(definitions, visitedRefs) }
                }
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

/**
 * Resolves a local JSON pointer such as `#/$defs/Name` or `#/definitions/Name` to the definition it
 * points at. Only the single-segment definitions used by Koog's generators are supported.
 */
private fun JsonObject.resolveRef(ref: String): JsonObject? =
    this[ref.substringAfterLast('/')] as? JsonObject

/**
 * Overrides the description with [node]'s own `description`, when present. Used when a schema is
 * produced by resolving a `$ref` or collapsing a `oneOf`/`anyOf`, so the call-site description
 * (which Koog emits alongside those keywords) is not lost.
 */
private fun Schema.withDescriptionOf(node: JsonObject): Schema =
    (node[DESCRIPTION] as? JsonPrimitive)?.contentOrNull?.let { copy(description = it) } ?: this

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

private const val DEFS = "\$defs"
private const val REF = "\$ref"
private const val TYPE = "type"
private const val PROPERTIES = "properties"
private const val ENUM = "enum"
private const val ONE_OF = "oneOf"
private const val ANY_OF = "anyOf"
private const val DESCRIPTION = "description"
