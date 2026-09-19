package dev.ynagai.koog.firebase

/**
 * Keys under which Firebase-specific response data is exposed in Koog's
 * `ResponseMetaInfo.metadata` (on [ai.koog.prompt.message.Message.Assistant.metaInfo] and on the
 * streaming `StreamFrame.End` frame).
 *
 * The values are JSON objects mirroring the Gemini REST field names, e.g.:
 * ```kotlin
 * val grounding = response.metaInfo.metadata[FirebaseMetadataKeys.GROUNDING_METADATA]?.jsonObject
 * val sources = grounding?.get("groundingChunks")?.jsonArray
 * val searchSuggestions = grounding?.get("searchEntryPoint")?.jsonObject?.get("renderedContent")
 * ```
 */
object FirebaseMetadataKeys {
    /**
     * Google Search grounding metadata (`Tool.googleSearch()`): `webSearchQueries`,
     * `searchEntryPoint.renderedContent`, `groundingChunks`, `groundingSupports`.
     *
     * Note: Google's terms require apps that use Google Search grounding to display the
     * Search Suggestions from `searchEntryPoint.renderedContent`.
     */
    const val GROUNDING_METADATA: String = "groundingMetadata"

    /** URL context metadata (`Tool.urlContext()`): `urlMetadata[]{retrievedUrl, urlRetrievalStatus}`. */
    const val URL_CONTEXT_METADATA: String = "urlContextMetadata"
}
