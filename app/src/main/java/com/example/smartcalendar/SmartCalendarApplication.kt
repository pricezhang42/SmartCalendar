package com.example.smartcalendar

import android.app.Application
import com.example.smartcalendar.data.remote.SupabaseClient

/**
 * Application class for initializing global services.
 */
class SmartCalendarApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Supabase client with application context
        SupabaseClient.init(this)
    }
}
