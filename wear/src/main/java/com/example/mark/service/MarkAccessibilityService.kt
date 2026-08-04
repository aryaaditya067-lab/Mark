package com.example.mark.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Exists only so Mark can press Home / Back on the user's behalf. Android does
 * not let one app close another — the most it allows is navigating away, and
 * only through an accessibility service the user has explicitly enabled.
 */
class MarkAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not needed */ }
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: MarkAccessibilityService? = null

        /** Press Home. Returns false if the service is not enabled. */
        fun goHome(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false

        fun goBack(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false

        val isEnabled: Boolean get() = instance != null
    }
}
