package tw.kuies.link.domain

sealed interface ExtractionResult {
    data class Single(val url: String) : ExtractionResult
    data class Multiple(val urls: List<String>) : ExtractionResult
    data object None : ExtractionResult
}

/**
 * Finds absolute HTTP(S) URLs inside arbitrary shared text (share sheets prepend app names,
 * captions, emoji, newlines, Markdown/HTML wrappers, Chinese punctuation, ...).
 *
 * The scanner is intentionally simple rather than a clever regular expression: it locates an
 * `http://` / `https://` token and consumes up to the next delimiter, so it cannot swallow the
 * surrounding sentence and cannot delete characters inside the URL (encoding, CJK, fragment).
 */
object UrlExtractor {
    private val scheme = Regex("https?://", RegexOption.IGNORE_CASE)
    private const val MAX_URL_LENGTH = 8192

    fun extract(text: String?): ExtractionResult {
        if (text.isNullOrBlank()) return ExtractionResult.None
        val source = text.replace("\u200B", "").replace("\uFEFF", "")

        val urls = LinkedHashSet<String>()
        var searchFrom = 0
        while (searchFrom < source.length) {
            val match = scheme.find(source, searchFrom) ?: break
            val start = match.range.first
            val afterScheme = match.range.last + 1
            if (!isBoundary(source, start)) {
                searchFrom = afterScheme
                continue
            }

            val end = scanEnd(source, afterScheme)
            val candidate = trimTrailingPunctuation(source.substring(start, end))
            searchFrom = maxOf(end, afterScheme)

            if (candidate.length > MAX_URL_LENGTH) continue
            if (HttpUrl.parse(candidate) != null) {
                urls += candidate
            }
        }

        return when (urls.size) {
            0 -> ExtractionResult.None
            1 -> ExtractionResult.Single(urls.first())
            else -> ExtractionResult.Multiple(urls.toList())
        }
    }

    /** True when "http" starts a token instead of being the tail of a longer word. */
    private fun isBoundary(source: String, start: Int): Boolean {
        if (start == 0) return true
        val previous = source[start - 1]
        return !(previous.isLetterOrDigit() || previous in "_-.+%@/")
    }

    private fun scanEnd(source: String, from: Int): Int {
        var index = from
        while (index < source.length) {
            val char = source[index]
            if (char.isWhitespace() || Character.isSpaceChar(char) || char in DELIMITERS) break
            index++
        }
        return index
    }

    private fun trimTrailingPunctuation(raw: String): String {
        var end = raw.length
        while (end > 0) {
            val char = raw[end - 1]
            val removable = when (char) {
                '.', ',', ';', ':', '!', '?', '\'', '"' -> true
                ')' -> !hasUnclosed(raw, end - 1, '(', ')')
                ']' -> !hasUnclosed(raw, end - 1, '[', ']')
                '}' -> !hasUnclosed(raw, end - 1, '{', '}')
                else -> char in WIDE_PUNCTUATION
            }
            if (!removable) break
            end--
        }
        return raw.substring(0, end)
    }

    private fun hasUnclosed(text: String, closingIndex: Int, open: Char, close: Char): Boolean {
        var opens = 0
        var closes = 0
        for (index in 0 until closingIndex) {
            when (text[index]) {
                open -> opens++
                close -> closes++
            }
        }
        return opens > closes
    }

    private const val DELIMITERS = "\"'`<>「」『』（）【】《》〈〉，。、；：！？"
    private const val WIDE_PUNCTUATION = "「」『』（）【】《》〈〉，。、；：！？…"
}
