package dev.ynagai.koog.firebase.mapper

import dev.ynagai.firebase.ai.Candidate
import dev.ynagai.firebase.ai.GroundingMetadata
import dev.ynagai.firebase.ai.UrlContextMetadata
import dev.ynagai.koog.firebase.FirebaseMetadataKeys.GROUNDING_METADATA
import dev.ynagai.koog.firebase.FirebaseMetadataKeys.URL_CONTEXT_METADATA
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal fun Candidate.toolMetadataJson(): JsonObject? =
    toolMetadataJson(groundingMetadata, urlContextMetadata)

/**
 * Serializes built-in-tool metadata (Google Search grounding, URL context) into the [JsonObject]
 * carried by Koog's `ResponseMetaInfo.metadata`. Returns `null` when both are absent so callers
 * can keep the default empty metadata.
 *
 * The Firebase SDK types are not `@Serializable`, so the JSON is built by hand. Field names mirror
 * the Gemini REST API; enum values use the SDK's short names (e.g. `SUCCESS`). Note that
 * `groundingSupports[].segment.partIndex` indexes the Firebase candidate's parts, not the mapped
 * Koog message parts (unsupported parts are dropped during mapping).
 */
internal fun toolMetadataJson(
    groundingMetadata: GroundingMetadata?,
    urlContextMetadata: UrlContextMetadata?,
): JsonObject? {
    if (groundingMetadata == null && urlContextMetadata == null) return null
    return buildJsonObject {
        groundingMetadata?.let { put(GROUNDING_METADATA, it.toJson()) }
        urlContextMetadata?.let { put(URL_CONTEXT_METADATA, it.toJson()) }
    }
}

private fun GroundingMetadata.toJson(): JsonObject = buildJsonObject {
    putJsonArray("webSearchQueries") { webSearchQueries.forEach { add(it) } }
    searchEntryPoint?.let { entryPoint ->
        putJsonObject("searchEntryPoint") { put("renderedContent", entryPoint.renderedContent) }
    }
    putJsonArray("groundingChunks") {
        groundingChunks.forEach { chunk ->
            addJsonObject {
                chunk.web?.let { web ->
                    putJsonObject("web") {
                        web.uri?.let { put("uri", it) }
                        web.title?.let { put("title", it) }
                        web.domain?.let { put("domain", it) }
                    }
                }
                chunk.maps?.let { maps ->
                    putJsonObject("maps") {
                        maps.uri?.let { put("uri", it) }
                        maps.title?.let { put("title", it) }
                        maps.placeId?.let { put("placeId", it) }
                    }
                }
            }
        }
    }
    putJsonArray("groundingSupports") {
        groundingSupports.forEach { support ->
            addJsonObject {
                putJsonObject("segment") {
                    put("partIndex", support.segment.partIndex)
                    put("startIndex", support.segment.startIndex)
                    put("endIndex", support.segment.endIndex)
                    put("text", support.segment.text)
                }
                putJsonArray("groundingChunkIndices") { support.groundingChunkIndices.forEach { add(it) } }
            }
        }
    }
}

private fun UrlContextMetadata.toJson(): JsonObject = buildJsonObject {
    putJsonArray("urlMetadata") {
        urlMetadata.forEach { entry ->
            addJsonObject {
                entry.retrievedUrl?.let { put("retrievedUrl", it) }
                put("urlRetrievalStatus", entry.retrievalStatus.name)
            }
        }
    }
}
