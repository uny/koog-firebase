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
    this[REF].asString()?.let { ref ->
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
        val nullableFromUnion = branches.any { it[TYPE].asString() == "null" }
        branches.firstOrNull { it[TYPE].asString() != "null" }?.let { primary ->
            val resolved = primary.toFirebaseSchema(definitions, visitedRefs)
            // The description lives on the union node, not the branches; keep it.
            return resolved.copy(nullable = resolved.nullable || nullableFromUnion).withDescriptionOf(this)
        }
    }

    val (typeName, nullableFromType) = this[TYPE].extractType()
    val description = this[DESCRIPTION].asString()
    val format = this[FORMAT].asString()
    val nullable = nullableFromType || ((this[NULLABLE] as? JsonPrimitive)?.booleanOrNull == true)

    return when (typeName) {
        "integer", "number", "boolean" -> Schema(
            type = when (typeName) {
                "integer" -> SchemaType.INTEGER
                "number" -> SchemaType.NUMBER
                else -> SchemaType.BOOLEAN
            },
            description = description,
            format = format,
            nullable = nullable,
        )

        "array" -> Schema.array(
            items = (this[ITEMS] as? JsonObject)?.toFirebaseSchema(definitions, visitedRefs)
                ?: Schema.string(),
            description = description,
            nullable = nullable,
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
            requiredProperties = (this[REQUIRED] as? JsonArray)?.mapNotNull { it.asString() },
        )

        else -> Schema(
            type = SchemaType.STRING,
            description = description,
            format = format,
            nullable = nullable,
            enumValues = (this[ENUM] as? JsonArray)?.mapNotNull { it.asString() },
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
    node[DESCRIPTION].asString()?.let { copy(description = it) } ?: this

/** Reads a JSON string value, or null when the element is absent or not a string primitive. */
private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

/**
 * Resolves a JSON Schema `type` keyword that may be either a single type string or an array of
 * types. Returns the primary (non-"null") type and whether "null" was part of the type union.
 */
private fun JsonElement?.extractType(): Pair<String?, Boolean> = when (this) {
    is JsonPrimitive -> contentOrNull to false
    is JsonArray -> {
        val types = mapNotNull { it.asString() }
        types.firstOrNull { it != "null" } to types.contains("null")
    }
    else -> null to false
}

private const val DEFS = "\$defs"
private const val REF = "\$ref"
private const val TYPE = "type"
private const val FORMAT = "format"
private const val NULLABLE = "nullable"
private const val PROPERTIES = "properties"
private const val REQUIRED = "required"
private const val ITEMS = "items"
private const val ENUM = "enum"
private const val ONE_OF = "oneOf"
private const val ANY_OF = "anyOf"
private const val DESCRIPTION = "description"
