package se.birdy.app.notifications

import android.app.NotificationManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import se.birdy.app.i18n.AppStrings
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.content.Locale
import kotlin.test.assertEquals

/**
 * 1.3.1 punkt 12 / Task 2b: the channels' names and descriptions follow the app's language.
 * Android updates name and description on an existing channel id and keeps the user's own
 * settings (importance, sound, on/off), so a language change must rename the same three
 * channels rather than create new ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationChannelsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService<NotificationManager>()!!

    @Test
    fun `english strings create the three channels with english names and descriptions`() {
        attachComposeResourcesContext()
        runBlocking { NotificationChannels.createOrUpdate(context, AppStrings(Locale.EN)) }

        val daily = manager.getNotificationChannel(NotificationChannels.DAILY_BIRD)
        assertEquals("Bird of the day", daily.name)
        assertEquals("A species to look out for, every morning.", daily.description)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, daily.importance)

        val recap = manager.getNotificationChannel(NotificationChannels.WEEKLY_RECAP)
        assertEquals("Weekly recap", recap.name)
        assertEquals("Your week in the field, Sunday evening.", recap.description)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, recap.importance)

        val trophy = manager.getNotificationChannel(NotificationChannels.TROPHY_PROGRESS)
        assertEquals("Badge progress", trophy.name)
        assertEquals("Once a week: how close you are to your next badge.", trophy.description)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, trophy.importance)
    }

    @Test
    fun `swedish strings create the three channels with swedish names and descriptions`() {
        attachComposeResourcesContext()
        runBlocking { NotificationChannels.createOrUpdate(context, AppStrings(Locale.SV)) }

        val daily = manager.getNotificationChannel(NotificationChannels.DAILY_BIRD)
        assertEquals("Dagens fågel", daily.name)
        assertEquals("En art att hålla utkik efter, varje morgon.", daily.description)

        val recap = manager.getNotificationChannel(NotificationChannels.WEEKLY_RECAP)
        assertEquals("Veckans recap", recap.name)
        assertEquals("Din vecka i fält, söndag kväll.", recap.description)

        val trophy = manager.getNotificationChannel(NotificationChannels.TROPHY_PROGRESS)
        assertEquals("Märkesprogression", trophy.name)
        assertEquals("En gång i veckan: hur nära du är nästa märke.", trophy.description)
    }

    @Test
    fun `a language change renames the same three channels instead of duplicating them`() {
        attachComposeResourcesContext()
        runBlocking { NotificationChannels.createOrUpdate(context, AppStrings(Locale.SV)) }

        // A setting the user controls on a real device (importance survives a rename); done here
        // by mutating the channel Robolectric already stores, since there is no system-settings UI
        // to drive in a unit test.
        manager.getNotificationChannel(NotificationChannels.DAILY_BIRD).importance = NotificationManager.IMPORTANCE_LOW

        runBlocking { NotificationChannels.createOrUpdate(context, AppStrings(Locale.EN)) }

        assertEquals(3, manager.notificationChannels.size, "renaming must not duplicate channels")
        val daily = manager.getNotificationChannel(NotificationChannels.DAILY_BIRD)
        assertEquals("Bird of the day", daily.name)
        assertEquals("A species to look out for, every morning.", daily.description)
        // Confirmed empirically (this Robolectric version): ShadowNotificationManager's update on
        // an existing channel id sets name and description and lowers importance whenever the
        // requested value is lower than the current one — it does not model Android's real
        // user-locked fields, where importance never changes on an update at all. Both keep LOW
        // here only because the app requests DEFAULT, which is higher than the LOW set above;
        // this assertion checks Robolectric's update rule, not the app's code.
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            daily.importance,
            "Android keeps the user's own importance when a channel id is recreated",
        )

        val recap = manager.getNotificationChannel(NotificationChannels.WEEKLY_RECAP)
        assertEquals("Weekly recap", recap.name)
        val trophy = manager.getNotificationChannel(NotificationChannels.TROPHY_PROGRESS)
        assertEquals("Badge progress", trophy.name)
    }
}
