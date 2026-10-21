package tw.kuies.link.data

data class ShortenResponse(
    val shortUrl: String,
    val targetUrl: String,
    val processingStatus: String,
    val resolutionStatus: String,
    val cleaningStatus: String,
    val removedCount: Int,
    val removedParams: List<String>,
    val warnings: List<String>,
    val cleaningRulesVersion: String?,
    val cleaningScope: String?,
) {
    /** The server removed at least one known tracking parameter. */
    val isCleaned: Boolean get() = cleaningStatus == STATUS_CLEANED

    /** The server has rules for this host and found nothing to remove. */
    val isUnchanged: Boolean get() = cleaningStatus == STATUS_UNCHANGED

    /**
     * The host is outside the server's cleaning policy. The short URL exists, but the target may
     * still carry tracking parameters, so the UI must not claim it was cleaned.
     */
    val isUnsupported: Boolean get() = cleaningStatus == STATUS_UNSUPPORTED

    val wasResolved: Boolean get() = resolutionStatus == RESOLVED

    val hasKnownTrackingRemoved: Boolean get() = removedCount > 0

    companion object {
        const val STATUS_CLEANED = "CLEANED"
        const val STATUS_UNCHANGED = "UNCHANGED"
        const val STATUS_UNSUPPORTED = "UNSUPPORTED"
        const val RESOLVED = "RESOLVED"

        /** cleaning_status values the deployed backend returns on a successful 201. */
        val SUCCESS_CLEANING = setOf(STATUS_CLEANED, STATUS_UNCHANGED, STATUS_UNSUPPORTED)
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
