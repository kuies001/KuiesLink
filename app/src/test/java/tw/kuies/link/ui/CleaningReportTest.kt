package tw.kuies.link.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.domain.RemovedParameter

class CleaningReportTest {

    @Test
    fun localCleaningCountsTheParametersThatActuallyDisappeared() {
        val report = buildCleaningReport(localCleaned(), short = null)

        assertEquals(CleaningOutcome.CLEANED, report.outcome)
        assertEquals(2, report.removedCount)
        assertTrue(report.canClaimRemoval)
        assertEquals("https://example.com/p?id=7", report.targetUrl)
        assertEquals(listOf("utm_source", "utm_medium"), report.diff.removed.map { it.name })
        assertEquals(listOf("id"), report.diff.kept.map { it.name })
    }

    @Test
    fun unchangedUrlIsNotPresentedAsCleaned() {
        val result = CleaningResult(
            originalUrl = "https://example.com/p?id=7",
            cleanedUrl = "https://example.com/p?id=7",
            status = CleaningStatus.UNCHANGED,
        )

        val report = buildCleaningReport(result, short = null)

        assertEquals(CleaningOutcome.UNCHANGED, report.outcome)
        assertEquals(0, report.removedCount)
        assertFalse(report.canClaimRemoval)
        assertEquals(listOf("id"), report.diff.kept.map { it.name })
    }

    @Test
    fun unresolvedWrapperHasNoTrustworthyTarget() {
        val url = "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F&h=AT1"
        val result = CleaningResult(
            originalUrl = url,
            cleanedUrl = url,
            status = CleaningStatus.UNRESOLVED_WRAPPER,
        )

        val report = buildCleaningReport(result, short = null)

        assertEquals(CleaningOutcome.UNRESOLVED, report.outcome)
        assertNull(report.targetUrl)
        assertFalse(report.canClaimRemoval)
    }

    @Test
    fun serverTargetIsAuthoritativeWhenItKeepsAParameterTheLocalRulesRemoved() {
        val report = buildCleaningReport(
            clean = localCleaned(),
            short = shortResponse(
                targetUrl = "https://example.com/p?utm_source=news&utm_medium=email&id=7",
                cleaningStatus = ShortenResponse.STATUS_CLEANED,
                removedCount = 2,
                removedParams = listOf("utm_source", "utm_medium"),
            ),
        )

        assertEquals(CleaningOutcome.INCONCLUSIVE, report.outcome)
        assertEquals(0, report.removedCount)
        assertFalse(report.canClaimRemoval)
        assertTrue(report.countMismatch)
        assertTrue(report.localResultDiffers)
        assertEquals(
            "https://example.com/p?utm_source=news&utm_medium=email&id=7",
            report.targetUrl,
        )
    }

    @Test
    fun serverRemovalCountThatDiffersFromTheVisibleDiffIsReported() {
        val report = buildCleaningReport(
            clean = localCleaned(),
            short = shortResponse(
                targetUrl = "https://example.com/p?utm_medium=email&id=7",
                cleaningStatus = ShortenResponse.STATUS_CLEANED,
                removedCount = 3,
                removedParams = listOf("utm_source", "other", "third"),
            ),
        )

        assertEquals(CleaningOutcome.CLEANED, report.outcome)
        assertEquals(1, report.removedCount)
        assertTrue(report.countMismatch)
        assertEquals(3, report.serverReportedRemovedCount)
    }

    @Test
    fun unsupportedWebsiteNeverClaimsCleaning() {
        val report = buildCleaningReport(
            clean = localCleaned(),
            short = shortResponse(
                targetUrl = "https://example.com/p?utm_source=news&utm_medium=email&id=7",
                cleaningStatus = ShortenResponse.STATUS_UNSUPPORTED,
                removedCount = 0,
            ),
        )

        assertEquals(CleaningOutcome.UNSUPPORTED, report.outcome)
        assertEquals(0, report.removedCount)
        assertFalse(report.canClaimRemoval)
        assertEquals("目標網址（未清理）", report.targetLabel())
        val summary = report.summaryText(shortUrlCreated = true)
        assertEquals("已建立短網址", summary.title)
        assertFalse(summary.title.contains("清理"))
    }

    @Test
    fun resolvedWrapperUsesStructuralComparisonAndServerCount() {
        val wrapper = "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fpost&h=AT1"
        val clean = CleaningResult(
            originalUrl = wrapper,
            cleanedUrl = wrapper,
            status = CleaningStatus.UNRESOLVED_WRAPPER,
        )

        val report = buildCleaningReport(
            clean = clean,
            short = shortResponse(
                shortUrl = "https://u.kuies.tw/wrap",
                targetUrl = "https://example.com/post",
                cleaningStatus = ShortenResponse.STATUS_CLEANED,
                removedCount = 2,
                removedParams = listOf("h", "u"),
                resolutionStatus = "RESOLVED",
            ),
        )

        assertEquals(CleaningOutcome.CLEANED, report.outcome)
        assertEquals(2, report.removedCount)
        assertTrue(report.resolvedWrapper)
        assertEquals("還原後的網址", report.targetLabel())
        assertEquals(2, report.diff.removed.size)
    }

    @Test
    fun resolvedWrapperWithoutReportedRemovalsIsOnlyAResolution() {
        val wrapper = "https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fpost&e=abc"
        val clean = CleaningResult(
            originalUrl = wrapper,
            cleanedUrl = wrapper,
            status = CleaningStatus.UNRESOLVED_WRAPPER,
        )

        val report = buildCleaningReport(
            clean = clean,
            short = shortResponse(
                targetUrl = "https://example.com/post",
                cleaningStatus = ShortenResponse.STATUS_UNCHANGED,
                removedCount = 0,
                resolutionStatus = "RESOLVED",
            ),
        )

        assertEquals(CleaningOutcome.RESOLVED, report.outcome)
        assertEquals(0, report.removedCount)
        assertFalse(report.canClaimRemoval)
        assertEquals("已還原連結並建立短網址", report.summaryText(true).title)
    }

    @Test
    fun serverCleanedWithoutAnyVisibleChangeIsInconclusive() {
        val report = buildCleaningReport(
            clean = localCleaned(),
            short = shortResponse(
                targetUrl = localCleaned().originalUrl,
                cleaningStatus = ShortenResponse.STATUS_CLEANED,
                removedCount = 0,
            ),
        )

        assertEquals(CleaningOutcome.INCONCLUSIVE, report.outcome)
        assertFalse(report.canClaimRemoval)
        assertEquals("無法確認清理結果", report.summaryText(shortUrlCreated = false).title)
    }

    private fun localCleaned(): CleaningResult = CleaningResult(
        originalUrl = "https://example.com/p?utm_source=news&utm_medium=email&id=7",
        cleanedUrl = "https://example.com/p?id=7",
        status = CleaningStatus.CLEANED,
        removedParameters = listOf(
            RemovedParameter(
                name = "utm_source",
                value = "news",
                raw = "utm_source=news",
                ruleSource = "UTM",
            ),
            RemovedParameter(
                name = "utm_medium",
                value = "email",
                raw = "utm_medium=email",
                ruleSource = "UTM",
            ),
        ),
    )

    private fun shortResponse(
        shortUrl: String = "https://u.kuies.tw/abc",
        targetUrl: String = "https://example.com/p?id=7",
        cleaningStatus: String = ShortenResponse.STATUS_CLEANED,
        removedCount: Int = 2,
        removedParams: List<String> = listOf("utm_source", "utm_medium"),
        resolutionStatus: String = "NOT_WRAPPER",
    ): ShortenResponse = ShortenResponse(
        shortUrl = shortUrl,
        targetUrl = targetUrl,
        processingStatus = cleaningStatus,
        resolutionStatus = resolutionStatus,
        cleaningStatus = cleaningStatus,
        removedCount = removedCount,
        removedParams = removedParams,
        warnings = emptyList(),
        cleaningRulesVersion = "test",
        cleaningScope = "known_params",
    )
}
