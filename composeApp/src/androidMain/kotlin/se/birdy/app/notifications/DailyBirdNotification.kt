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
import se.birdy.app.R
import java.io.IOException
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

    private const val REQUEST_CODE_CONTENT = NOTIFICATION_ID
    private const val REQUEST_CODE_FIRST_ACTION = 1100
    private const val TAG = "DailyBirdNotification"

    fun build(
        context: Context,
        content: NotificationContent,
        picture: Bitmap?,
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
            // Collapsed: the photo as a thumbnail. Expanded: the big photo, no duplicate thumbnail.
            builder
                .setLargeIcon(picture)
                .setStyle(
                    NotificationCompat
                        .BigPictureStyle()
                        .bigPicture(picture)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(content.body),
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
     * most [maxEdgePx]. Null when it isn't there (debug APKs carry no asset pack) or can't be
     * decoded: the notification then goes out without a photo.
     */
    fun loadPicture(
        context: Context,
        imagePath: String,
        maxEdgePx: Int = PICTURE_MAX_EDGE_PX,
    ): Bitmap? = decodeAsset(context.assets, "images/$imagePath", maxEdgePx)

    internal fun decodeAsset(
        assets: AssetManager,
        assetPath: String,
        maxEdgePx: Int,
    ): Bitmap? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdgePx)
            val decoded =
                assets.open(assetPath).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            decoded?.let { scaleDown(it, maxEdgePx) }
        } catch (e: IOException) {
            Log.i(TAG, "No photo for the notification at $assetPath: ${e.message}")
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

    private fun scaleDown(
        bitmap: Bitmap,
        maxEdgePx: Int,
    ): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxEdgePx) return bitmap
        val factor = maxEdgePx.toFloat() / longEdge
        val width = (bitmap.width * factor).roundToInt()
        val height = (bitmap.height * factor).roundToInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
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
