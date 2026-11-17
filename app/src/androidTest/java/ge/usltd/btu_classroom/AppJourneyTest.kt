package ge.usltd.btu_classroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun exploreNavigateChangeAppearanceAndClearLocalData() {
        compose.onNodeWithTag("explore_app_button").performScrollTo().performClick()
        compose.onNodeWithTag("tab_courses").performClick()
        compose.onNodeWithTag("courses_search").performTextInput("CS301")
        compose.onNodeWithTag("tab_schedule").performClick()
        compose.onNodeWithTag("schedule_content").assertIsDisplayed()
        compose.onNodeWithTag("tab_more").performClick()
        compose.onNodeWithTag("more_settings_card").performClick()
        compose.onNodeWithTag("settings_theme_dark").performScrollTo().performClick()
        compose.onNodeWithTag("settings_lang_ka").performScrollTo().performClick()
        compose.onNodeWithTag("settings_lang_en").performScrollTo().performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_clear_data_button"))
        compose.onNodeWithTag("settings_clear_data_button").performClick()
        compose.onNodeWithTag("clear_data_confirm_dialog").assertIsDisplayed()
        compose.onNodeWithTag("clear_data_confirm_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sign_in_button").assertIsDisplayed()
    }
}
