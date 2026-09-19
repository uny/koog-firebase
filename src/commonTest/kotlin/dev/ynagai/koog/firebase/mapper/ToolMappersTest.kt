package dev.ynagai.koog.firebase.mapper

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.params.LLMParams
import dev.ynagai.firebase.ai.FunctionCallingMode
import dev.ynagai.firebase.ai.LatLng
import dev.ynagai.firebase.ai.RetrievalConfig
import dev.ynagai.firebase.ai.SchemaType
import dev.ynagai.firebase.ai.Tool
import dev.ynagai.koog.firebase.FirebaseLLMParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun resolveToolConfigMapsChoiceWhenToolsArePresent() {
        val tools = listOf(
            ToolDescriptor("get_weather", "Get the weather"),
        ).toFirebaseTools()

        val config = resolveToolConfig(tools, LLMParams.ToolChoice.Required)

        assertEquals(FunctionCallingMode.ANY, config?.functionCallingConfig?.mode)
    }

    @Test
    fun resolveToolConfigIsNullWhenNoToolsEvenIfChoiceSet() {
        assertNull(resolveToolConfig(null, LLMParams.ToolChoice.Required))
    }

    @Test
    fun resolveToolConfigIsNullWhenNoChoiceSet() {
        val tools = listOf(
            ToolDescriptor("get_weather", "Get the weather"),
        ).toFirebaseTools()

        assertNull(resolveToolConfig(tools, null))
    }

    @Test
    fun resolveToolsIsNullWithoutFunctionOrBuiltInTools() {
        assertNull(resolveToolsForTest(emptyList(), LLMParams()))
        assertNull(resolveToolsForTest(emptyList(), FirebaseLLMParams()))
    }

    @Test
    fun resolveToolsAppendsBuiltInToolsAfterFunctionDeclarations() {
        val params = FirebaseLLMParams(builtInTools = listOf(Tool.googleSearch(), Tool.urlContext()))
        val descriptors = listOf(ToolDescriptor("get_weather", "Get the weather"))

        val tools = resolveToolsForTest(descriptors, params)

        assertEquals(3, tools?.size)
        assertTrue(tools?.get(0) is Tool.FunctionDeclarations)
        assertEquals(Tool.GoogleSearch, tools?.get(1))
        assertEquals(Tool.UrlContext, tools?.get(2))
    }

    @Test
    fun resolveToolsPassesBuiltInToolsWithoutFunctionDeclarations() {
        val params = FirebaseLLMParams(builtInTools = listOf(Tool.googleSearch()))

        assertEquals(listOf(Tool.GoogleSearch), resolveToolsForTest(emptyList(), params))
    }

    @Test
    fun plainLLMParamsCarryNoBuiltInTools() {
        val tools = resolveToolsForTest(listOf(ToolDescriptor("get_weather", "Get the weather")), LLMParams())

        assertEquals(1, tools?.size)
        assertTrue(tools?.single() is Tool.FunctionDeclarations)
    }

    @Test
    fun builtInToolsRejectFunctionDeclarations() {
        assertFailsWith<IllegalArgumentException> {
            FirebaseLLMParams(builtInTools = listOf(Tool.functionDeclarations(emptyList())))
        }
    }

    @Test
    fun resolveToolConfigIgnoresChoiceWhenOnlyBuiltInToolsArePresent() {
        // Firebase rejects a forcing mode without function declarations, so no config is sent.
        assertNull(resolveToolConfig(listOf(Tool.googleSearch()), LLMParams.ToolChoice.Required))
    }

    @Test
    fun resolveToolConfigMapsChoiceWhenFunctionsAreMixedWithBuiltInTools() {
        val tools = listOf(ToolDescriptor("get_weather", "Get the weather")).toFirebaseTools() + Tool.googleSearch()

        val config = resolveToolConfig(tools, LLMParams.ToolChoice.Required)

        assertEquals(FunctionCallingMode.ANY, config?.functionCallingConfig?.mode)
        assertNull(config?.retrievalConfig)
    }

    @Test
    fun resolveToolConfigPassesRetrievalConfigWithoutFunctionCallingConfig() {
        val retrievalConfig = RetrievalConfig(latLng = LatLng(35.68, 139.76), languageCode = "ja")

        val config = resolveToolConfig(listOf(Tool.googleMaps()), LLMParams.ToolChoice.Required, retrievalConfig)

        assertNull(config?.functionCallingConfig)
        assertEquals(retrievalConfig, config?.retrievalConfig)
    }

    private fun resolveToolsForTest(descriptors: List<ToolDescriptor>, params: LLMParams): List<Tool>? =
        resolveTools(descriptors, params)
}
