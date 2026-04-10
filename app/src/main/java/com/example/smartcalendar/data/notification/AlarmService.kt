package com.example.smartcalendar.data.notification

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smartcalendar.R

/**
 * Foreground service that keeps the process alive while launching the full-screen AlarmActivity.
 * BroadcastReceivers have limited execution time and can't reliably start activities from background.
 * A foreground service solves both problems:
 * 1. Keeps the process alive (won't be killed by the system)
 * 2. Has permission to start activities from background
 */
class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val NOTIFICATION_ID = 99999

        fun start(context: Context, intent: Intent) {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtras(intent)
            }
            Log.d(TAG, "Starting AlarmService...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "AlarmService start requested")
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // Use SCREEN_BRIGHT_WAKE_LOCK to forcibly turn on the screen
        // PARTIAL_WAKE_LOCK ignores ACQUIRE_CAUSES_WAKEUP — the screen stays off
        // SCREEN_BRIGHT_WAKE_LOCK is deprecated but is the only reliable way on OEM devices (Vivo, Xiaomi, etc.)
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "SmartCalendar:AlarmServiceLock"
        )
        wakeLock?.acquire(5 * 60 * 1000L) // 5 minutes max
        Log.d(TAG, "Screen wake lock acquired")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps alarm alive while displaying"
                setBypassDnd(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        // Build the full-screen intent for AlarmActivity
        val activityIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent?.extras?.let { putExtras(it) }
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = intent?.getStringExtra(ReminderManager.EXTRA_EVENT_TITLE) ?: "Alarm"

        // Notification with fullScreenIntent — system launches AlarmActivity when screen is off
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_bell)
            .setContentTitle(title)
            .setContentText("Alarm is ringing...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Foreground notification with fullScreenIntent posted")

        // Also try launching activity directly (works when screen is on)
        try {
            startActivity(activityIntent)
            Log.d(TAG, "AlarmActivity launched directly from service")
        } catch (e: Exception) {
            Log.w(TAG, "Direct activity launch failed (expected on some devices): ${e.message}")
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        super.onDestroy()
    }
}
