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
 * End-to-end Compose checks on a device: type a link, clean it, see the result and the removed
 * parameter count. A full clean URL must be reported as clean instead of being "fixed".
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun manualUrlIsCleanedAndShown() {
        composeRule.onNode(hasSetTextAction()).performClick()
            .performTextInput("https://example.com/p?utm_source=news&id=7")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.com/p?id=7").assertIsDisplayed()
    }

    @Test
    fun cleanUrlIsReportedAsClean() {
        composeRule.onNode(hasSetTextAction()).performClick()
            .performTextInput("https://example.com/p?id=7")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("網址已經很乾淨").assertIsDisplayed()
    }

    @Test
    fun instagramReelExlnIsRemovedEndToEnd() {
        composeRule.onNode(hasSetTextAction()).performClick()
            .performTextInput("https://www.instagram.com/reel/DcClyQgikR4/?exln=Yjg5MXAyMzZid2l0")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").assertIsDisplayed()
        composeRule.onNodeWithText("https://www.instagram.com/reel/DcClyQgikR4/").assertIsDisplayed()
    }

    @Test
    fun instagramReelQueryIsFullyRemovedEndToEnd() {
        composeRule.onNode(hasSetTextAction()).performClick()
            .performTextInput("https://www.instagram.com/reel/DeuAqoizRW9/?vrfl=djBqNnQzZ2J6Y2Fu")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").assertIsDisplayed()
        composeRule.onNodeWithText("https://www.instagram.com/reel/DeuAqoizRW9/").assertIsDisplayed()
    }

    @Test
    fun instagramPostCplkIsRemovedEndToEnd() {
        composeRule.onNode(hasSetTextAction()).performClick()
            .performTextInput("https://www.instagram.com/p/DeoNZZmCde8/?cplk=Zmg3OGxybHA2aHow")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").assertIsDisplayed()
        composeRule.onNodeWithText("https://www.instagram.com/p/DeoNZZmCde8/").assertIsDisplayed()
    }

    @Test
    fun multipleUrlsAreListedInsteadOfGuessed() {
        composeRule.onNode(hasSetTextAction()).performClick()
            .performTextInput("https://a.example/1 https://b.example/2")

        composeRule.onNodeWithText("清理網址").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("找到 2 個網址").assertIsDisplayed()
    }
}
