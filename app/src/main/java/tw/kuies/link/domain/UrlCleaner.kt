package tw.kuies.link.domain

/**
 * Stateless, offline URL cleaner.
 *
 * Guarantees:
 *  - never throws: any failure returns the input untouched with [CleaningStatus.FAILED];
 *  - the path, fragment, authority, parameter order and original encoding are preserved;
 *  - a parameter is only dropped when the rule repository returns a matching rule;
 *  - URLs carrying functional/sensitive parameters (tokens, codes, signatures, ...) only lose
 *    unambiguous cross-site trackers, never host-scoped rules.
 */
class UrlCleaner(
    private val rules: TrackingRulesRepository = TrackingRulesRepository(),
) {
    fun clean(input: String): CleaningResult = try {
        cleanInternal(input)
    } catch (error: Exception) {
        CleaningResult.unchanged(input, CleaningStatus.FAILED, host = null)
    }

    private fun cleanInternal(input: String): CleaningResult {
        val candidate = input.trim()
        if (candidate.isEmpty()) {
            return CleaningResult.unchanged(input, CleaningStatus.INVALID_URL, host = null)
        }

        val url = HttpUrl.parse(candidate)
            ?: return CleaningResult.unchanged(input, CleaningStatus.INVALID_URL, host = null)

        if (url.parameters.isEmpty()) {
            return CleaningResult.unchanged(candidate, CleaningStatus.ALREADY_CLEAN, url.host)
        }

        val containsSensitiveParameter = url.parameters.any { isSensitiveParameter(it.rawName) }
        val kept = ArrayList<QueryParameter>(url.parameters.size)
        val removed = ArrayList<RemovedParameter>()

        url.parameters.forEach { parameter ->
            val name = PercentCodec.decode(parameter.rawName, plusAsSpace = false)
            val rule = rules.ruleFor(url.host, name)
            val hostScoped = rule != null && rule.hosts.isNotEmpty()
            val removable = rule != null && !(containsSensitiveParameter && hostScoped)

            if (removable) {
                removed += RemovedParameter(
                    name = name,
                    value = parameter.rawValue?.let { PercentCodec.decode(it, plusAsSpace = true) },
                    raw = parameter.raw,
                    ruleSource = rule?.source.orEmpty(),
                )
            } else {
                kept += parameter
            }
        }

        if (removed.isEmpty()) {
            return CleaningResult.unchanged(candidate, CleaningStatus.ALREADY_CLEAN, url.host)
        }

        return CleaningResult(
            originalUrl = candidate,
            cleanedUrl = url.rebuild(kept),
            status = CleaningStatus.CLEANED,
            removedParameters = removed,
            host = url.host,
        )
    }

    private fun isSensitiveParameter(rawName: String): Boolean {
        val name = PercentCodec.decode(rawName, plusAsSpace = false).lowercase()
        return SENSITIVE_NAME_HINTS.any { name.contains(it) }
    }

    private companion object {
        val SENSITIVE_NAME_HINTS = listOf(
            "token",
            "secret",
            "password",
            "passwd",
            "signature",
            "session",
            "auth",
            "code",
            "key",
            "sig",
            "expires",
            "nonce",
            "ticket",
            "coupon",
            "promo",
            "discount",
            "invite",
            "checkout",
            "payment",
            "order",
        )
    }
}
