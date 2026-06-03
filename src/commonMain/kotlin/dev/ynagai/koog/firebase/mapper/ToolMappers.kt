package dev.ynagai.koog.firebase.mapper

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import dev.ynagai.firebase.ai.FunctionDeclaration
import dev.ynagai.firebase.ai.Schema
import dev.ynagai.firebase.ai.Tool

/**
 * Converts Koog [ToolDescriptor]s into the single Firebase [Tool] that carries their
 * [FunctionDeclaration]s. Returns an empty list when there are no tools so callers can pass
 * `null` to the Firebase SDK.
 */
internal fun List<ToolDescriptor>.toFirebaseTools(): List<Tool> =
    if (isEmpty()) {
        emptyList()
    } else {
        listOf(Tool.functionDeclarations(map { it.toFunctionDeclaration() }))
    }

internal fun ToolDescriptor.toFunctionDeclaration(): FunctionDeclaration {
    val allParameters = requiredParameters + optionalParameters
    return FunctionDeclaration(
        name = name,
        description = description,
        parameters = allParameters.associate { it.name to it.type.toSchema(it.description) },
        optionalParameters = optionalParameters.map { it.name },
    )
}

private fun ToolParameterType.toSchema(description: String? = null): Schema = when (this) {
    ToolParameterType.String -> Schema.string(description)
    // Koog's Integer/Float carry no bit-width, so map to the wider Firebase types (int64/double)
    // to avoid narrowing large or high-precision values the model may produce.
    ToolParameterType.Integer -> Schema.long(description)
    ToolParameterType.Float -> Schema.double(description)
    ToolParameterType.Boolean -> Schema.boolean(description)
    ToolParameterType.Null -> Schema.string(description = description, nullable = true)
    is ToolParameterType.Enum -> Schema.enumeration(entries.toList(), description)
    is ToolParameterType.List -> Schema.array(items = itemsType.toSchema(), description = description)
    is ToolParameterType.Object -> Schema.obj(
        properties = properties.associate { it.name to it.type.toSchema(it.description) },
        requiredProperties = requiredProperties,
        description = description,
    )
    is ToolParameterType.AnyOf -> toNullableUnionSchema(description)
}

/**
 * Firebase's [Schema] cannot express arbitrary type unions. The common case Koog emits is a
 * nullable type (e.g. `string | null`), which we collapse into the underlying type marked
 * nullable. For richer unions we fall back to the first non-null member as a best effort.
 */
private fun ToolParameterType.AnyOf.toNullableUnionSchema(description: String?): Schema {
    val nonNullTypes = types.filterNot { it.type is ToolParameterType.Null }
    val isNullable = types.any { it.type is ToolParameterType.Null }
    val baseSchema = (nonNullTypes.firstOrNull()?.type ?: ToolParameterType.String).toSchema(description)
    return if (isNullable) baseSchema.copy(nullable = true) else baseSchema
}
