package com.example.smartcalendar.data.notification

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.smartcalendar.databinding.ActivityAlarmBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen alarm activity with sound and vibration.
 * Displayed when an alarm-type reminder triggers.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("AlarmActivity", "onCreate called")

        try {
            // Acquire wake lock to keep screen on
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "SmartCalendar:AlarmWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
            android.util.Log.d("AlarmActivity", "Wake lock acquired")

            // Show on lock screen and turn screen on
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }

            // Keep screen on while this window is visible
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Dismiss keyguard
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                keyguardManager?.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmActivity", "Error setting up alarm activity", e)
        }

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eventTitle = intent.getStringExtra(ReminderManager.EXTRA_EVENT_TITLE) ?: "Event"
        val eventStart = intent.getLongExtra(ReminderManager.EXTRA_EVENT_START, 0L)
        val reminderMinutes = intent.getIntExtra(ReminderManager.EXTRA_REMINDER_MINUTES, 0)

        // Display event information
        binding.eventTitle.text = eventTitle

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val startTime = timeFormat.format(Date(eventStart))

        val timeText = when (reminderMinutes) {
            0 -> "Starting now at $startTime"
            1 -> "In 1 minute at $startTime"
            else -> {
                val unit = when {
                    reminderMinutes % 10080 == 0 -> {
                        val weeks = reminderMinutes / 10080
                        if (weeks == 1) "1 week" else "$weeks weeks"
                    }
                    reminderMinutes % 1440 == 0 -> {
                        val days = reminderMinutes / 1440
                        if (days == 1) "1 day" else "$days days"
                    }
                    reminderMinutes % 60 == 0 -> {
                        val hours = reminderMinutes / 60
                        if (hours == 1) "1 hour" else "$hours hours"
                    }
                    else -> "$reminderMinutes minutes"
                }
                "In $unit at $startTime"
            }
        }
        binding.eventTime.text = timeText

        // Dismiss button
        binding.dismissButton.setOnClickListener {
            stopAlarmAndFinish()
        }

        // Snooze button (5 minutes)
        binding.snoozeButton.setOnClickListener {
            // TODO: Implement snooze functionality
            stopAlarmAndFinish()
        }

        // Start alarm sound and vibration
        startAlarmSound()
        startVibration()
    }

    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, alarmUri)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                }

                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                val effect = VibrationEffect.createWaveform(pattern, 0)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmAndFinish() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                android.util.Log.d("AlarmActivity", "Wake lock released")
            }
        }
        wakeLock = null

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmAndFinish()
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing alarm
        // User must explicitly dismiss or snooze
    }
}
