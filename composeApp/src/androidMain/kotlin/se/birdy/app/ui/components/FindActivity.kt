package se.birdy.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind a (possibly wrapped) Context; shared by the system bar icon actuals. */
internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
