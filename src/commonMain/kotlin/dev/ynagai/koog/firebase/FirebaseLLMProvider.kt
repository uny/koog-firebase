package dev.ynagai.koog.firebase

import ai.koog.prompt.llm.LLMProvider
import kotlinx.serialization.Serializable

@Serializable
data object FirebaseLLMProvider : LLMProvider("firebase", "Firebase")
