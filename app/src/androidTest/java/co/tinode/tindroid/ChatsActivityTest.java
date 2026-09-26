package co.tinode.tindroid;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Backend-independent navigation tests for the chats screen.
 *
 * <p>The tests deliberately verify only local UI behavior. ChatsActivity starts a background
 * connection attempt, but no authenticated account or running Tinode server is required.</p>
 */
@RunWith(AndroidJUnit4.class)
public class ChatsActivityTest {
    @Rule
    public final ActivityScenarioRule<ChatsActivity> activityRule =
            new ActivityScenarioRule<>(ChatsActivity.class);

    @Test
    public void launchesWithChatListAndNewChatButton() {
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.chat_list)).check(matches(isDisplayed()));
        onView(withId(R.id.startNewChat)).check(matches(isDisplayed()));
        onView(withContentDescription(R.string.button_hint_start_new_chat))
                .check(matches(isDisplayed()));
    }

    @Test
    public void newChatButtonOpensNewChatScreen() {
        onView(withId(R.id.startNewChat)).perform(click());

        onView(withText(R.string.action_new_chat)).check(matches(isDisplayed()));
    }

    @Test
    public void archiveMenuOpensArchiveAndBackReturnsToChats() {
        Context context = ApplicationProvider.getApplicationContext();
        openActionBarOverflowOrOptionsMenu(context);
        onView(withText(R.string.action_show_archive)).perform(click());

        onView(withText(R.string.archived_chats)).check(matches(isDisplayed()));
        onView(withId(R.id.startNewChat)).check(doesNotExist());

        pressBack();
        onView(withId(R.id.startNewChat)).check(matches(isDisplayed()));
    }
}
