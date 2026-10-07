package se.birdy.app.notifications

import android.app.Notification
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.BuildConfig
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesImage
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7d, the approved notification add-on: the 08:00 Dagens fågel notification
 * carries the species photo, an invitation instead of "Här just nu." and two buttons with the
 * right deep links. Content via the shared [NotificationPayloads], the Android build via
 * [DailyBirdNotification].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DailyBirdNotificationTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun payloads(): NotificationPayloads {
        val prefs = FakeUserPreferences()
        runBlocking { prefs.setDailyBirdPushEnabled(true) }
        val repo = FakeSpeciesRepository.withDefaults()
        val greatTit = repo.byId.value.getValue(SpeciesId("Q25485"))!!
        val withPhoto =
            greatTit.copy(
                images =
                    listOf(
                        SpeciesImage("secondary", "Q25485/secondary-1.webp", 800, 600, "CC BY-SA 4.0", "A", "u"),
                        SpeciesImage("hero", "Q25485/hero.webp", 2400, 1600, "CC BY-SA 4.0", "B", "u"),
                    ),
            )
        return NotificationPayloads(
            prefs = prefs,
            observationRepo = FakeObservationRepository(),
            badgeRepo = FakeBadgeRepository(),
            badgeCatalog = BadgeCatalog(version = 1, badges = emptyList()),
            speciesByQid = { mapOf(SpeciesId("Q25485") to withPhoto) },
            speciesNameFor = { "Talgoxe" },
            selectDailyBird = { DailyBird("Q25485", SeasonTag.PRESENT) },
            dailyBirdMatchCount = { 0 },
            timeZone = TimeZone.of("Europe/Stockholm"),
            clock = Clock.System,
        )
    }

    private fun content() =
        NotificationContent(
            title = "Dagens fågel: Talgoxe",
            body = "Kan du fånga den idag? Kika, foto eller läte räknas.",
            deepLink = "birdy://species/Q25485",
            imagePath = "Q25485/hero.webp",
            actions =
                listOf(
                    NotificationAction("Läs om arten", "birdy://species/Q25485"),
                    NotificationAction("Lyssna efter den", "birdy://audio"),
                ),
        )

    @Test
    @Config(qualifiers = "+sv")
    fun `the swedish content has the title the invitation the photo and two buttons`() {
        attachComposeResourcesContext()
        val c = runBlocking { payloads().dailyBird(LocalDate(2026, 10, 6)) }!!
        assertEquals("Dagens fågel: Talgoxe", c.title)
        assertEquals("Kan du fånga den idag? Kika, foto eller läte räknas.", c.body)
        assertEquals("birdy://species/Q25485", c.deepLink)
        assertEquals("Q25485/hero.webp", c.imagePath)
        assertEquals(
            listOf(
                NotificationAction("Läs om arten", "birdy://species/Q25485"),
                NotificationAction("Lyssna efter den", "birdy://audio"),
            ),
            c.actions,
        )
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the english content`() {
        attachComposeResourcesContext()
        val c = runBlocking { payloads().dailyBird(LocalDate(2026, 10, 6)) }!!
        assertEquals("Bird of the day: Talgoxe", c.title)
        assertEquals("Can you catch it today? Camera, photo or call all count.", c.body)
        assertEquals(listOf("Read about it", "Listen for it"), c.actions.map { it.label })
    }

    @Test
    fun `the notification shows the photo and the two buttons open the right deep links`() {
        val picture = DailyBirdNotification.pictureFrom { Bitmap.createBitmap(1024, 683, Bitmap.Config.ARGB_8888) }!!
        val n = DailyBirdNotification.build(context, content(), picture)

        assertEquals(NotificationChannels.DAILY_BIRD, n.channelId)
        assertEquals("Dagens fågel: Talgoxe", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "Kan du fånga den idag? Kika, foto eller läte räknas.",
            n.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(
            n.extras.containsKey(Notification.EXTRA_PICTURE) || n.extras.containsKey(Notification.EXTRA_PICTURE_ICON),
            "the photo is in the expanded notification",
        )
        assertNotNull(n.getLargeIcon(), "the photo is the collapsed thumbnail")

        val contentIntent = shadowOf(n.contentIntent).savedIntent
        assertEquals(Uri.parse("birdy://species/Q25485"), contentIntent.data)
        assertEquals(context.packageName, contentIntent.`package`)

        assertEquals(listOf("Läs om arten", "Lyssna efter den"), n.actions.map { it.title.toString() })
        val readMore = shadowOf(n.actions[0].actionIntent).savedIntent
        val listen = shadowOf(n.actions[1].actionIntent).savedIntent
        assertEquals(Uri.parse("birdy://species/Q25485"), readMore.data)
        assertEquals(Uri.parse("birdy://audio"), listen.data)
        // The buttons ask MainActivity to dismiss the notification (a tap on the body auto-cancels).
        assertEquals(1001, readMore.getIntExtra(DailyBirdNotification.EXTRA_DISMISS_NOTIFICATION_ID, 0))
        assertEquals(1001, listen.getIntExtra(DailyBirdNotification.EXTRA_DISMISS_NOTIFICATION_ID, 0))
        assertFalse(contentIntent.hasExtra(DailyBirdNotification.EXTRA_DISMISS_NOTIFICATION_ID))
    }

    @Test
    fun `without a photo the notification is text and buttons only`() {
        val n = DailyBirdNotification.build(context, content(), picture = null)
        assertFalse(n.extras.containsKey(Notification.EXTRA_PICTURE))
        assertNull(n.getLargeIcon())
        assertEquals(2, n.actions.size)
    }

    @Test
    fun `the large icon is a small thumbnail and the big picture keeps its size`() {
        val picture = DailyBirdNotification.pictureFrom { Bitmap.createBitmap(1024, 683, Bitmap.Config.ARGB_8888) }!!
        assertEquals(1024, picture.big.width)
        assertEquals(DailyBirdNotification.THUMBNAIL_MAX_EDGE_PX, maxOf(picture.thumbnail.width, picture.thumbnail.height))
        assertFalse(picture.big.isRecycled, "making the thumbnail must not recycle the big picture")
    }

    @Test
    fun `a decode that runs out of memory or crashes gives no photo and the notification still goes out`() {
        assertNull(DailyBirdNotification.pictureFrom { throw OutOfMemoryError("test") })
        assertNull(DailyBirdNotification.pictureFrom { error("broken image") })
        val n = DailyBirdNotification.build(context, content(), DailyBirdNotification.pictureFrom { throw OutOfMemoryError("test") })
        assertEquals(2, n.actions.size)
        assertEquals("Dagens fågel: Talgoxe", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `the bundled photo is decoded and scaled down`() {
        // The benchmark photos are debug-only assets (composeApp/src/androidDebug/assets, 1.3.0 legal
        // review fix F), so only testDebugUnitTest (the CI gate) has one to decode. The release unit
        // test variant skips this case; there is no other bundled JPEG in the module's assets.
        assumeTrue("benchmark/talgoxe.jpg is a debug-only asset", BuildConfig.DEBUG)
        val bitmap = DailyBirdNotification.decodeAsset(context.assets, "benchmark/talgoxe.jpg", maxEdgePx = 256)
        assertNotNull(bitmap)
        assertEquals(256, maxOf(bitmap.width, bitmap.height))
    }

    @Test
    fun `a missing photo gives no picture instead of failing`() {
        assertNull(DailyBirdNotification.decodeAsset(context.assets, "images/Q0/hero.webp", maxEdgePx = 256))
        assertNull(DailyBirdNotification.loadPicture(context, "Q0/hero.webp"))
    }

    @Test
    fun `the sample size keeps the long edge at or above the target`() {
        assertEquals(2, DailyBirdNotification.sampleSizeFor(2400, 1600, 1024))
        assertEquals(1, DailyBirdNotification.sampleSizeFor(1000, 700, 1024))
        assertEquals(4, DailyBirdNotification.sampleSizeFor(4096, 3072, 1024))
    }
}
