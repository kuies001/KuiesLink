package tw.kuies.link

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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
                Box(Modifier.size(360.dp, 640.dp)) {
                    ShareDialogScreen(
                        state = state,
                        onCopyShortUrl = {},
                        onShareShortUrl = {},
                        onFinish = {},
                    )
                }
            }
        }
    }

    @Test
    fun successShowsBeforeAfterAndShortUrlWithActions() {
        show(successState())

        composeRule.onNodeWithText("KuiesLink").assertIsDisplayed()
        composeRule.onNodeWithText("已建立乾淨短網址").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.com/p?id=7").assertExists()
        composeRule.onNodeWithText("https://u.kuies.tw/abc").assertExists()
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
        composeRule.onNodeWithText("目前沒有需要移除的已知參數").assertIsDisplayed()
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
        composeRule.onNodeWithText("這個網站不在目前的清理規則內，無法確認清理結果")
            .assertIsDisplayed()
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

        composeRule.onNodeWithText("短網址建立失敗").assertIsDisplayed()
        composeRule.onNodeWithText("無法連線到短網址服務").assertIsDisplayed()
        composeRule.onNodeWithText("本機已清理網址，但短網址未建立。").assertIsDisplayed()
        composeRule.onNodeWithText("複製清理後網址").assertIsDisplayed()
        composeRule.onNodeWithText("分享清理後網址").assertIsDisplayed()
    }

    @Test
    fun unchangedUrlFailureLeadsWithShortenerFailureAndKeepsOriginal() {
        show(
            LinkUiState(
                input = "https://example.com/p?id=7",
                result = CleaningResult(
                    originalUrl = "https://example.com/p?id=7",
                    cleanedUrl = "https://example.com/p?id=7",
                    status = CleaningStatus.UNCHANGED,
                ),
                isCleaning = false,
                isShortening = false,
                shortResult = null,
                shortenError = tw.kuies.link.data.ShortenError.NETWORK,
            ),
        )

        composeRule.onNodeWithText("短網址建立失敗").assertIsDisplayed()
        composeRule.onNodeWithText("無法連線到短網址服務").assertIsDisplayed()
        composeRule.onNodeWithText("本機未發現需要移除的參數，原始網址已保留。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("複製原網址").assertIsDisplayed()
        composeRule.onNodeWithText("分享原網址").assertIsDisplayed()
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
        composeRule.onNodeWithText("https://example.com/p?utm_source=news").assertExists()
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

    @Test
    fun detailsExpandToShowRemovedAndKeptParameters() {
        show(successState())

        composeRule.onNodeWithTag("cleaning_details_toggle")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("cleaning_details_content", useUnmergedTree = true)
            .assertExists()

        composeRule.onNodeWithText("已移除的參數（1）", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("utm_source", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("保留的參數（1）", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(
            "此參數未被目前的清理規則移除。未移除不代表已確認安全。",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun unsupportedDetailsWarnThatTrackingMayRemain() {
        show(
            successState().copy(
                shortResult = shortResponse(
                    cleaningStatus = ShortenResponse.STATUS_UNSUPPORTED,
                    removedCount = 0,
                    removedParams = emptyList(),
                ),
            ),
        )

        composeRule.onNodeWithTag("cleaning_details_toggle")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("cleaning_details_content", useUnmergedTree = true)
            .assertExists()

        composeRule.onNodeWithText(
            "此網站不在目前的清理規則內，未移除的參數可能仍包含追蹤資訊。",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun expandedDetailsStayScrollableInsideAShortDialog() {
        composeRule.setContent {
            KuiesLinkTheme(darkTheme = true) {
                Box(Modifier.size(360.dp, 640.dp)) {
                    ShareDialogScreen(
                        state = longState(),
                        onCopyShortUrl = {},
                        onShareShortUrl = {},
                        onFinish = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(longOriginal()).assertExists()
        composeRule.onNodeWithTag("cleaning_details_toggle")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("cleaning_details_content", useUnmergedTree = true)
            .assertExists()

        composeRule.onNodeWithText("保留的參數（6）", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("完成").assertIsDisplayed()
    }

    @Test
    fun lightThemeKeepsTheSameCleaningSummary() {
        composeRule.setContent {
            KuiesLinkTheme(darkTheme = false) {
                ShareDialogScreen(
                    state = successState(),
                    onCopyShortUrl = {},
                    onShareShortUrl = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithText("已建立乾淨短網址").assertIsDisplayed()
        composeRule.onNodeWithText("已移除 1 個追蹤參數", substring = true).assertIsDisplayed()
    }

    private fun longOriginal(): String =
        "https://example.com/p?utm_source=newsletter&utm_medium=email&utm_campaign=spring" +
            "&id=7&color=red&size=42&lang=zh-TW&ref=home&sort=new"

    private fun longState(): LinkUiState {
        val original = longOriginal()
        val cleaned = "https://example.com/p?id=7&color=red&size=42&lang=zh-TW&ref=home&sort=new"
        return LinkUiState(
            input = original,
            result = CleaningResult(
                originalUrl = original,
                cleanedUrl = cleaned,
                status = CleaningStatus.CLEANED,
                removedParameters = listOf(
                    RemovedParameter(
                        name = "utm_source",
                        value = "newsletter",
                        raw = "utm_source=newsletter",
                        ruleSource = "UTM",
                    ),
                    RemovedParameter(
                        name = "utm_medium",
                        value = "email",
                        raw = "utm_medium=email",
                        ruleSource = "UTM",
                    ),
                    RemovedParameter(
                        name = "utm_campaign",
                        value = "spring",
                        raw = "utm_campaign=spring",
                        ruleSource = "UTM",
                    ),
                ),
            ),
            isCleaning = false,
            isShortening = false,
            shortResult = shortResponse(
                targetUrl = cleaned,
                removedCount = 3,
                removedParams = listOf("utm_source", "utm_medium", "utm_campaign"),
            ),
        )
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
        targetUrl: String = "https://example.com/p?id=7",
        cleaningStatus: String = ShortenResponse.STATUS_CLEANED,
        removedCount: Int = 1,
        removedParams: List<String> = listOf("utm_source"),
    ): ShortenResponse = ShortenResponse(
        shortUrl = "https://u.kuies.tw/abc",
        targetUrl = targetUrl,
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
