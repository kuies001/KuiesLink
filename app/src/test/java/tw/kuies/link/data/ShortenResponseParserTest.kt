package tw.kuies.link.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortenResponseParserTest {

    @Test
    fun completedCleanedResponseIsSuccess() {
        val result = parse(
            201,
            """
            {
              "short_url": "https://u.kuies.tw/abc",
              "target_url": "https://example.com/p?id=7",
              "original_url": "https://example.com/p?id=7&utm_source=news",
              "resolved_url": null,
              "processing_status": "completed",
              "resolution_status": "not_needed",
              "cleaning_status": "cleaned",
              "removed_count": 1,
              "removed_parameters": [{"name": "utm_source", "value": "news"}],
              "warnings": []
            }
            """.trimIndent(),
        )

        val response = (result as ShortenResult.Success).response
        assertEquals("https://u.kuies.tw/abc", response.shortUrl)
        assertEquals("https://example.com/p?id=7", response.targetUrl)
        assertTrue(response.isCompleted)
        assertTrue(response.isVerifiedClean)
        assertEquals(1, response.removedCount)
        assertEquals("utm_source", response.removedParameters.single().name)
    }

    @Test
    fun partialCleaningIsAcceptedButNotVerifiedClean() {
        val result = parse(201, body(cleaning = "partial", removedCount = 1))

        val response = (result as ShortenResult.Success).response
        assertTrue(response.isPartial)
        assertEquals(false, response.isVerifiedClean)
    }

    @Test
    fun wrapperUnresolvedIsItsOwnError() {
        assertEquals(
            ShortenResult.Failure(ShortenError.WRAPPER_UNRESOLVED, 422),
            parse(422, """{"error":"wrapper_unresolved","processing_status":"failed"}"""),
        )
    }

    @Test
    fun rateLimitAndServerErrorsAreMapped() {
        assertEquals(ShortenError.RATE_LIMITED, (parse(429, "") as ShortenResult.Failure).error)
        assertEquals(ShortenError.SERVER_ERROR, (parse(500, "oops") as ShortenResult.Failure).error)
        assertEquals(ShortenError.INVALID_REQUEST, (parse(400, "{}") as ShortenResult.Failure).error)
    }

    @Test
    fun brokenJsonIsAnInvalidResponse() {
        assertEquals(
            ShortenError.INVALID_RESPONSE,
            (parse(201, "<html>captive portal") as ShortenResult.Failure).error,
        )
    }

    @Test
    fun missingNewFieldsIsNotTreatedAsSuccess() {
        val legacy = """
            {"short_url":"https://u.kuies.tw/abc","target_url":"https://example.com/"}
        """.trimIndent()
        assertEquals(
            ShortenError.UNVERIFIED,
            (parse(201, legacy) as ShortenResult.Failure).error,
        )
    }

    @Test
    fun incompleteProcessingIsNotSuccessEvenWith201() {
        val result = parse(201, body(processing = "failed"))
        assertEquals(ShortenError.UNVERIFIED, (result as ShortenResult.Failure).error)
    }

    @Test
    fun unknownCleaningStatusIsNotSuccess() {
        val result = parse(201, body(cleaning = "probably_fine"))
        assertEquals(ShortenError.UNVERIFIED, (result as ShortenResult.Failure).error)
    }

    private fun parse(httpCode: Int, body: String): ShortenResult =
        ShortenResponseParser.parse(httpCode, body)

    private fun body(
        processing: String = "completed",
        cleaning: String = "cleaned",
        removedCount: Int = 1,
    ): String = """
        {
          "short_url": "https://u.kuies.tw/abc",
          "target_url": "https://example.com/",
          "processing_status": "$processing",
          "resolution_status": "not_needed",
          "cleaning_status": "$cleaning",
          "removed_count": $removedCount,
          "removed_parameters": [],
          "warnings": []
        }
    """.trimIndent()
}
