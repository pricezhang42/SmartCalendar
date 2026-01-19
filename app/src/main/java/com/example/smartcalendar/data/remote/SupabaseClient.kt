package com.example.smartcalendar.data.remote

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
    
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Auth) {
            // Auth configuration
        }
        install(Postgrest) {
            // Database configuration
        }
        install(Realtime) {
            // Real-time configuration
        }
    }
    
    val auth get() = client.auth
    val postgrest get() = client.postgrest
    val realtime get() = client.realtime
}
