package dev.ynagai.koog.firebase.mapper

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import dev.ynagai.firebase.ai.SchemaType
import dev.ynagai.firebase.ai.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
