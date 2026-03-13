package com.wildtribe.drive.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wildtribe.drive.utils.DebugLogger

/**
 * Core accessibility service that monitors Google Maps navigation screen
 * and extracts turn direction, distance, ETA, and speed limit data.
 *
 * Data is broadcast via [ACTION_NAV_UPDATE] to DashboardActivity and BleService.
 *
 * APPROACH: Collects ALL text visible on the Maps screen (no hardcoded view IDs —
 * Maps updates frequently and IDs change), then passes to [NavigationParser].
 *
 * TEST: Accessibility service starts after permission granted
 * TEST: Maps navigation updates appear on Dashboard within 1 second
 */
class MapsAccessibilityService : AccessibilityService() {

    companion object {
        val MONITORED_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.google.android.apps.mapslite"
        )

        const val ACTION_NAV_UPDATE   = "com.wildtribe.drive.NAV_UPDATE"
        const val EXTRA_TURN          = "turn"
        const val EXTRA_DISTANCE      = "distance"
        const val EXTRA_ETA           = "eta"
        const val EXTRA_SPEED_LIMIT   = "speed_limit"
        const val EXTRA_REMAINING     = "remaining_dist"
        const val EXTRA_STREET        = "street"

        // Broadcast action for service active/inactive state
        const val ACTION_SERVICE_STATE = "com.wildtribe.drive.ACC_SERVICE_STATE"
        const val EXTRA_SERVICE_ACTIVE = "active"
    }

    private val parser = NavigationParser()
    private var lastBroadcastHash = 0

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_SCROLLED

            // Only monitor Google Maps — do NOT use hardcoded resource IDs
            packageNames = MONITORED_PACKAGES.toTypedArray()

            // Full window content for deep parsing
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // 500ms debounce — don't fire too often
            notificationTimeout = 500
        }

        DebugLogger.log("ACC_SERVICE", "Service connected — monitoring Google Maps")
        broadcastServiceState(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in MONITORED_PACKAGES) return

        // Collect all text from the Maps window
        val rootNode = rootInActiveWindow ?: return
        val allText = mutableListOf<String>()
        collectAllText(rootNode, allText)
        rootNode.recycle()

        if (allText.isEmpty()) return

        // Deduplicate — skip if identical to last broadcast
        val hash = allText.hashCode()
        if (hash == lastBroadcastHash) return
        lastBroadcastHash = hash

        // Parse into structured NavData
        val navData = parser.parse(allText)

        if (navData.hasAnyData()) {
            DebugLogger.log("ACC_NAV", "turn=${navData.turn} dist=${navData.distance} eta=${navData.eta}")
            sendBroadcast(Intent(ACTION_NAV_UPDATE).apply {
                setPackage(packageName)
                navData.turn?.let          { putExtra(EXTRA_TURN, it) }
                navData.distance?.let      { putExtra(EXTRA_DISTANCE, it) }
                navData.eta?.let           { putExtra(EXTRA_ETA, it) }
                navData.speedLimit?.let    { putExtra(EXTRA_SPEED_LIMIT, it) }
                navData.remainingDist?.let { putExtra(EXTRA_REMAINING, it) }
                navData.street?.let        { putExtra(EXTRA_STREET, it) }
            })
        }
    }

    /**
     * Recursively collect all visible text from the accessibility node tree.
     * We use text content only — NOT resource IDs — for Maps version resilience.
     */
    private fun collectAllText(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            collectAllText(node.getChild(i), out)
        }
    }

    override fun onInterrupt() {
        DebugLogger.log("ACC_SERVICE", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        broadcastServiceState(false)
        DebugLogger.log("ACC_SERVICE", "Service destroyed")
    }

    private fun broadcastServiceState(active: Boolean) {
        sendBroadcast(Intent(ACTION_SERVICE_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_SERVICE_ACTIVE, active)
        })
    }
}
