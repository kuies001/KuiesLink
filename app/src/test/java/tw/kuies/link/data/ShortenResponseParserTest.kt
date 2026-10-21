package tw.kuies.link.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortenResponseParserTest {

    @Test
    fun cleanedResponseIsSuccess() {
        val result = parse(
            201,
            body(cleaning = "CLEANED", removedCount = 1, removed = """["igsh"]"""),
        )

        val response = (result as ShortenResult.Success).response
        assertEquals("https://u.kuies.tw/abc", response.shortUrl)
        assertEquals("https://www.instagram.com/reel/ABC/", response.targetUrl)
        assertTrue(response.isCleaned)
        assertEquals(1, response.removedCount)
        assertEquals(listOf("igsh"), response.removedParams)
        assertEquals("2026.10.20.1", response.cleaningRulesVersion)
    }

    @Test
    fun resolvedWrapperIsSuccess() {
        val result = parse(
            201,
            body(
                processing = "UNCHANGED",
                cleaning = "UNCHANGED",
                resolution = "RESOLVED",
                removedCount = 0,
                removed = "[]",
                warnings = """["social_share_wrapper_resolved"]""",
            ),
        )

        val response = (result as ShortenResult.Success).response
        assertTrue(response.wasResolved)
        assertTrue(response.isUnchanged)
    }

    @Test
    fun unsupportedHostIsSuccessButNotCleaned() {
        val result = parse(
            201,
            body(
                processing = "UNSUPPORTED",
                cleaning = "UNSUPPORTED",
                removedCount = 0,
                removed = "[]",
                warnings = """["cleaning_rules_unavailable","cleaning_scope_supported_params_only"]""",
            ),
        )

        val response = (result as ShortenResult.Success).response
        assertTrue(response.isUnsupported)
        assertEquals(0, response.removedCount)
        assertEquals(2, response.warnings.size)
    }

    @Test
    fun wrapperUnresolvedIsItsOwnError() {
        assertEquals(
            ShortenResult.Failure(ShortenError.WRAPPER_UNRESOLVED, 422),
            parse(
                422,
                """
                {"error":"unable to resolve social share wrapper",
                 "processing_status":"UNRESOLVED_WRAPPER",
                 "resolution_status":"UNRESOLVED_WRAPPER",
                 "cleaning_status":"UNSUPPORTED",
                 "removed_params":[],"removed_count":0,
                 "warnings":["social_share_wrapper_unresolved"]}
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun rateLimitAndServerErrorsAreMapped() {
        assertEquals(ShortenError.RATE_LIMITED, failure(429).error)
        assertEquals(ShortenError.SERVER_ERROR, failure(500, "oops").error)
        assertEquals(ShortenError.INVALID_REQUEST, failure(400, "{}").error)
    }

    @Test
    fun brokenJsonIsAnInvalidResponse() {
        assertEquals(ShortenError.INVALID_RESPONSE, failure(201, "<html>captive portal").error)
    }

    @Test
    fun missingStatusFieldsIsNotTreatedAsSuccess() {
        val legacy = """{"short_url":"https://u.kuies.tw/abc","target_url":"https://example.com/"}"""
        assertEquals(ShortenError.UNVERIFIED, failure(201, legacy).error)
    }

    @Test
    fun processingStatusMustMatchTheCleaningStatus() {
        val contradictory = body(processing = "UNRESOLVED_WRAPPER", cleaning = "CLEANED")
        assertEquals(ShortenError.UNVERIFIED, failure(201, contradictory).error)
    }

    @Test
    fun unknownCleaningStatusIsNotSuccess() {
        assertEquals(
            ShortenError.UNVERIFIED,
            failure(201, body(processing = "PROBABLY_FINE", cleaning = "PROBABLY_FINE")).error,
        )
    }

    @Test
    fun unresolvedResolutionIsNotSuccess() {
        assertEquals(
            ShortenError.UNVERIFIED,
            failure(201, body(cleaning = "UNCHANGED", resolution = "UNRESOLVED_WRAPPER")).error,
        )
    }

    private fun parse(httpCode: Int, body: String): ShortenResult =
        ShortenResponseParser.parse(httpCode, body)

    private fun failure(httpCode: Int, body: String = ""): ShortenResult.Failure =
        parse(httpCode, body) as ShortenResult.Failure

    private fun body(
        processing: String = "CLEANED",
        cleaning: String = "CLEANED",
        resolution: String = "NOT_WRAPPER",
        removedCount: Int = 1,
        removed: String = """["igsh"]""",
        warnings: String = "[]",
    ): String = """
        {
          "short_url": "https://u.kuies.tw/abc",
          "target_url": "https://www.instagram.com/reel/ABC/",
          "processing_status": "$processing",
          "resolution_status": "$resolution",
          "cleaning_status": "$cleaning",
          "removed_params": $removed,
          "removed_count": $removedCount,
          "warnings": $warnings,
          "cleaning_rules_version": "2026.10.20.1",
          "cleaning_scope": "supported_tracking_params_only"
        }
    """.trimIndent()
}
