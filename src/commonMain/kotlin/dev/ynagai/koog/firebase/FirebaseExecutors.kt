package dev.ynagai.koog.firebase

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import dev.ynagai.firebase.Firebase
import dev.ynagai.firebase.FirebaseApp
import dev.ynagai.firebase.ai.GenerativeBackend
import dev.ynagai.firebase.ai.ai
import dev.ynagai.firebase.app

/**
 * Creates a simple Firebase AI executor for use with Koog agents.
 *
 * This factory function creates a [SingleLLMPromptExecutor] configured to use
 * Firebase AI's generative models through the [FirebaseLLMClient].
 *
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(
 *     promptExecutor = simpleFirebaseExecutor(),
 *     systemPrompt = "You are a helpful assistant.",
 *     llmModel = FirebaseModels.Gemini2_5Flash
 * )
 * val result = agent.run("Hello!")
 * ```
 *
 * @param app The FirebaseApp instance to use. Defaults to the default Firebase app.
 * @param backend The generative backend to use (Google AI or Vertex AI). Defaults to Google AI.
 * @return A [SingleLLMPromptExecutor] configured with Firebase AI client.
 */
fun simpleFirebaseExecutor(
    app: FirebaseApp = Firebase.app,
    backend: GenerativeBackend = GenerativeBackend.googleAI(),
): SingleLLMPromptExecutor {
    val firebaseAI = Firebase.ai(app, backend)
    val client = FirebaseLLMClient(firebaseAI)
    return SingleLLMPromptExecutor(client)
}
