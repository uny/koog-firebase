# Project Context: koog-firebase

## Overview
`koog-firebase` is a Kotlin Multiplatform (KMP) library designed to integrate **Firebase Vertex AI** (via `dev.ynagai.firebase:firebase-ai`) with the **Koog** AI Agent Framework (v0.6.0).
This library enables Koog agents to utilize Google's Gemini models through Firebase as an LLM provider.

## Goals
1. Provide a Koog-compliant `LLMProvider` or `ChatModel` implementation using Firebase Vertex AI.
2. Support Kotlin Multiplatform targets (Android, iOS, JVM, WasmJS) consistent with `firebase-ai` and `koog-agents`.
3. Map Koog's `Message`, `Role`, and `Tool` abstractions to Firebase's `Content`, `Role`, and `FunctionCall`.

## Dependencies & References

### Core Dependencies
- **Koog Framework**: `ai.koog:koog-agents:0.6.0`
- **Firebase AI SDK**: `dev.ynagai.firebase:firebase-ai` (Local or Maven dependency)
  - Key Package: `dev.ynagai.firebase.ai`
  - Key Classes: `GenerativeModel`, `GenerativeBackend`, `FirebaseAI`

### Integration Architecture
The integration should bridge the Koog `Model` interface with the Firebase `GenerativeModel`.

#### Key Components to Implement
1.  **`FirebaseKoogModel`**:
    * Implements Koog's `ChatModel` (or equivalent interface in v0.6.0).
    * **Initialization**: Should accept a `dev.ynagai.firebase.ai.GenerativeModel` instance or parameters to create one (modelName, config).
    * **Execution**: Maps Koog's input `messages` to Firebase's `generateContent()`.
    * **Streaming**: Implement `generateContentStream()` mapping if Koog supports streaming responses.

2.  **`KoogFirebaseMapper`**:
    * Helper logic to convert between Koog and Firebase types.
    * `Koog.Role` -> `Firebase.Role` (user, model, function).
    * `Koog.Message` -> `Firebase.Content`.

## Usage Example (Conceptual)

```kotlin
// Setup Firebase AI
val firebaseApp = Firebase.app
val backend = GenerativeBackend.googleAI()
val generativeModel = Firebase.ai(firebaseApp, backend).generativeModel("gemini-1.5-flash")

// Initialize Koog Model
val model = FirebaseKoogModel(generativeModel)

// Use in Koog Agent
val agent = Agent(
    model = model,
    systemPrompt = "You are a helpful assistant."
)

```

## Development Guidelines

* **KMP First**: All logic must be in `commonMain`. Platform-specific code should only be used if strictly necessary (e.g., specific concurreny handling), but `firebase-ai` handles most platform diffs.
* **Coroutines**: Use Kotlin Coroutines for all async operations.
* **Error Handling**: Wrap Firebase exceptions (e.g., `FirebaseException`) into Koog's standard error types where applicable to ensure agent fault tolerance.

## File Structure (Suggested)

* `src/commonMain/kotlin/dev/ynagai/koog/firebase/FirebaseKoogModel.kt`
* `src/commonMain/kotlin/dev/ynagai/koog/firebase/Mapper.kt`
