package tw.kuies.link.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCleanerTest {

    private val cleaner = UrlCleaner()

    @Test
    fun instagramIgshRemoved() {
        val result = cleaner.clean("https://www.instagram.com/p/CxYz123/?igsh=MW9xZ2F0")

        assertEquals(CleaningStatus.CLEANED, result.status)
        assertEquals("https://www.instagram.com/p/CxYz123/", result.cleanedUrl)
        assertEquals(listOf("igsh"), result.removedParameters.map { it.name })
        assertEquals("MW9xZ2F0", result.removedParameters.single().value)
        assertEquals("www.instagram.com", result.host)
    }

    @Test
    fun threadsXmtAndSlofRemoved() {
        val result = cleaner.clean(
            "https://www.threads.net/@someone/post/AbC?xmt=AQG123&slof=1&utm_source=threads",
        )

        assertEquals("https://www.threads.net/@someone/post/AbC", result.cleanedUrl)
        assertEquals(3, result.removedCount)
        assertEquals(listOf("xmt", "slof", "utm_source"), result.removedParameters.map { it.name })
    }

    @Test
    fun facebookFbclidRemovedOnSharedHost() {
        val result = cleaner.clean("https://news.example.com/article?fbclid=IwAR123&id=42")

        assertEquals("https://news.example.com/article?id=42", result.cleanedUrl)
        assertEquals(listOf("fbclid"), result.removedParameters.map { it.name })
    }

    @Test
    fun utmParametersRemoved() {
        val result = cleaner.clean(
            "https://shop.example.com/item?utm_source=newsletter&utm_medium=email" +
                "&utm_campaign=spring&utm_term=shoes&utm_content=banner&utm_id=9&sku=123",
        )

        assertEquals("https://shop.example.com/item?sku=123", result.cleanedUrl)
        assertEquals(6, result.removedCount)
    }

    @Test
    fun youtubeParametersAreKept() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123&t=42s&si=abc"
        val result = cleaner.clean(url)

        assertEquals(CleaningStatus.UNCHANGED, result.status)
        assertEquals(url, result.cleanedUrl)
        assertTrue(result.removedParameters.isEmpty())
    }

    @Test
    fun fragmentIsPreserved() {
        val result = cleaner.clean(
            "https://example.com/page?utm_source=news&utm_medium=email#section-2",
        )

        assertEquals("https://example.com/page#section-2", result.cleanedUrl)
    }

    @Test
    fun duplicateParametersAreHandledIndividually() {
        val result = cleaner.clean(
            "https://example.com/p?utm_source=a&utm_source=b&id=1&id=2&utm_medium=c",
        )

        assertEquals("https://example.com/p?id=1&id=2", result.cleanedUrl)
        assertEquals(3, result.removedCount)
        assertEquals(
            listOf("a", "b", "c"),
            result.removedParameters.map { it.value },
        )
    }

    @Test
    fun nonTrackingParametersAreKeptInOrder() {
        val result = cleaner.clean(
            "https://example.com/search?q=shoes&color=red&utm_source=fb&size=42&ref=home",
        )

        assertEquals("https://example.com/search?q=shoes&color=red&size=42&ref=home", result.cleanedUrl)
    }

    @Test
    fun percentEncodingAndChineseArePreserved() {
        val result = cleaner.clean(
            "https://example.com/搜尋?q=%E4%B8%AD%E6%96%87&utm_campaign=spring#片段-1",
        )

        assertEquals("https://example.com/搜尋?q=%E4%B8%AD%E6%96%87#片段-1", result.cleanedUrl)
        assertEquals("spring", result.removedParameters.single().value)
    }

    @Test
    fun cleanUrlIsNotChangedAtAll() {
        val url = "https://example.com/a/b?x=1&y=2#top"
        val result = cleaner.clean(url)

        assertEquals(CleaningStatus.UNCHANGED, result.status)
        assertEquals(url, result.cleanedUrl)
        assertEquals(0, result.removedCount)
    }

    @Test
    fun malformedInputDoesNotCrash() {
        val inputs = listOf(
            "",
            "   ",
            "hello world",
            "https://",
            "http://?a=1",
            "https:///path",
            "https://exa mple.com/?utm_source=1",
            "%%%",
        )

        inputs.forEach { input ->
            val result = cleaner.clean(input)
            assertEquals("input: <$input>", CleaningStatus.INVALID_URL, result.status)
            assertEquals("input: <$input>", input, result.cleanedUrl)
        }
    }

    @Test
    fun nonHttpSchemesAreRejected() {
        val inputs = listOf(
            "javascript:alert(1)",
            "file:///etc/passwd?utm_source=1",
            "content://media/external/images/1",
            "ftp://example.com/?utm_source=x",
            "intent://scan/#Intent;scheme=zxing;end",
        )

        inputs.forEach { input ->
            val result = cleaner.clean(input)
            assertEquals("input: <$input>", CleaningStatus.INVALID_URL, result.status)
            assertEquals("input: <$input>", input, result.cleanedUrl)
        }
    }

    @Test
    fun parameterNamesMatchCaseInsensitively() {
        val result = cleaner.clean("https://example.com/?UTM_Source=fb&Utm_Medium=cpc&Keep=1")

        assertEquals("https://example.com/?Keep=1", result.cleanedUrl)
        assertEquals(2, result.removedCount)
    }

    @Test
    fun hostSpecificRuleRespectsDomainBoundary() {
        assertEquals(
            "https://m.facebook.com/story.php?story_fbid=1",
            cleaner.clean("https://m.facebook.com/story.php?share_url=x&story_fbid=1").cleanedUrl,
        )
        assertEquals(
            "https://notfacebook.com/?share_url=x",
            cleaner.clean("https://notfacebook.com/?share_url=x").cleanedUrl,
        )
        assertEquals(
            "https://facebook.com.evil.example/?share_url=x",
            cleaner.clean("https://facebook.com.evil.example/?share_url=x").cleanedUrl,
        )
    }

    @Test
    fun sensitiveUrlOnlyLosesUnambiguousTrackers() {
        val result = cleaner.clean(
            "https://www.facebook.com/dialog/oauth?share_url=abc&client_id=1&code=xyz&utm_source=fb",
        )

        assertEquals(
            "https://www.facebook.com/dialog/oauth?share_url=abc&client_id=1&code=xyz",
            result.cleanedUrl,
        )
        assertEquals(listOf("utm_source"), result.removedParameters.map { it.name })
    }

    @Test
    fun failureReturnsTheOriginalUrlUntouched() {
        val brokenRules = object : TrackingRulesRepository() {
            override fun ruleFor(host: String, parameterName: String): TrackingRule? =
                error("simulated failure")
        }
        val input = "https://example.com/?utm_source=1"

        val result = UrlCleaner(brokenRules).clean(input)

        assertEquals(CleaningStatus.FAILED, result.status)
        assertEquals(input, result.cleanedUrl)
        assertTrue(result.removedParameters.isEmpty())
    }

    @Test
    fun cleaningIsIdempotent() {
        val first = cleaner.clean("https://a.example/?utm_source=x&igsh=y&id=1#frag")
        val second = cleaner.clean(first.cleanedUrl)

        assertEquals(first.cleanedUrl, second.cleanedUrl)
        assertEquals(CleaningStatus.UNCHANGED, second.status)
        assertEquals(0, second.removedCount)
    }

    @Test
    fun instagramReelExlnIsRemoved() {
        val result = cleaner.clean("https://www.instagram.com/reel/DcClyQgikR4/?exln=Yjg5MXAyMzZid2l0")

        assertEquals(CleaningStatus.CLEANED, result.status)
        assertEquals("https://www.instagram.com/reel/DcClyQgikR4/", result.cleanedUrl)
        assertEquals(listOf("exln"), result.removedParameters.map { it.name })
        assertEquals("Yjg5MXAyMzZid2l0", result.removedParameters.single().value)
        assertEquals("Instagram", result.removedParameters.single().ruleSource)
    }

    @Test
    fun instagramReelExlnAndIgshAreBothRemoved() {
        val result = cleaner.clean(
            "https://www.instagram.com/reel/DcClyQgikR4/?exln=Yjg5MXAy&igsh=MW9xZ2F0&img_index=2",
        )

        assertEquals("https://www.instagram.com/reel/DcClyQgikR4/?img_index=2", result.cleanedUrl)
        assertEquals(2, result.removedCount)
        assertEquals(listOf("exln", "igsh"), result.removedParameters.map { it.name })
    }

    @Test
    fun exlnIsNotRemovedOnOtherWebsites() {
        val url = "https://shop.example.com/item?exln=abc&sku=1"
        val result = cleaner.clean(url)

        assertEquals(CleaningStatus.UNCHANGED, result.status)
        assertEquals(url, result.cleanedUrl)
        assertEquals(0, result.removedCount)
    }

    @Test
    fun instagramKeepsItsFunctionalParameters() {
        val result = cleaner.clean(
            "https://www.instagram.com/p/CxYz123/?img_index=2&exln=token&hl=zh-tw",
        )

        assertEquals("https://www.instagram.com/p/CxYz123/?img_index=2&hl=zh-tw", result.cleanedUrl)
        assertEquals(listOf("exln"), result.removedParameters.map { it.name })
    }

    @Test
    fun instagramLookalikeHostsAreNotMatched() {
        val lookalikes = listOf(
            "https://fakeinstagram.com/reel/X/?exln=1",
            "https://instagram.com.evil.example/reel/X/?exln=1",
            "https://notinstagram.com/?exln=1",
        )

        lookalikes.forEach { url ->
            val result = cleaner.clean(url)
            assertEquals("url: <$url>", CleaningStatus.UNCHANGED, result.status)
            assertEquals("url: <$url>", url, result.cleanedUrl)
        }
    }

    @Test
    fun instagramExlnCleaningIsIdempotent() {
        val first = cleaner.clean("https://www.instagram.com/reel/DcClyQgikR4/?exln=Yjg5#comments")
        val second = cleaner.clean(first.cleanedUrl)

        assertEquals("https://www.instagram.com/reel/DcClyQgikR4/#comments", first.cleanedUrl)
        assertEquals(first.cleanedUrl, second.cleanedUrl)
        assertEquals(CleaningStatus.UNCHANGED, second.status)
        assertEquals(0, second.removedCount)
    }

    @Test
    fun facebookLinkShimIsReportedAsUnresolvedWrapper() {
        val url = "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fpost&h=AT1"
        val result = cleaner.clean(url)

        assertEquals(CleaningStatus.UNRESOLVED_WRAPPER, result.status)
        assertEquals(url, result.cleanedUrl)
        assertTrue(result.removedParameters.isEmpty())
    }

    @Test
    fun instagramLinkShimIsReportedAsUnresolvedWrapper() {
        val url = "https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2F&e=abc"
        val result = cleaner.clean(url)

        assertEquals(CleaningStatus.UNRESOLVED_WRAPPER, result.status)
        assertEquals(url, result.cleanedUrl)
    }

    @Test
    fun unchangedUrlIsNotMistakenForAnUnresolvedWrapper() {
        assertEquals(
            CleaningStatus.UNCHANGED,
            cleaner.clean("https://example.com/article?id=1").status,
        )
        // l.facebook.com, but not the /l.php shim path.
        assertEquals(
            CleaningStatus.UNCHANGED,
            cleaner.clean("https://l.facebook.com/help?u=x").status,
        )
        // The shim path on an unrelated host.
        assertEquals(
            CleaningStatus.UNCHANGED,
            cleaner.clean("https://example.com/l.php?u=x").status,
        )
    }
}
