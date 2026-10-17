package com.a24solver.wakusei

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View

/**
 * A long press whose threshold this app chooses.
 *
 * `View.setOnLongClickListener` cannot be used for that: the platform measures the press with
 * `ViewConfiguration.getLongPressTimeout()`, a device-wide constant. So this watches the touch stream
 * instead — it arms a timer on ACTION_DOWN, fires when the configured time has passed, and swallows
 * the ACTION_UP that follows, so one press is either a tap or a long press and never both.
 *
 * The threshold is read through [thresholdMs] at the moment of the press rather than passed in once,
 * so a change in the settings screen applies to the next press without rebuilding the fragment.
 *
 * Taps are deliberately **not** handled here: the events that do not become a long press are returned
 * un-consumed, so the view's own click listener (and therefore `performClick`, and with it the
 * accessibility click action) runs exactly as it would without this class. That is why the
 * `ClickableViewAccessibility` lint is silenced below rather than "fixed" by calling `performClick`
 * from here, which would double every tap.
 */
@SuppressLint("ClickableViewAccessibility")
internal class LongPressGesture(
    private val view: View,
    private val thresholdMs: () -> Int,
    private val onLongPress: () -> Unit
) {

    /** Set once the timer has fired, so the release that follows is not also delivered as a tap. */
    private var fired = false

    private val timer = Runnable {
        fired = true
        // the view never sees ACTION_UP in this case, so it would otherwise stay in its pressed state
        view.isPressed = false
        onLongPress()
    }

    init {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    view.postDelayed(timer, thresholdMs().toLong())
                    false // let the key show its pressed state
                }

                MotionEvent.ACTION_MOVE -> {
                    // sliding off the key cancels the gesture, the way it cancels a tap
                    if (!isInside(event)) {
                        view.removeCallbacks(timer)
                        fired = false
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(timer)
                    val wasLongPress = fired
                    fired = false
                    wasLongPress // consumed only when the long press already ran
                }

                else -> false
            }
        }
    }

    private fun isInside(event: MotionEvent): Boolean =
        event.x >= 0f && event.y >= 0f && event.x < view.width && event.y < view.height
}
