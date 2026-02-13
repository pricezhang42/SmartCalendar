package com.example.smartcalendar.data.remote

import android.content.Context
import com.example.smartcalendar.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime

/**
 * Singleton Supabase client for auth and database operations.
 */
object SupabaseClient {

    private var _client: io.github.jan.supabase.SupabaseClient? = null

    fun init(context: Context) {
        if (_client == null) {
            _client = createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_KEY
            ) {
                install(Auth) {
                    // Session persistence is enabled by default in Supabase Kotlin SDK
                    // The SDK automatically saves and restores sessions from encrypted storage
                }
                install(Postgrest) {
                    // Database configuration
                }
                install(Realtime) {
                    // Real-time configuration
                }
            }
        }
    }

    val client: io.github.jan.supabase.SupabaseClient
        get() = _client ?: throw IllegalStateException("SupabaseClient must be initialized first. Call init(context) in Application class.")

    val auth get() = client.auth
    val postgrest get() = client.postgrest
    val realtime get() = client.realtime
}
