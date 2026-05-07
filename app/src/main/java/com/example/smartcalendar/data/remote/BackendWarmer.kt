package com.example.smartcalendar.data.remote

import android.util.Log
import com.example.smartcalendar.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Fire-and-forget GET ${BACKEND_URL}/health to wake up an idle Azure App Service
 * before the user actually needs the backend. Cold starts on the F1 free tier can
 * take 5-30s; firing this when the AI Assistant opens lets that delay overlap
 * with user think-time instead of with their voice/AI request.
 *
 * No-op if BACKEND_URL isn't configured. All errors are swallowed (best effort).
 */
object BackendWarmer {

    private const val TAG = "BackendWarmer"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                }
            }
        }
    }

    fun warmup() {
        if (BuildConfig.BACKEND_URL.isEmpty()) return
        scope.launch {
            val started = System.currentTimeMillis()
            try {
                val response = httpClient.get("${BuildConfig.BACKEND_URL}/health")
                val elapsed = System.currentTimeMillis() - started
                Log.d(TAG, "Warmup ${response.status} in ${elapsed}ms")
            } catch (e: Exception) {
                Log.d(TAG, "Warmup failed (non-fatal): ${e.message}")
            }
        }
    }
}
