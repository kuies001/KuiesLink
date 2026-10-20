package tw.kuies.link.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Maps an HTTP status + body to a typed result.
 *
 * A 201 is not success on its own: the strict contract must also carry a completed processing
 * status and known cleaning/resolution statuses, otherwise the result is [ShortenError.UNVERIFIED].
 */
object ShortenResponseParser {

    fun parse(httpCode: Int, body: String): ShortenResult {
        if (httpCode == 422) return ShortenResult.Failure(ShortenError.WRAPPER_UNRESOLVED, 422)
        if (httpCode == 429) return ShortenResult.Failure(ShortenError.RATE_LIMITED, 429)
        if (httpCode == 400) return ShortenResult.Failure(ShortenError.INVALID_REQUEST, 400)
        if (httpCode !in 200..299) return ShortenResult.Failure(ShortenError.SERVER_ERROR, httpCode)

        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return ShortenResult.Failure(ShortenError.INVALID_RESPONSE, httpCode)

        val shortUrl = root.string("short_url")
        val targetUrl = root.string("target_url")
        if (shortUrl.isNullOrBlank() || targetUrl.isNullOrBlank()) {
            return ShortenResult.Failure(ShortenError.INVALID_RESPONSE, httpCode)
        }

        val processing = root.string("processing_status")
        val resolution = root.string("resolution_status")
        val cleaning = root.string("cleaning_status")
        if (processing.isNullOrBlank() || resolution.isNullOrBlank() || cleaning.isNullOrBlank()) {
            return ShortenResult.Failure(ShortenError.UNVERIFIED, httpCode)
        }
        if (processing != "completed" || resolution !in RESOLUTION_STATUSES || cleaning !in CLEANING_STATUSES) {
            return ShortenResult.Failure(ShortenError.UNVERIFIED, httpCode)
        }

        val removed = root.array("removed_parameters")
            .mapNotNull { element -> (element as? JsonObject)?.let { param ->
                val name = param.string("name") ?: return@let null
                RemovedParameter(name, param.string("value"))
            } }
        val removedCount = root.int("removed_count") ?: removed.size
        if (removedCount < 0) return ShortenResult.Failure(ShortenError.INVALID_RESPONSE, httpCode)

        return ShortenResult.Success(
            ShortenResponse(
                shortUrl = shortUrl,
                targetUrl = targetUrl,
                originalUrl = root.string("original_url"),
                resolvedUrl = root.string("resolved_url"),
                processingStatus = processing,
                resolutionStatus = resolution,
                cleaningStatus = cleaning,
                removedCount = removedCount,
                removedParameters = removed,
                warnings = root.array("warnings").mapNotNull { (it as? JsonPrimitive)?.content },
            ),
        )
    }

    private val RESOLUTION_STATUSES = setOf("not_needed", "resolved")
    private val CLEANING_STATUSES = setOf("cleaned", "unchanged", "partial")
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.int(name: String): Int? =
    (this[name] as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonObject.array(name: String): JsonArray =
    this[name] as? JsonArray ?: JsonArray(emptyList())
