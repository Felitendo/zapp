package de.christinecoenen.code.zapp.utils.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Helper class for testing proxy connections used for ORF channels.
 */
class ProxyTestHelper {

    companion object {
        private val AUSTRIAN_PROXIES = listOf(
            Proxy(Proxy.Type.HTTP, InetSocketAddress("45.91.94.197", 80)),
            Proxy(Proxy.Type.HTTP, InetSocketAddress("212.183.88.217", 80)),
            Proxy(Proxy.Type.HTTP, InetSocketAddress("212.183.88.198", 80))
        )
        
        // Use a simple Austrian website to test proxy connectivity
        private const val TEST_URL = "http://orf.at/"
        private const val TIMEOUT_MS = 10000
    }

    /**
     * Tests if Austrian proxy servers are accessible and working.
     * @return true if at least one proxy is working, false otherwise
     */
    suspend fun testProxyConnection(): Boolean = withContext(Dispatchers.IO) {
        for (proxy in AUSTRIAN_PROXIES) {
            try {
                val client = OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .callTimeout(TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()

                val request = Request.Builder()
                    .url(TEST_URL)
                    .head() // Use HEAD request to minimize data transfer
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Timber.d("Proxy test successful via ${proxy.address()}")
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Proxy test failed for ${proxy.address()}")
            }
        }
        
        Timber.w("All proxy tests failed")
        return@withContext false
    }
}