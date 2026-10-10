package se.birdy.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import se.birdy.app.R
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The 08:00 Dagens fågel notification (release 1.3.0 Task 7d, the approved "notification" add-on):
 * title "Dagens fågel: Sävsångare", a line that invites a catch, the species photo (bundled with
 * the app, nothing downloaded) and two buttons, "Läs om arten" and "Lyssna efter den". Channel,
 * time and the tap-to-open-the-profile behaviour are unchanged.
 */
object DailyBirdNotification {
    const val NOTIFICATION_ID = 1001

    /** Set on the button intents; MainActivity dismisses this notification id when it sees it. */
    const val EXTRA_DISMISS_NOTIFICATION_ID = "se.birdy.extra.DISMISS_NOTIFICATION_ID"

    /** Longest edge of the decoded photo: sharp in the expanded notification, small in memory. */
    const val PICTURE_MAX_EDGE_PX = 1024

    /** Longest edge of the collapsed notification's thumbnail (the large icon). */
    const val THUMBNAIL_MAX_EDGE_PX = 256

    private const val REQUEST_CODE_CONTENT = NOTIFICATION_ID
    private const val REQUEST_CODE_FIRST_ACTION = 1100
    private const val TAG = "DailyBirdNotification"

    /** The photo for the expanded notification and a small copy for the collapsed one. */
    class Picture(
        val big: Bitmap,
        val thumbnail: Bitmap,
    )

    fun build(
        context: Context,
        content: NotificationContent,
        picture: Picture?,
    ): Notification {
        val builder =
            NotificationCompat
                .Builder(context, NotificationChannels.DAILY_BIRD)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .setContentIntent(deepLinkIntent(context, content.deepLink, REQUEST_CODE_CONTENT, dismiss = false))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (picture != null) {
            // Collapsed: the photo as a thumbnail with the invitation. Expanded: the big photo, no
            // duplicate thumbnail, and the photo's credit under the title (release 1.3.0 Task 7e-2:
            // a CC BY photo is never shown without its photographer and licence).
            builder
                .setLargeIcon(picture.thumbnail)
                .setStyle(
                    NotificationCompat
                        .BigPictureStyle()
                        .bigPicture(picture.big)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(content.photoCredit ?: content.body),
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
        }
        content.actions.forEachIndexed { index, action ->
            builder.addAction(
                0,
                action.label,
                deepLinkIntent(context, action.deepLink, REQUEST_CODE_FIRST_ACTION + index, dismiss = true),
            )
        }
        return builder.build()
    }

    /**
     * The species' hero photo from the bundled images (the install-time asset pack), scaled to at
     * most [PICTURE_MAX_EDGE_PX], plus its thumbnail. Null when it isn't there (debug APKs carry no
     * asset pack) or anything goes wrong decoding it: the notification then goes out without a
     * photo, with its text and buttons.
     */
    fun loadPicture(
        context: Context,
        imagePath: String,
    ): Picture? = pictureFrom { decodeAsset(context.assets, "images/$imagePath", PICTURE_MAX_EDGE_PX) }

    /**
     * Runs [decode] and adds the thumbnail. Any failure, out-of-memory included, gives null instead
     * of reaching the worker's catch-all (which would retry and send no notification at all).
     */
    @Suppress("TooGenericExceptionCaught") // a photo is optional: every failure degrades to no photo, logged.
    internal fun pictureFrom(decode: () -> Bitmap?): Picture? =
        try {
            decode()?.let { big -> Picture(big = big, thumbnail = scaledCopy(big, THUMBNAIL_MAX_EDGE_PX)) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "No photo for the daily-bird notification", t)
            null
        }

    internal fun decodeAsset(
        assets: AssetManager,
        assetPath: String,
        maxEdgePx: Int,
    ): Bitmap? = decodeStream(source = assetPath, maxEdgePx = maxEdgePx) { assets.open(assetPath) }

    /**
     * Decodes the image that [open] streams, sampled and scaled down to at most [maxEdgePx] on its
     * long edge. [open] is called twice (bounds, then pixels); [source] only names the image in the
     * log. Null when opening fails or the bytes are no image. Split out of [decodeAsset] so the unit
     * tests can decode a photo from disk: the debug-only benchmark photos never reach the merged
     * assets of the unit tests (1.3.0 legal review, fix F).
     */
    internal fun decodeStream(
        source: String,
        maxEdgePx: Int,
        open: () -> InputStream,
    ): Bitmap? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdgePx)
            val decoded =
                open().use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            decoded?.let { scaleDown(it, maxEdgePx) }
        } catch (e: IOException) {
            Log.i(TAG, "No photo for the notification at $source: ${e.message}")
            null
        }

    /** Largest power of two that keeps the decoded image at least [maxEdgePx] on its long edge. */
    internal fun sampleSizeFor(
        width: Int,
        height: Int,
        maxEdgePx: Int,
    ): Int {
        var sample = 1
        val longEdge = max(width, height)
        while (longEdge / (sample * 2) >= maxEdgePx) sample *= 2
        return sample
    }

    /** Scales [bitmap] down to [maxEdgePx] and recycles the original. */
    private fun scaleDown(
        bitmap: Bitmap,
        maxEdgePx: Int,
    ): Bitmap {
        val scaled = scaledCopy(bitmap, maxEdgePx)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** A copy of [bitmap] at most [maxEdgePx] on its long edge; [bitmap] itself when already small enough. */
    private fun scaledCopy(
        bitmap: Bitmap,
        maxEdgePx: Int,
    ): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxEdgePx) return bitmap
        val factor = maxEdgePx.toFloat() / longEdge
        val width = (bitmap.width * factor).roundToInt()
        val height = (bitmap.height * factor).roundToInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun deepLinkIntent(
        context: Context,
        deepLink: String,
        requestCode: Int,
        dismiss: Boolean,
    ): PendingIntent {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                .setPackage(context.packageName)
                .apply { if (dismiss) putExtra(EXTRA_DISMISS_NOTIFICATION_ID, NOTIFICATION_ID) }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
