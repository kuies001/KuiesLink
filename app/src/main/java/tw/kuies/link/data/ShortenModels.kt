package tw.kuies.link.data

data class RemovedParameter(
    val name: String,
    val value: String?,
)

data class ShortenResponse(
    val shortUrl: String,
    val targetUrl: String,
    val originalUrl: String?,
    val resolvedUrl: String?,
    val processingStatus: String,
    val resolutionStatus: String,
    val cleaningStatus: String,
    val removedCount: Int,
    val removedParameters: List<RemovedParameter>,
    val warnings: List<String>,
) {
    val isCompleted: Boolean get() = processingStatus == "completed"

    /** Server-side evidence exists; never infer this from HTTP 201 alone. */
    val isVerifiedClean: Boolean
        get() = processingStatus == "completed" && cleaningStatus in VERIFIED_CLEANING

    val wasResolved: Boolean get() = resolutionStatus == "resolved"

    val hasKnownTrackingRemoved: Boolean get() = removedCount > 0

    val isPartial: Boolean get() = cleaningStatus == "partial"

    companion object {
        val VERIFIED_CLEANING = setOf("cleaned", "unchanged")
    }
}

sealed interface ShortenResult {
    data class Success(val response: ShortenResponse) : ShortenResult

    data class Failure(
        val error: ShortenError,
        val httpCode: Int? = null,
    ) : ShortenResult
}

enum class ShortenError {
    INVALID_REQUEST,
    WRAPPER_UNRESOLVED,
    RATE_LIMITED,
    SERVER_ERROR,
    NETWORK,
    TIMEOUT,
    TLS,
    INVALID_RESPONSE,
    UNVERIFIED,
    CANCELLED,
}
