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

        // A wrapper hides its real destination; never forward it silently as if it were cleaned.
        if (isUnresolvedWrapper(url)) {
            return CleaningResult.unchanged(candidate, CleaningStatus.UNRESOLVED_WRAPPER, url.host)
        }

        if (isInstagramReel(url) && url.parameters.isNotEmpty()) {
            return clearInstagramReelQuery(candidate, url)
        }

        if (url.parameters.isEmpty()) {
            return CleaningResult.unchanged(candidate, CleaningStatus.UNCHANGED, url.host)
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
            return CleaningResult.unchanged(candidate, CleaningStatus.UNCHANGED, url.host)
        }

        return CleaningResult(
            originalUrl = candidate,
            cleanedUrl = url.rebuild(kept),
            status = CleaningStatus.CLEANED,
            removedParameters = removed,
            host = url.host,
        )
    }

    /**
     * Recognises the small set of share shims that carry the real destination only inside a query
     * parameter. Only these exact shapes qualify: an unchanged ordinary URL stays UNCHANGED.
     */
    private fun isUnresolvedWrapper(url: HttpUrl): Boolean {
        val hasTargetParameter = url.parameters.any {
            PercentCodec.decode(it.rawName, plusAsSpace = false).equals("u", ignoreCase = true)
        }
        return when (url.host) {
            "l.facebook.com", "lm.facebook.com" -> url.path == "/l.php" && hasTargetParameter
            "l.instagram.com" -> hasTargetParameter
            else -> false
        }
    }

    /**
     * Instagram Reel share links: the reel is identified entirely by `/reel/{id}` in the path, so
     * every query parameter on that canonical path is a share artefact. Instagram keeps inventing
     * new share tokens (igsh, exln, vrfl, ...), so the whole query is dropped instead of chasing
     * names. Other Instagram paths (/p/, /stories/, /accounts/, /direct/) keep the conservative
     * name-based rules.
     *
     * The path must be exactly `/reel/{id}` or `/reel/{id}/`: extra path segments do not qualify.
     * Query placement, fragment separation and encoding are handled by [HttpUrl], never by
     * splitting the raw string on '?'.
     */
    private fun isInstagramReel(url: HttpUrl): Boolean {
        if (url.host != "instagram.com" && !url.host.endsWith(".instagram.com")) return false
        if (!url.path.startsWith("/reel/")) return false

        val remainder = url.path.removePrefix("/reel/")
        val id = remainder.removeSuffix("/")
        return id.isNotEmpty() && id.none { it == '/' } && REEL_ID_PATTERN.matches(id)
    }

    private fun clearInstagramReelQuery(candidate: String, url: HttpUrl): CleaningResult {
        val removed = url.parameters.map { parameter ->
            RemovedParameter(
                name = PercentCodec.decode(parameter.rawName, plusAsSpace = false),
                value = parameter.rawValue?.let { PercentCodec.decode(it, plusAsSpace = true) },
                raw = parameter.raw,
                ruleSource = "Instagram Reel",
            )
        }
        return CleaningResult(
            originalUrl = candidate,
            cleanedUrl = url.rebuild(emptyList()),
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
        /** Shortcodes are alphanumeric; a generous length range still rejects junk. */
        val REEL_ID_PATTERN = Regex("[A-Za-z0-9_-]{3,64}")

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
