package tw.kuies.link

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
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
        input("https://example.com/p?utm_source=news&id=7")
        clean()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("https://example.com/p?id=7").assertIsDisplayed()
    }

    @Test
    fun cleanUrlIsReportedAsClean() {
        input("https://example.com/p?id=7")
        clean()

        composeRule.onAllNodesWithText("未發現已知追蹤參數").onFirst().assertIsDisplayed()
    }

    @Test
    fun instagramReelExlnIsRemovedEndToEnd() {
        input("https://www.instagram.com/reel/DcClyQgikR4/?exln=Yjg5MXAyMzZid2l0")
        clean()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("https://www.instagram.com/reel/DcClyQgikR4/").assertIsDisplayed()
    }

    @Test
    fun instagramReelQueryIsFullyRemovedEndToEnd() {
        input("https://www.instagram.com/reel/DeuAqoizRW9/?vrfl=djBqNnQzZ2J6Y2Fu")
        clean()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("https://www.instagram.com/reel/DeuAqoizRW9/").assertIsDisplayed()
    }

    @Test
    fun instagramPostCplkIsRemovedEndToEnd() {
        input("https://www.instagram.com/p/DeoNZZmCde8/?cplk=Zmg3OGxybHA2aHow")
        clean()

        composeRule.onNodeWithText("已移除 1 個追蹤參數").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("https://www.instagram.com/p/DeoNZZmCde8/").assertIsDisplayed()
    }

    @Test
    fun multipleUrlsAreListedInsteadOfGuessed() {
        input("https://a.example/1 https://b.example/2")
        composeRule.onNode(hasText("清理並縮短網址") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("找到 2 個網址").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsShareContentUsesCompactCopy() {
        composeRule.onNodeWithContentDescription("設定").performClick()
        composeRule.onNode(hasText("分享內容") and hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("短網址").assertIsDisplayed()

        composeRule.onNode(hasText("分享內容") and hasClickAction()).performClick()
        composeRule.onNodeWithText("短網址（推薦）").assertIsDisplayed()
        composeRule.onNodeWithText("原網址").assertIsDisplayed()
    }

    @Test
    fun settingsOffersInAppUpdateCheck() {
        composeRule.onNodeWithContentDescription("設定").performClick()
        composeRule.onNodeWithText("檢查更新").performScrollTo().assertIsDisplayed()
    }

    private fun input(text: String) {
        composeRule.onNode(hasSetTextAction()).performClick().performTextInput(text)
    }

    private fun clean() {
        composeRule.onNode(hasText("清理並縮短網址") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
    }
}
