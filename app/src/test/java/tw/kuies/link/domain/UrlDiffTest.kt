package tw.kuies.link.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDiffTest {

    @Test
    fun singleRemovedParameterIsTheOnlyHighlightedSegment() {
        val original = "https://example.com/p?utm_source=igsh&id=7"
        val diff = UrlDiffAnalyzer.compare(original, "https://example.com/p?id=7")

        assertEquals(UrlDiffAvailability.PARAMETER_LEVEL, diff.availability)
        assertEquals(original, diff.originalSegments.joinToString("") { it.text })
        assertEquals(
            listOf("utm_source=igsh"),
            diff.originalSegments.filter { it.removed }.map { it.text },
        )
        assertEquals(1, diff.removed.size)
        assertEquals("utm_source", diff.removed.single().name)
        assertEquals("igsh", diff.removed.single().value)
        assertEquals(1, diff.kept.size)
        assertEquals("id", diff.kept.single().name)
        assertEquals("7", diff.kept.single().value)
    }

    @Test
    fun removedAndKeptParametersKeepTheirOriginalOrder() {
        val diff = UrlDiffAnalyzer.compare(
            "https://example.com/p?utm_source=a&id=1&utm_medium=b&color=red",
            "https://example.com/p?id=1&color=red",
        )

        assertEquals(listOf("utm_source", "utm_medium"), diff.removed.map { it.name })
        assertEquals(listOf("id", "color"), diff.kept.map { it.name })
        assertEquals(
            listOf("utm_source=a", "utm_medium=b"),
            diff.originalSegments.filter { it.removed }.map { it.text },
        )
    }

    @Test
    fun unchangedUrlHasNoRemovedParameters() {
        val url = "https://example.com/p?id=1&color=red#top"
        val diff = UrlDiffAnalyzer.compare(url, url)

        assertEquals(0, diff.removed.size)
        assertEquals(2, diff.kept.size)
        assertFalse(diff.hasRemoved)
        assertFalse(diff.originalSegments.any { it.removed })
    }

    @Test
    fun duplicateParametersAreCountedOccurrenceByOccurrence() {
        val diff = UrlDiffAnalyzer.compare(
            "https://example.com/p?utm_source=a&utm_source=b&id=1&id=2",
            "https://example.com/p?id=1&id=2",
        )

        assertEquals(2, diff.removed.size)
        assertEquals(listOf("a", "b"), diff.removed.map { it.value })
        assertTrue(diff.removed.all { it.repeated })
        assertEquals(2, diff.kept.size)
        assertTrue(diff.kept.all { it.repeated })
    }

    @Test
    fun emptyValueAndMissingEqualsAreReportedHonestly() {
        val diff = UrlDiffAnalyzer.compare(
            "https://example.com/p?utm_source=&utm_medium&id=1",
            "https://example.com/p?id=1",
        )

        assertEquals(2, diff.removed.size)
        val empty = diff.removed.first { it.name == "utm_source" }
        assertTrue(empty.hasValue)
        assertEquals("", empty.value)
        val missing = diff.removed.first { it.name == "utm_medium" }
        assertFalse(missing.hasValue)
        assertEquals(null, missing.value)
    }

    @Test
    fun percentEncodingAndFragmentStayExactlyAsWritten() {
        val original =
            "https://example.com/搜尋?q=%E4%B8%AD%E6%96%87&utm_campaign=spring#片段-1"
        val diff = UrlDiffAnalyzer.compare(
            original,
            "https://example.com/搜尋?q=%E4%B8%AD%E6%96%87#片段-1",
        )

        assertEquals(original, diff.originalSegments.joinToString("") { it.text })
        assertEquals("中文", diff.kept.single().value)
        assertEquals("spring", diff.removed.single().value)
        assertTrue(diff.originalSegments.last().text.endsWith("#片段-1"))
    }

    @Test
    fun reorderedParametersAreNotReportedAsRemoved() {
        val diff = UrlDiffAnalyzer.compare(
            "https://example.com/p?a=1&b=2",
            "https://example.com/p?b=2&a=1",
        )

        assertEquals(0, diff.removed.size)
        assertEquals(listOf("b", "a"), diff.kept.map { it.name })
    }

    @Test
    fun resolvedWrapperIsStructuralAndNeverParameterHighlighted() {
        val wrapper = "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fpost&h=AT1"
        val diff = UrlDiffAnalyzer.compare(wrapper, "https://example.com/post")

        assertEquals(UrlDiffAvailability.STRUCTURAL, diff.availability)
        assertTrue(diff.baseChanged)
        assertEquals(wrapper, diff.originalSegments.single().text)
        assertFalse(diff.originalSegments.single().removed)
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun fragmentOnlyChangeStillComparesParameters() {
        val diff = UrlDiffAnalyzer.compare(
            "https://example.com/p?utm_source=a&id=1#top",
            "https://example.com/p?id=1",
        )

        assertEquals(UrlDiffAvailability.PARAMETER_LEVEL, diff.availability)
        assertEquals(listOf("utm_source"), diff.removed.map { it.name })
        assertEquals(listOf("id"), diff.kept.map { it.name })
        assertFalse(diff.baseChanged)
        assertTrue(diff.fragmentChanged)
    }

    @Test
    fun localRuleSourceIsCarriedIntoTheRemovalReason() {
        val diff = UrlDiffAnalyzer.compare(
            originalUrl = "https://example.com/p?utm_source=news&id=1",
            cleanedUrl = "https://example.com/p?id=1",
            localRemoved = listOf(
                RemovedParameter(
                    name = "utm_source",
                    value = "news",
                    raw = "utm_source=news",
                    ruleSource = "UTM",
                ),
            ),
        )

        assertEquals(RemovalReason.RULE, diff.removed.single().removalReason)
        assertEquals("UTM", diff.removed.single().ruleSource)
    }

    @Test
    fun unparsableCleanedUrlIsUnavailableInsteadOfGuessed() {
        val diff = UrlDiffAnalyzer.compare("https://example.com/?utm_source=a", "not a url")

        assertEquals(UrlDiffAvailability.UNAVAILABLE, diff.availability)
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.kept.isEmpty())
    }
}
