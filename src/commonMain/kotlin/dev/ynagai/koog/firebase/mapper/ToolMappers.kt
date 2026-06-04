package dev.ynagai.koog.firebase.mapper

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.params.LLMParams
import dev.ynagai.firebase.ai.FunctionCallingConfig
import dev.ynagai.firebase.ai.FunctionCallingMode
import dev.ynagai.firebase.ai.FunctionDeclaration
import dev.ynagai.firebase.ai.Schema
import dev.ynagai.firebase.ai.Tool
import dev.ynagai.firebase.ai.ToolConfig

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

/**
 * Maps a Koog [LLMParams.ToolChoice] to a Firebase [ToolConfig]. Firebase has no dedicated
 * "force a specific tool" mode, so [LLMParams.ToolChoice.Named] is expressed as the `ANY` mode
 * restricted to a single allowed function name.
 */
internal fun LLMParams.ToolChoice.toFirebaseToolConfig(): ToolConfig = ToolConfig(
    functionCallingConfig = when (this) {
        LLMParams.ToolChoice.Auto -> FunctionCallingConfig(mode = FunctionCallingMode.AUTO)
        LLMParams.ToolChoice.None -> FunctionCallingConfig(mode = FunctionCallingMode.NONE)
        LLMParams.ToolChoice.Required -> FunctionCallingConfig(mode = FunctionCallingMode.ANY)
        is LLMParams.ToolChoice.Named -> FunctionCallingConfig(
            mode = FunctionCallingMode.ANY,
            allowedFunctionNames = listOf(name),
        )
    },
)

/**
 * Resolves the Firebase [ToolConfig] for a request. Tool choice is only meaningful when tools are
 * available, so this returns `null` when [tools] is `null` — even if a [toolChoice] is set —
 * because Firebase rejects a forcing mode (e.g. `ANY`) when no functions are declared.
 */
internal fun resolveToolConfig(tools: List<Tool>?, toolChoice: LLMParams.ToolChoice?): ToolConfig? =
    if (tools != null) toolChoice?.toFirebaseToolConfig() else null

/** Converts a single Koog [ToolDescriptor] into a Firebase [FunctionDeclaration]. */
internal fun ToolDescriptor.toFunctionDeclaration(): FunctionDeclaration {
    val allParameters = requiredParameters + optionalParameters
    return FunctionDeclaration(
        name = name,
        description = description,
        parameters = allParameters.associate { it.name to it.type.toSchema(it.description) },
        optionalParameters = optionalParameters.map { it.name },
    )
}

/** Maps a Koog [ToolParameterType] to the corresponding Firebase [Schema], recursing into nested types. */
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
