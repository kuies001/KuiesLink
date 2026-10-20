package tw.kuies.link.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun extractsUrlFromSurroundingText() {
        val text = "推薦這篇文章 https://example.com/article?utm_source=igsh 很好看"

        assertEquals(
            ExtractionResult.Single("https://example.com/article?utm_source=igsh"),
            UrlExtractor.extract(text),
        )
    }

    @Test
    fun extractsUrlSeparatedByNewlines() {
        val text = "Threads\nhttps://www.threads.net/@a/post/b?xmt=1\n分享"

        assertEquals(
            ExtractionResult.Single("https://www.threads.net/@a/post/b?xmt=1"),
            UrlExtractor.extract(text),
        )
    }

    @Test
    fun reportsMultipleUrlsWithoutChoosingOne() {
        val text = "https://a.example/1\nhttps://b.example/2?x=1"

        assertEquals(
            ExtractionResult.Multiple(listOf("https://a.example/1", "https://b.example/2?x=1")),
            UrlExtractor.extract(text),
        )
    }

    @Test
    fun returnsNoneWhenTextHasNoUrl() {
        assertEquals(ExtractionResult.None, UrlExtractor.extract(null))
        assertEquals(ExtractionResult.None, UrlExtractor.extract(""))
        assertEquals(ExtractionResult.None, UrlExtractor.extract("今天天氣很好，沒有連結"))
    }

    @Test
    fun rejectsNonHttpSchemes() {
        assertEquals(ExtractionResult.None, UrlExtractor.extract("javascript:alert(1)"))
        assertEquals(ExtractionResult.None, UrlExtractor.extract("file:///etc/passwd"))
        assertEquals(ExtractionResult.None, UrlExtractor.extract("content://media/external/1"))
        assertEquals(ExtractionResult.None, UrlExtractor.extract("mailto:someone@example.com"))
    }

    @Test
    fun stripsWrappingChinesePunctuation() {
        assertEquals(
            ExtractionResult.Single("https://example.com/a"),
            UrlExtractor.extract("（https://example.com/a）"),
        )
        assertEquals(
            ExtractionResult.Single("https://example.com/a"),
            UrlExtractor.extract("看這裡：https://example.com/a。"),
        )
    }

    @Test
    fun keepsBalancedParenthesesInsideUrl() {
        val url = "https://zh.wikipedia.org/wiki/Foo_(bar)"

        assertEquals(ExtractionResult.Single(url), UrlExtractor.extract("連結 $url 謝謝"))
    }

    @Test
    fun handlesMarkdownAndHtmlWrapping() {
        assertEquals(
            ExtractionResult.Single("https://example.com/a?b=1"),
            UrlExtractor.extract("[文章](https://example.com/a?b=1)"),
        )
        assertEquals(
            ExtractionResult.Single("https://example.com/a?b=1"),
            UrlExtractor.extract("<a href=\"https://example.com/a?b=1\">x</a>"),
        )
    }

    @Test
    fun preservesFragmentAndChinesePath() {
        val url = "https://zh.wikipedia.org/wiki/台北#歷史"

        assertEquals(ExtractionResult.Single(url), UrlExtractor.extract("看看 $url 這頁"))
    }

    @Test
    fun duplicateUrlsAreCollapsed() {
        assertEquals(
            ExtractionResult.Single("https://a.example/1"),
            UrlExtractor.extract("https://a.example/1 和 https://a.example/1"),
        )
    }

    @Test
    fun encodedUrlStaysEncoded() {
        val url = "https://example.com/s?q=%E4%B8%AD%E6%96%87&r=https%3A%2F%2Fb.example%2F"

        assertEquals(ExtractionResult.Single(url), UrlExtractor.extract(url))
    }

    @Test
    fun doesNotExtractNestedUrlFromQueryParameter() {
        val url = "https://example.com/redirect?next=https://b.example/2"

        assertEquals(ExtractionResult.Single(url), UrlExtractor.extract(url))
    }

    @Test
    fun trimsTrailingSentencePunctuation() {
        assertEquals(
            ExtractionResult.Single("https://example.com/a"),
            UrlExtractor.extract("https://example.com/a, 然後"),
        )
        assertEquals(
            ExtractionResult.Single("https://example.com/a?b=1"),
            UrlExtractor.extract("https://example.com/a?b=1."),
        )
    }

    @Test
    fun ignoresSchemeThatIsPartOfALongerToken() {
        assertEquals(ExtractionResult.None, UrlExtractor.extract("nothttps://example.com"))
    }
}
