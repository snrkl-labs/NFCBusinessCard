package org.snrkl.nfcbusinesscard.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks up the context chain to the hosting [Activity].
 *
 * `LocalContext` inside a composable is frequently a `ContextThemeWrapper`, not
 * the activity itself, so a direct cast works right up until it does not.
 * Returns null rather than throwing: the only callers are window-level effects
 * that should degrade quietly if there is no window to act on, such as under a
 * screenshot test harness.
 */
internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
