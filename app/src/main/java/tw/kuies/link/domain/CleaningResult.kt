package tw.kuies.link.domain

enum class CleaningStatus {
    /** Tracking parameters were removed. */
    CLEANED,

    /** A usable URL without any removable tracking parameter. */
    UNCHANGED,

    /**
     * A known link shim (for example l.facebook.com/l.php?u=...) whose real destination is only
     * encoded inside a query parameter. v0.1.x does not unwrap these, so the result is not
     * silently forwarded.
     */
    UNRESOLVED_WRAPPER,

    /** The input is not a single absolute HTTP(S) URL. */
    INVALID_URL,

    /** Cleaning failed unexpectedly; the original URL is returned untouched. */
    FAILED,
}

data class RemovedParameter(
    val name: String,
    val value: String?,
    val raw: String,
    val ruleSource: String,
)

data class CleaningResult(
    val originalUrl: String,
    val cleanedUrl: String,
    val status: CleaningStatus,
    val removedParameters: List<RemovedParameter> = emptyList(),
    val host: String? = null,
) {
    val removedCount: Int get() = removedParameters.size

    val hasChange: Boolean get() = cleanedUrl != originalUrl

    val isShareable: Boolean
        get() = status == CleaningStatus.CLEANED ||
            status == CleaningStatus.UNCHANGED ||
            status == CleaningStatus.UNRESOLVED_WRAPPER

    companion object {
        fun unchanged(url: String, status: CleaningStatus, host: String?): CleaningResult =
            CleaningResult(originalUrl = url, cleanedUrl = url, status = status, host = host)
    }
}
