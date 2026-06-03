package dev.ynagai.koog.firebase.mapper

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Helpers for converting between the dynamic `Map<String, Any?>` shape that Firebase uses for
 * function-call arguments and responses, and the `kotlinx.serialization` [JsonElement] shape that
 * Koog uses for tool-call arguments.
 */

/** Converts a Firebase dynamic argument/response map into a Koog [JsonObject]. */
internal fun Map<String, Any?>.toJsonObject(): JsonObject =
    JsonObject(mapValues { (_, value) -> value.toJsonElement() })

/** Wraps a single dynamic Firebase value (primitive, map, or list) as a [JsonElement]. */
internal fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

/** Converts a Koog [JsonObject] back into the dynamic map shape Firebase expects. */
internal fun JsonObject.toAnyMap(): Map<String, Any?> = mapValues { (_, value) -> value.toAnyValue() }

/** Unwraps a [JsonElement] into a plain Kotlin value (String/Boolean/Long/Double, map, or list). */
internal fun JsonElement.toAnyValue(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
    is JsonObject -> mapValues { (_, value) -> value.toAnyValue() }
    is JsonArray -> map { it.toAnyValue() }
}
