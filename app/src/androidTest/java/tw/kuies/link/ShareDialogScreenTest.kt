package tw.kuies.link

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.domain.RemovedParameter
import tw.kuies.link.ui.LinkUiState
import tw.kuies.link.ui.ShareDialogScreen
import tw.kuies.link.ui.theme.KuiesLinkTheme

/**
 * Renders the external-share dialog with fixed state, so the labels and the three actions are
 * checked without calling the network or depending on another activity.
 */
@RunWith(AndroidJUnit4::class)
class ShareDialogScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: LinkUiState) {
        composeRule.setContent {
            KuiesLinkTheme(darkTheme = true) {
                ShareDialogScreen(
                    state = state,
                    onCopyShortUrl = {},
                    onShareShortUrl = {},
                    onFinish = {},
                )
            }
        }
    }

    @Test
    fun successShowsBeforeAfterAndShortUrlWithActions() {
        show(successState())

        composeRule.onNodeWithText("KuiesLink").assertIsDisplayed()
        composeRule.onNodeWithText("已清理並建立短網址").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.com/p?id=7").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("https://u.kuies.tw/abc").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("已移除 1 個追蹤參數", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("複製短網址").assertIsDisplayed()
        composeRule.onNodeWithText("分享短網址").assertIsDisplayed()
        composeRule.onNodeWithText("完成").assertIsDisplayed()
    }

    @Test
    fun successWithoutRemovedParametersSaysNoCleaningIsNeeded() {
        show(
            successState().copy(
                shortResult = shortResponse(
                    cleaningStatus = ShortenResponse.STATUS_UNCHANGED,
                    removedCount = 0,
                    removedParams = emptyList(),
                ),
            ),
        )

        composeRule.onNodeWithText("無須清理，已建立短網址").assertIsDisplayed()
        composeRule.onNodeWithText("沒有需要移除的追蹤參數").assertIsDisplayed()
    }

    @Test
    fun unsupportedWebsiteDoesNotClaimItWasCleaned() {
        show(
            successState().copy(
                shortResult = shortResponse(
                    cleaningStatus = ShortenResponse.STATUS_UNSUPPORTED,
                    removedCount = 0,
                    removedParams = emptyList(),
                ),
            ),
        )

        composeRule.onNodeWithText("已建立短網址").assertIsDisplayed()
        composeRule.onNodeWithText("未支援此網站的清理規則").assertIsDisplayed()
    }

    @Test
    fun shorteningFailureFallsBackToTheCleanedUrl() {
        show(
            successState().copy(
                shortResult = null,
                isShortening = false,
                shortenError = tw.kuies.link.data.ShortenError.NETWORK,
            ),
        )

        composeRule.onNodeWithText("已清理，但短網址建立失敗").assertIsDisplayed()
        composeRule.onNodeWithText("無法連線到短網址服務").assertIsDisplayed()
        composeRule.onNodeWithText("複製清理後網址").assertIsDisplayed()
        composeRule.onNodeWithText("分享網址").assertIsDisplayed()
    }

    @Test
    fun processingShowsOnlyCancelAndTheOriginalUrl() {
        show(
            LinkUiState(
                input = "https://example.com/p?utm_source=news",
                isCleaning = true,
            ),
        )

        composeRule.onNodeWithText("正在清理網址…").assertIsDisplayed()
        composeRule.onNodeWithText("處理中…").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.com/p?utm_source=news").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun copyAndShareActionsReceiveTheShortUrl() {
        val copied = mutableListOf<String>()
        val shared = mutableListOf<String>()
        composeRule.setContent {
            KuiesLinkTheme(darkTheme = true) {
                ShareDialogScreen(
                    state = successState(),
                    onCopyShortUrl = { copied += it },
                    onShareShortUrl = { shared += it },
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("複製短網址").performClick()
        composeRule.onNodeWithText("分享短網址").performClick()

        assertEquals(listOf("https://u.kuies.tw/abc"), copied)
        assertEquals(listOf("https://u.kuies.tw/abc"), shared)
    }

    private fun successState(): LinkUiState = LinkUiState(
        input = "https://example.com/p?utm_source=igsh&id=7",
        result = CleaningResult(
            originalUrl = "https://example.com/p?utm_source=igsh&id=7",
            cleanedUrl = "https://example.com/p?id=7",
            status = CleaningStatus.CLEANED,
            removedParameters = listOf(
                RemovedParameter(
                    name = "utm_source",
                    value = "igsh",
                    raw = "utm_source=igsh",
                    ruleSource = "test",
                ),
            ),
        ),
        isCleaning = false,
        isShortening = false,
        shortResult = shortResponse(),
    )

    private fun shortResponse(
        cleaningStatus: String = ShortenResponse.STATUS_CLEANED,
        removedCount: Int = 1,
        removedParams: List<String> = listOf("utm_source"),
    ): ShortenResponse = ShortenResponse(
        shortUrl = "https://u.kuies.tw/abc",
        targetUrl = "https://example.com/p?id=7",
        processingStatus = cleaningStatus,
        resolutionStatus = "NOT_WRAPPER",
        cleaningStatus = cleaningStatus,
        removedCount = removedCount,
        removedParams = removedParams,
        warnings = emptyList(),
        cleaningRulesVersion = "test",
        cleaningScope = "known_params",
    )
}
