package de.christinecoenen.code.zapp.utils.api

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException
import timber.log.Timber

/**
 * Custom ProxySelector that applies Austrian proxy settings only for ORF channels.
 * This ensures ORF streams can be accessed through Austrian proxy servers.
 */
class OrfProxySelector : ProxySelector() {

    companion object {
        private val AUSTRIAN_PROXIES = listOf(
            Proxy(Proxy.Type.HTTP, InetSocketAddress("45.91.94.197", 80)),
            Proxy(Proxy.Type.HTTP, InetSocketAddress("212.183.88.217", 80)),
            Proxy(Proxy.Type.HTTP, InetSocketAddress("212.183.88.198", 80))
        )
        private val NO_PROXY = listOf(Proxy.NO_PROXY)
    }

    private var currentProxyIndex = 0

    override fun select(uri: URI?): List<Proxy> {
        if (uri == null) {
            return NO_PROXY
        }

        val url = uri.toString()
        
        // Check if this is an ORF channel request
        if (isOrfChannelRequest(url)) {
            Timber.d("Using Austrian proxy for ORF request: $url")
            // Return all Austrian proxies for failover
            return AUSTRIAN_PROXIES
        }

        // For non-ORF requests, use direct connection
        return NO_PROXY
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        if (uri != null && isOrfChannelRequest(uri.toString())) {
            Timber.w(ioe, "Connection failed for ORF request to $uri via proxy $sa")
            // Move to next proxy for next attempt
            currentProxyIndex = (currentProxyIndex + 1) % AUSTRIAN_PROXIES.size
        }
    }

    private fun isOrfChannelRequest(url: String): Boolean {
        return url.contains("mdn.ors.at")
    }
}