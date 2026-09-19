package dev.ynagai.koog.firebase.mapper

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import dev.ynagai.firebase.ai.CodeExecutionOutcome
import dev.ynagai.firebase.ai.CodeExecutionResultPart
import dev.ynagai.firebase.ai.ExecutableCodePart
import dev.ynagai.firebase.ai.FunctionCallPart
import dev.ynagai.firebase.ai.GenerateContentResponse
import dev.ynagai.firebase.ai.Part
import dev.ynagai.firebase.ai.TextPart
import dev.ynagai.koog.firebase.FirebaseMetadataKeys

/**
 * Converts a Firebase [GenerateContentResponse] into Koog [Message.Assistant]s, mapping text,
 * tool-call, and code-execution parts. Built-in-tool metadata (Google Search grounding, URL
 * context) is per candidate and is exposed through each message's [ResponseMetaInfo.metadata]
 * under [FirebaseMetadataKeys.GROUNDING_METADATA] / [FirebaseMetadataKeys.URL_CONTEXT_METADATA].
 */
internal fun GenerateContentResponse.toKoog(clock: KoogClock): List<Message.Assistant> {
    val inputTokensCount = usageMetadata?.promptTokenCount
    val outputTokensCount = usageMetadata?.candidatesTokenCount
    val totalTokensCount = usageMetadata?.totalTokenCount
    return candidates.map { candidate ->
        val responseParts: List<MessagePart.ResponsePart> =
            candidate.content.parts.mapNotNull { it.toResponsePart() }
        val metaInfo = ResponseMetaInfo.create(
            clock,
            totalTokensCount = totalTokensCount,
            inputTokensCount = inputTokensCount,
            outputTokensCount = outputTokensCount,
            metadata = candidate.toolMetadataJson(),
        )
        Message.Assistant(
            parts = responseParts.ifEmpty { listOf(MessagePart.Text("")) },
            metaInfo = metaInfo,
            finishReason = candidate.finishReason?.name,
        )
    }
}

/**
 * Maps a single Firebase [Part] to a Koog [MessagePart.ResponsePart], or `null` if it has no
 * equivalent. Thought parts become [MessagePart.Reasoning]; code produced and run by the
 * `codeExecution` built-in tool is rendered as text (fenced code block / raw output) since Koog
 * has no dedicated part for server-side execution.
 */
private fun Part.toResponsePart(): MessagePart.ResponsePart? = when (this) {
    is TextPart -> textOrReasoning(text, isThought)
    is FunctionCallPart -> MessagePart.Tool.Call(
        id = id,
        tool = name,
        args = args.toJsonObject(),
    )
    is ExecutableCodePart -> textOrReasoning(renderExecutableCode(), isThought)
    is CodeExecutionResultPart -> renderCodeExecutionResult()?.let { textOrReasoning(it, isThought) }
    else -> null
}

/**
 * Maps a single streamed Firebase [Part] to a Koog [StreamFrame], or `null` if the part has no
 * streaming equivalent. Mirrors [toResponsePart]: a thought becomes a
 * [StreamFrame.ReasoningDelta], other text (including rendered code-execution parts) a
 * [StreamFrame.TextDelta], and a function call a [StreamFrame.ToolCallComplete].
 */
internal fun Part.toStreamFrame(): StreamFrame? = when (this) {
    is TextPart -> textOrReasoningDelta(text, isThought)
    is FunctionCallPart -> StreamFrame.ToolCallComplete(
        id = id,
        name = name,
        content = args.toJsonObject().toString(),
    )
    is ExecutableCodePart -> textOrReasoningDelta(renderExecutableCode(), isThought)
    is CodeExecutionResultPart -> renderCodeExecutionResult()?.let { textOrReasoningDelta(it, isThought) }
    else -> null
}

private fun textOrReasoning(text: String, isThought: Boolean): MessagePart.ResponsePart =
    if (isThought) MessagePart.Reasoning(text) else MessagePart.Text(text)

private fun textOrReasoningDelta(text: String, isThought: Boolean): StreamFrame =
    if (isThought) StreamFrame.ReasoningDelta(text = text) else StreamFrame.TextDelta(text)

/**
 * Renders server-executed code as a Markdown fenced block tagged with its language. Gemini reports
 * `LANGUAGE_UNSPECIFIED` when it has none, which is rendered as an untagged fence. The trailing
 * newline keeps the closing fence on its own line when the next part (typically the execution
 * output) is appended directly, as streaming consumers do.
 */
private fun ExecutableCodePart.renderExecutableCode(): String {
    val tag = language.takeUnless { it.equals("LANGUAGE_UNSPECIFIED", ignoreCase = true) }?.lowercase().orEmpty()
    return "```$tag\n$code\n```\n"
}

/**
 * Renders a code-execution result as text: the raw output, prefixed with a marker when the
 * execution did not succeed so a failure is not mistaken for a run that printed nothing. Returns
 * `null` for a successful run with no output — an empty text part would only be replayed to the
 * model as an empty [TextPart], which Gemini rejects.
 */
private fun CodeExecutionResultPart.renderCodeExecutionResult(): String? = buildString {
    if (outcome != CodeExecutionOutcome.OK) append("[code execution ${outcome.name}]")
    if (output.isNotEmpty()) {
        if (isNotEmpty()) append('\n')
        append(output)
    }
}.ifEmpty { null }
