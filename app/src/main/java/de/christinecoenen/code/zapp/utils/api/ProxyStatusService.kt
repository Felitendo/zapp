package de.christinecoenen.code.zapp.utils.api

import android.content.Context
import timber.log.Timber

/**
 * Service to handle proxy status notifications and user feedback.
 */
class ProxyStatusService(private val context: Context) {

    companion object {
        private var lastNotificationTime = 0L
        private const val NOTIFICATION_COOLDOWN_MS = 30000L // 30 seconds
    }

    fun onProxyUsed(url: String) {
        val currentTime = System.currentTimeMillis()
        
        // Only log periodically to avoid spam
        if (currentTime - lastNotificationTime > NOTIFICATION_COOLDOWN_MS) {
            Timber.i("Connected to Austrian proxy for ORF channel access: $url")
            lastNotificationTime = currentTime
        }
    }
}