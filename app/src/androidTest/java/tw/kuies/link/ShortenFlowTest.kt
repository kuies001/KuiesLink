package tw.kuies.link

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real shortener path against the debug endpoint (`http://127.0.0.1:8787` reached
 * through `adb reverse`). Run with `adb install -r` + `am instrument`, never the connected task.
 */
@RunWith(AndroidJUnit4::class)
class ShortenFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun manualShortenRendersTheServerResponse() {
        composeRule.onNode(hasSetTextAction()).performClick()
            .performTextInput("https://www.instagram.com/reel/DcClyQgikR4/?igsh=MW9xZ2F0")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("清理並縮短網址").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("伺服器已清理追蹤參數")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("伺服器已清理追蹤參數").performScrollTo().assertIsDisplayed()
        val baseUrl = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.shortener_api_base_url)
            .trimEnd('/') + "/"
        val shortUrls = composeRule
            .onAllNodesWithText(baseUrl, substring = true)
            .fetchSemanticsNodes()
        assertTrue("short URL was not rendered", shortUrls.isNotEmpty())
    }

    @Test
    fun backendWrapperErrorIsShownWithoutRetry() {
        composeRule.onNode(hasSetTextAction()).performClick()
            .performTextInput("https://www.threads.net/share/AbCdEf")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("清理並縮短網址").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("無法還原這個包裝連結")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("無法還原這個包裝連結").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("重試").assertDoesNotExist()
    }
}
