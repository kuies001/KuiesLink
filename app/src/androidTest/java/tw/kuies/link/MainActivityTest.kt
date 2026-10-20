package tw.kuies.link

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Compose check on a device: paste a link, clean it, see the result and the removed
 * parameter count. A full clean URL must be reported as clean instead of being "fixed".
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun manualUrlIsCleanedAndShown() {
        composeRule.onNode(hasSetTextAction())
            .performTextInput("https://example.com/p?utm_source=news&id=7")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.com/p?id=7").assertIsDisplayed()
    }

    @Test
    fun cleanUrlIsReportedAsClean() {
        composeRule.onNode(hasSetTextAction())
            .performTextInput("https://example.com/p?id=7")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("網址已經很乾淨").assertIsDisplayed()
    }
}
