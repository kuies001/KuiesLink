package tw.kuies.link.domain

/**
 * A single removable query parameter.
 *
 * [hosts] empty means the parameter is a share/click identifier that Meta, Google, Threads and
 * Instagram append to links on *any* host. Non-empty [hosts] entries only match the host itself or
 * one of its subdomains, so "facebook.com" matches "m.facebook.com" but never "notfacebook.com".
 *
 * This model is intentionally data-only: adding ClearURLs providers later means feeding more
 * entries (provider host + regex rules + exceptions) through a rule source, not changing the
 * cleaner. Nothing here executes remotely supplied regular expressions.
 */
data class TrackingRule(
    val parameter: String,
    val source: String,
    val hosts: Set<String> = emptySet(),
) {
    fun matchesHost(host: String): Boolean {
        if (hosts.isEmpty()) return true
        return hosts.any { host == it || host.endsWith(".$it") }
    }
}

open class TrackingRulesRepository(
    private val rules: List<TrackingRule> = DEFAULT_RULES,
) {
    open fun ruleFor(host: String, parameterName: String): TrackingRule? {
        if (parameterName.isEmpty()) return null
        if (ProtectedParameters.contains(parameterName.lowercase())) return null
        return rules.firstOrNull {
            it.parameter.equals(parameterName, ignoreCase = true) && it.matchesHost(host)
        }
    }

    companion object {
        val DEFAULT_RULES: List<TrackingRule> = listOf(
            TrackingRule("utm_source", "UTM"),
            TrackingRule("utm_medium", "UTM"),
            TrackingRule("utm_campaign", "UTM"),
            TrackingRule("utm_term", "UTM"),
            TrackingRule("utm_content", "UTM"),
            TrackingRule("utm_id", "UTM"),
            TrackingRule("gclid", "Google Ads"),
            TrackingRule("dclid", "Google Ads"),
            TrackingRule("msclkid", "Microsoft Ads"),
            TrackingRule("fbclid", "Meta"),
            TrackingRule("mibextid", "Meta"),
            TrackingRule("rdid", "Meta"),
            TrackingRule("xmt", "Threads"),
            TrackingRule("slof", "Threads"),
            TrackingRule("igsh", "Instagram"),
            TrackingRule("igshid", "Instagram"),
            // Facebook's own share link carries a functional target in share_url.
            TrackingRule("share_url", "Facebook", hosts = setOf("facebook.com", "fb.com", "fb.watch")),
        )
    }
}

/**
 * Parameters that must survive even if a future rule is written too broadly: media ids, playlist
 * ids and comment targets are required to open the intended content.
 */
internal object ProtectedParameters {
    private val names = setOf(
        "v",
        "list",
        "t",
        "start",
        "index",
        "story_fbid",
        "id",
        "comment_id",
        "img_index",
        "si",
        "feature",
    )

    fun contains(lowercaseName: String): Boolean = names.contains(lowercaseName)
}
