package dev.ynagai.koog.firebase.mapper

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.params.LLMParams
import dev.ynagai.firebase.ai.FunctionCallingMode
import dev.ynagai.firebase.ai.SchemaType
import dev.ynagai.firebase.ai.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolMappersTest {

    @Test
    fun emptyToolListMapsToEmpty() {
        assertTrue(emptyList<ToolDescriptor>().toFirebaseTools().isEmpty())
    }

    @Test
    fun toolDescriptorMapsToFunctionDeclaration() {
        val descriptor = ToolDescriptor(
            name = "get_weather",
            description = "Get the weather",
            requiredParameters = listOf(
                ToolParameterDescriptor("city", "City name", ToolParameterType.String),
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("days", "Forecast days", ToolParameterType.Integer),
            ),
        )

        val tools = listOf(descriptor).toFirebaseTools()

        assertEquals(1, tools.size)
        val tool = tools[0]
        assertTrue(tool is Tool.FunctionDeclarations)
        val declaration = tool.declarations.single()
        assertEquals("get_weather", declaration.name)
        assertEquals("Get the weather", declaration.description)
        assertEquals(setOf("city", "days"), declaration.parameters.keys)
        assertEquals(SchemaType.STRING, declaration.parameters.getValue("city").type)
        assertEquals(SchemaType.INTEGER, declaration.parameters.getValue("days").type)
        assertEquals("int64", declaration.parameters.getValue("days").format)
        assertEquals(listOf("days"), declaration.optionalParameters)
    }

    @Test
    fun nestedListEnumAndObjectTypesMap() {
        val descriptor = ToolDescriptor(
            name = "complex",
            description = "complex tool",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "items",
                    description = "list of items",
                    type = ToolParameterType.List(ToolParameterType.String),
                ),
                ToolParameterDescriptor(
                    name = "filter",
                    description = "filter object",
                    type = ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor("kind", "kind", ToolParameterType.Enum(arrayOf("a", "b"))),
                        ),
                        requiredProperties = listOf("kind"),
                    ),
                ),
            ),
        )

        val declaration = (listOf(descriptor).toFirebaseTools()[0] as Tool.FunctionDeclarations)
            .declarations.single()

        val items = declaration.parameters.getValue("items")
        assertEquals(SchemaType.ARRAY, items.type)
        assertEquals(SchemaType.STRING, items.items?.type)

        val filter = declaration.parameters.getValue("filter")
        assertEquals(SchemaType.OBJECT, filter.type)
        assertEquals(listOf("kind"), filter.requiredProperties)
        val kind = filter.properties?.getValue("kind")
        assertEquals(SchemaType.STRING, kind?.type)
        assertEquals(listOf("a", "b"), kind?.enumValues)
    }

    @Test
    fun anyOfWithNullCollapsesToNullableUnderlyingType() {
        val descriptor = ToolDescriptor(
            name = "nullable_tool",
            description = "tool with a nullable union parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "value",
                    description = "string or null",
                    type = ToolParameterType.AnyOf(
                        arrayOf(
                            ToolParameterDescriptor("s", "string member", ToolParameterType.String),
                            ToolParameterDescriptor("n", "null member", ToolParameterType.Null),
                        ),
                    ),
                ),
            ),
        )

        val declaration = (listOf(descriptor).toFirebaseTools()[0] as Tool.FunctionDeclarations)
            .declarations.single()

        val value = declaration.parameters.getValue("value")
        assertEquals(SchemaType.STRING, value.type)
        assertEquals(true, value.nullable)
        assertEquals("string or null", value.description)
    }

    @Test
    fun anyOfWithMultipleNonNullMembersFallsBackToFirst() {
        val descriptor = ToolDescriptor(
            name = "union_tool",
            description = "tool with a multi-member union parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "value",
                    description = "string or integer",
                    type = ToolParameterType.AnyOf(
                        arrayOf(
                            ToolParameterDescriptor("s", "string member", ToolParameterType.String),
                            ToolParameterDescriptor("i", "integer member", ToolParameterType.Integer),
                        ),
                    ),
                ),
            ),
        )

        val declaration = (listOf(descriptor).toFirebaseTools()[0] as Tool.FunctionDeclarations)
            .declarations.single()

        // No Null member, so the union falls back to the first non-null type and is not forced nullable.
        val value = declaration.parameters.getValue("value")
        assertEquals(SchemaType.STRING, value.type)
        assertTrue(value.nullable != true)
    }

    @Test
    fun toolChoiceModesMapToFunctionCallingModes() {
        assertEquals(
            FunctionCallingMode.AUTO,
            LLMParams.ToolChoice.Auto.toFirebaseToolConfig().functionCallingConfig?.mode,
        )
        assertEquals(
            FunctionCallingMode.NONE,
            LLMParams.ToolChoice.None.toFirebaseToolConfig().functionCallingConfig?.mode,
        )
        assertEquals(
            FunctionCallingMode.ANY,
            LLMParams.ToolChoice.Required.toFirebaseToolConfig().functionCallingConfig?.mode,
        )
    }

    @Test
    fun namedToolChoiceRestrictsToSingleFunction() {
        val config = LLMParams.ToolChoice.Named("get_weather").toFirebaseToolConfig()

        val functionCallingConfig = config.functionCallingConfig
        assertEquals(FunctionCallingMode.ANY, functionCallingConfig?.mode)
        assertEquals(listOf("get_weather"), functionCallingConfig?.allowedFunctionNames)
    }

    @Test
    fun modesOtherThanNamedHaveNoAllowedFunctionNames() {
        assertNull(LLMParams.ToolChoice.Auto.toFirebaseToolConfig().functionCallingConfig?.allowedFunctionNames)
        assertNull(LLMParams.ToolChoice.None.toFirebaseToolConfig().functionCallingConfig?.allowedFunctionNames)
        assertNull(LLMParams.ToolChoice.Required.toFirebaseToolConfig().functionCallingConfig?.allowedFunctionNames)
    }
}
