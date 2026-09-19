# Koog Firebase

[![Maven Central](https://img.shields.io/maven-central/v/dev.ynagai.koog/koog-firebase)](https://central.sonatype.com/artifact/dev.ynagai.koog/koog-firebase)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/uny/koog-firebase/actions/workflows/ci.yml/badge.svg)](https://github.com/uny/koog-firebase/actions/workflows/ci.yml)

Firebase Vertex AI integration for the [Koog Agent Framework](https://github.com/JetBrains/koog).

## Features

- Kotlin Multiplatform support (Android, iOS)
- Firebase AI (Google AI / Vertex AI) backend integration
- Seamless integration with Koog agents
- Pre-configured Gemini model definitions
- Streaming response support
- Function calling (tool) support
- Thinking / reasoning configuration
- Built-in Gemini tools: Google Search grounding, URL context, code execution, Google Maps grounding

## Installation

### Version Catalog

Add to your `libs.versions.toml`:

```toml
[versions]
koog-firebase = "0.8.0"

[libraries]
koog-firebase = { module = "dev.ynagai.koog:koog-firebase", version.ref = "koog-firebase" }
```

Then in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.koog.firebase)
}
```

### Gradle DSL

```kotlin
dependencies {
    implementation("dev.ynagai.koog:koog-firebase:0.8.0")
}
```

## Usage

### Simple Usage

```kotlin
import ai.koog.agents.core.agent.AIAgent
import dev.ynagai.koog.firebase.FirebaseModels
import dev.ynagai.koog.firebase.simpleFirebaseExecutor

val agent = AIAgent(
    promptExecutor = simpleFirebaseExecutor(),
    systemPrompt = "You are a helpful assistant.",
    llmModel = FirebaseModels.Gemini3_7Flash
)

val result = agent.run("Hello!")
```

### Custom Configuration

```kotlin
import dev.ynagai.firebase.Firebase
import dev.ynagai.firebase.ai.GenerativeBackend
import dev.ynagai.koog.firebase.simpleFirebaseExecutor

// Use Vertex AI backend.
// Gemini 3.x models are only available in the "global" location; vertexAI()
// defaults to "us-central1", which will 404 for those models.
val executor = simpleFirebaseExecutor(
    app = Firebase.app,
    backend = GenerativeBackend.vertexAI("global")
)
```

### Built-in Tools (Google Search, URL context, ...)

Gemini can run some tools server-side without a Koog tool round-trip. Request them via
`FirebaseLLMParams.builtInTools` using the Firebase SDK's `Tool` factories:

| Firebase tool | What it does |
|---------------|--------------|
| `Tool.googleSearch()` | Grounds the answer with Google Search results (web search) |
| `Tool.urlContext()` | Fetches URLs mentioned in the prompt and uses their content (web fetch) |
| `Tool.codeExecution()` | Lets the model write and run code; code and output are surfaced as text parts |
| `Tool.googleMaps()` | Grounds the answer with Google Maps; pass `retrievalConfig` for location |

```kotlin
import ai.koog.prompt.dsl.prompt
import dev.ynagai.firebase.ai.Tool
import dev.ynagai.koog.firebase.FirebaseLLMParams
import dev.ynagai.koog.firebase.FirebaseMetadataKeys
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val prompt = prompt("search", params = FirebaseLLMParams(
    builtInTools = listOf(Tool.googleSearch(), Tool.urlContext()),
)) {
    user("What changed in the latest Kotlin release? See https://kotlinlang.org/docs/whatsnew.html")
}

val response = executor.execute(prompt, FirebaseModels.Gemini3_7Flash).first()

// Grounding sources and Search Suggestions are exposed in the response metadata.
val grounding = response.metaInfo.metadata?.get(FirebaseMetadataKeys.GROUNDING_METADATA)?.jsonObject
val sources = grounding?.get("groundingChunks")?.jsonArray
    ?.mapNotNull { it.jsonObject["web"]?.jsonObject?.get("uri")?.jsonPrimitive?.content }
val searchSuggestionsHtml = grounding?.get("searchEntryPoint")?.jsonObject
    ?.get("renderedContent")?.jsonPrimitive?.content
```

Built-in tools can be combined with regular Koog tools; `toolChoice` only affects the Koog
function tools. When streaming, the same metadata is attached to the final `StreamFrame.End`
frame's `metaInfo`.

> **Note:** When using Google Search grounding, Google's terms require your app to display the
> Search Suggestions from `searchEntryPoint.renderedContent`. See the
> [Firebase AI Logic grounding docs](https://firebase.google.com/docs/ai-logic/grounding-google-search).

### Available Models

| Model | Description |
|-------|-------------|
| `FirebaseModels.Gemini3_7Flash` | Latest stable Gemini 3.x Flash model |
| `FirebaseModels.Gemini3_6Flash` | Previous stable Gemini 3.x Flash model |
| `FirebaseModels.Gemini3FlashPreview` | Preview version of the Gemini 3.x Flash line |
| `FirebaseModels.Gemini3_5Flash` | Older stable Gemini 3.x Flash model |
| `FirebaseModels.Gemini3_5FlashLite` | High-volume, cost-sensitive workhorse model |
| `FirebaseModels.Gemini3_1Pro` | Advanced reasoning (preview) |
| `FirebaseModels.Gemini3_1FlashLite` | Ultra-fast, budget-friendly |
| `FirebaseModels.Gemini2_5Pro` (deprecated) | High-capability with speculation support — retires October 2026 |
| `FirebaseModels.Gemini2_5Flash` (deprecated) | Fast and efficient with speculation support — retires October 2026 |
| `FirebaseModels.Gemini2_5FlashLite` (deprecated) | Budget-friendly Flash variant — retires October 2026 |

## Requirements

- Kotlin 2.1+
- Firebase project with AI enabled
- Android API 24+ / iOS 13+

## License

```
Copyright 2025 Yuki Nagai

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
