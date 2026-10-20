package tw.kuies.link.domain

/**
 * Minimal, allocation-light HTTP(S) URL parser.
 *
 * It deliberately does not use [java.net.URI]: shared links may contain raw CJK characters or
 * percent escapes that URI rejects, and rebuilding through URI would re-encode the parts we must
 * keep byte-for-byte. Everything except the host is kept as the original text.
 */
internal data class QueryParameter(
    val rawName: String,
    val rawValue: String?,
    val raw: String,
)

internal data class HttpUrl(
    val scheme: String,
    val authority: String,
    val host: String,
    val path: String,
    val parameters: List<QueryParameter>,
    val hasFragment: Boolean,
    val fragment: String,
) {
    fun rebuild(parameters: List<QueryParameter>): String = buildString {
        append(scheme).append("://").append(authority).append(path)
        if (parameters.isNotEmpty()) {
            append('?')
            parameters.forEachIndexed { index, parameter ->
                if (index > 0) append('&')
                append(parameter.raw)
            }
        }
        if (hasFragment) append('#').append(fragment)
    }

    companion object {
        fun parse(raw: String): HttpUrl? {
            if (raw.isEmpty() || raw.any { it.isWhitespace() || Character.isSpaceChar(it) }) return null
            if (raw.any { it.isISOControl() }) return null

            val colon = raw.indexOf(':')
            if (colon <= 0) return null
            val scheme = raw.substring(0, colon)
            if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
                return null
            }

            val authorityStart = colon + 1
            if (!raw.startsWith("//", authorityStart)) return null

            val authorityEnd = raw.indexOfAny(charArrayOf('/', '?', '#'), startIndex = authorityStart + 2)
                .let { if (it == -1) raw.length else it }
            if (authorityEnd == authorityStart + 2) return null

            val authority = raw.substring(authorityStart + 2, authorityEnd)
            val host = extractHost(authority) ?: return null

            val rest = raw.substring(authorityEnd)
            val fragmentIndex = rest.indexOf('#')
            val beforeFragment = if (fragmentIndex == -1) rest else rest.substring(0, fragmentIndex)
            val hasFragment = fragmentIndex != -1
            val fragment = if (hasFragment) rest.substring(fragmentIndex + 1) else ""

            val queryIndex = beforeFragment.indexOf('?')
            val path = if (queryIndex == -1) beforeFragment else beforeFragment.substring(0, queryIndex)
            val query = if (queryIndex == -1) "" else beforeFragment.substring(queryIndex + 1)

            return HttpUrl(
                scheme = scheme,
                authority = authority,
                host = host,
                path = path,
                parameters = parseParameters(query),
                hasFragment = hasFragment,
                fragment = fragment,
            )
        }

        private fun extractHost(authority: String): String? {
            val hostPort = authority.substringAfterLast('@')
            if (hostPort.isEmpty()) return null

            val host = if (hostPort.startsWith("[")) {
                val closing = hostPort.indexOf(']')
                if (closing <= 1) return null
                hostPort.substring(0, closing + 1)
            } else {
                val portSeparator = hostPort.lastIndexOf(':')
                if (portSeparator != -1 && hostPort.substring(portSeparator + 1).all { it.isDigit() }) {
                    hostPort.substring(0, portSeparator)
                } else {
                    hostPort
                }
            }.lowercase()

            if (host.isEmpty() || host.contains(' ') || host.contains('@')) return null
            if (host.any { it in "\"'<>`{}|\\^" }) return null
            return host
        }

        private fun parseParameters(query: String): List<QueryParameter> {
            if (query.isEmpty()) return emptyList()
            return query.split('&').mapNotNull { segment ->
                if (segment.isEmpty()) return@mapNotNull null
                val equals = segment.indexOf('=')
                if (equals == -1) {
                    QueryParameter(rawName = segment, rawValue = null, raw = segment)
                } else {
                    QueryParameter(
                        rawName = segment.substring(0, equals),
                        rawValue = segment.substring(equals + 1),
                        raw = segment,
                    )
                }
            }
        }
    }
}

internal object PercentCodec {
    private val hexDigits = "0123456789abcdefABCDEF"

    /** Decodes %XX sequences; malformed or truncated sequences are kept verbatim. */
    fun decode(input: String, plusAsSpace: Boolean): String {
        if (!input.contains('%') && !(plusAsSpace && input.contains('+'))) return input
        val out = StringBuilder(input.length)
        val bytes = ArrayList<Byte>(4)
        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (char == '%' && index + 2 < input.length &&
                hexDigits.indexOf(input[index + 1]) >= 0 && hexDigits.indexOf(input[index + 2]) >= 0
            ) {
                bytes.add(input.substring(index + 1, index + 3).toInt(16).toByte())
                index += 3
                continue
            }
            flush(bytes, out)
            if (plusAsSpace && char == '+') out.append(' ') else out.append(char)
            index++
        }
        flush(bytes, out)
        return out.toString()
    }

    private fun flush(bytes: MutableList<Byte>, out: StringBuilder) {
        if (bytes.isEmpty()) return
        out.append(bytes.toByteArray().toString(Charsets.UTF_8))
        bytes.clear()
    }
}
