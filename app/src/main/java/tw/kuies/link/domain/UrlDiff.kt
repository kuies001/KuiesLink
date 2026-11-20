package tw.kuies.link.domain

/** How much of the original URL can be compared with the cleaned target. */
enum class UrlDiffAvailability {
    /** Both URLs share scheme, authority, path and fragment, so parameters can be compared. */
    PARAMETER_LEVEL,

    /** A wrapper was resolved or the destination changed, so per-parameter comparison is unsafe. */
    STRUCTURAL,

    /** At least one URL could not be parsed. */
    UNAVAILABLE,
}

/** A contiguous piece of the original URL. Removed pieces are highlighted, never string-replaced. */
data class UrlDiffSegment(
    val text: String,
    val removed: Boolean,
)

enum class RemovalReason {
    /** A local cleaning rule matched the parameter name. */
    RULE,

    /** The backend reported the parameter but did not send a per-parameter reason. */
    SERVER_REPORTED,
}

data class UrlParameterDetail(
    val name: String,
    val value: String?,
    val hasValue: Boolean,
    /** False when only the name is known (for example a resolved wrapper's server report). */
    val valueKnown: Boolean = true,
    val repeated: Boolean,
    val ruleSource: String?,
    val removalReason: RemovalReason? = null,
)

data class UrlDiff(
    val availability: UrlDiffAvailability,
    val originalUrl: String,
    val cleanedUrl: String,
    val originalSegments: List<UrlDiffSegment>,
    val removed: List<UrlParameterDetail>,
    val kept: List<UrlParameterDetail>,
    val baseChanged: Boolean,
    val fragmentChanged: Boolean = false,
) {
    val hasRemoved: Boolean get() = removed.isNotEmpty()
}

/**
 * Compares a URL before and after cleaning without rewriting either string.
 *
 * Removed occurrences are found by comparing the parsed query parameters as a multiset, so
 * duplicate names, empty values, re-encoded values and a changed parameter order are handled
 * without splitting URLs on '?' or replacing text. Path, authority, fragment and the original
 * encoding are never modified.
 */
object UrlDiffAnalyzer {

    fun compare(
        originalUrl: String,
        cleanedUrl: String,
        localRemoved: List<RemovedParameter> = emptyList(),
    ): UrlDiff {
        val original = HttpUrl.parse(originalUrl)
        val cleaned = HttpUrl.parse(cleanedUrl)
        if (original == null || cleaned == null) {
            return UrlDiff(
                availability = UrlDiffAvailability.UNAVAILABLE,
                originalUrl = originalUrl,
                cleanedUrl = cleanedUrl,
                originalSegments = listOf(UrlDiffSegment(originalUrl, removed = false)),
                removed = emptyList(),
                kept = emptyList(),
                baseChanged = false,
            )
        }

        if (!sameLocation(original, cleaned)) {
            return UrlDiff(
                availability = UrlDiffAvailability.STRUCTURAL,
                originalUrl = originalUrl,
                cleanedUrl = cleanedUrl,
                originalSegments = listOf(UrlDiffSegment(originalUrl, removed = false)),
                removed = emptyList(),
                kept = emptyList(),
                baseChanged = true,
                fragmentChanged = fragmentsDiffer(original, cleaned),
            )
        }

        val fragmentChanged = fragmentsDiffer(original, cleaned)

        val remainingCleaned = cleaned.parameters
            .groupingBy { it.key() }
            .eachCount()
            .toMutableMap()
        val removedIndices = mutableSetOf<Int>()

        original.parameters.forEachIndexed { index, parameter ->
            val key = parameter.key()
            val remaining = remainingCleaned[key] ?: 0
            if (remaining > 0) {
                remainingCleaned[key] = remaining - 1
            } else {
                removedIndices += index
            }
        }

        val localPool = localRemoved.toMutableList()
        val removedRepeatedNames = original.parameters
            .groupingBy { it.name() }
            .eachCount()
        val keptRepeatedNames = cleaned.parameters
            .groupingBy { it.name() }
            .eachCount()

        val removed = original.parameters
            .filterIndexed { index, _ -> index in removedIndices }
            .map { parameter ->
                val local = localPool.removeFirstMatch(parameter)
                UrlParameterDetail(
                    name = parameter.name(),
                    value = parameter.value(),
                    hasValue = parameter.rawValue != null,
                    repeated = (removedRepeatedNames[parameter.name()] ?: 0) > 1,
                    ruleSource = local?.ruleSource,
                    removalReason = if (local != null) {
                        RemovalReason.RULE
                    } else {
                        RemovalReason.SERVER_REPORTED
                    },
                )
            }

        val kept = cleaned.parameters.map { parameter ->
            UrlParameterDetail(
                name = parameter.name(),
                value = parameter.value(),
                hasValue = parameter.rawValue != null,
                repeated = (keptRepeatedNames[parameter.name()] ?: 0) > 1,
                ruleSource = null,
                removalReason = null,
            )
        }

        return UrlDiff(
            availability = UrlDiffAvailability.PARAMETER_LEVEL,
            originalUrl = originalUrl,
            cleanedUrl = cleanedUrl,
            originalSegments = highlightSegments(originalUrl, removedIndices),
            removed = removed,
            kept = kept,
            baseChanged = false,
            fragmentChanged = fragmentChanged,
        )
    }

    private fun sameLocation(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme.equals(b.scheme, ignoreCase = true) &&
            a.authority.equals(b.authority, ignoreCase = true) &&
            a.path == b.path

    private fun fragmentsDiffer(a: HttpUrl, b: HttpUrl): Boolean =
        a.hasFragment != b.hasFragment || a.fragment != b.fragment

    private fun QueryParameter.name(): String =
        PercentCodec.decode(rawName, plusAsSpace = false)

    private fun QueryParameter.value(): String? =
        rawValue?.let { PercentCodec.decode(it, plusAsSpace = true) }

    private data class ParameterKey(
        val name: String,
        val value: String?,
    )

    private fun QueryParameter.key(): ParameterKey = ParameterKey(name(), value())

    private fun MutableList<RemovedParameter>.removeFirstMatch(
        parameter: QueryParameter,
    ): RemovedParameter? {
        val index = indexOfFirst { candidate ->
            candidate.raw == parameter.raw ||
                (
                    candidate.name.equals(parameter.name(), ignoreCase = true) &&
                        candidate.value == parameter.value()
                    )
        }
        return if (index >= 0) removeAt(index) else null
    }

    /**
     * Splits the original string at the actual '&' boundaries so the highlighted text is the
     * original encoding, not a rebuilt URL. Empty query segments stay in the plain text.
     */
    private fun highlightSegments(
        raw: String,
        removedIndices: Set<Int>,
    ): List<UrlDiffSegment> {
        val fragmentIndex = raw.indexOf('#').let { if (it == -1) raw.length else it }
        val queryIndex = raw.indexOf('?')
        if (queryIndex == -1 || queryIndex > fragmentIndex) {
            return listOf(UrlDiffSegment(raw, removed = false))
        }

        val segments = mutableListOf<UrlDiffSegment>()
        fun plain(text: String) {
            if (text.isNotEmpty()) segments += UrlDiffSegment(text, removed = false)
        }

        plain(raw.substring(0, queryIndex + 1))

        var cursor = queryIndex + 1
        var parameterIndex = 0
        var segmentStart = queryIndex + 1
        while (segmentStart <= fragmentIndex) {
            val ampersand = raw.indexOf('&', segmentStart)
                .let { if (it == -1 || it > fragmentIndex) fragmentIndex else it }
            if (segmentStart < ampersand) {
                plain(raw.substring(cursor, segmentStart))
                segments += UrlDiffSegment(
                    text = raw.substring(segmentStart, ampersand),
                    removed = parameterIndex in removedIndices,
                )
                parameterIndex++
                cursor = ampersand
            }
            if (ampersand >= fragmentIndex) break
            segmentStart = ampersand + 1
        }
        plain(raw.substring(cursor))
        return segments
    }
}
