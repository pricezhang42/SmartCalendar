package com.example.smartcalendar.ui.ai

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.PendingEvent
import com.example.smartcalendar.ui.event.EventModalFragment

/**
 * Activity that hosts the AI assistant flow:
 * 1. AIInputFragment - User enters text
 * 2. AIPreviewFragment - User reviews extracted events
 * 3. EventModalFragment - User edits individual events
 */
class AIAssistantActivity : AppCompatActivity() {

    private var currentSessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_assistant)

        if (savedInstanceState == null) {
            showInputFragment()
        }
    }

    private fun showInputFragment() {
        val inputFragment = AIInputFragment().apply {
            onSessionCreated = { sessionId ->
                currentSessionId = sessionId
                showPreviewFragment(sessionId)
            }
            onClose = {
                finish()
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, inputFragment)
            .commit()
    }

    private fun showPreviewFragment(sessionId: String) {
        val previewFragment = AIPreviewFragment.newInstance(sessionId).apply {
            onEventClick = { event ->
                showEventEditModal(event)
            }
            onComplete = {
                // Events approved or rejected
                setResult(RESULT_OK)
                finish()
            }
            onBack = {
                // Go back to input
                showInputFragment()
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, previewFragment)
            .addToBackStack("preview")
            .commit()
    }

    private fun showEventEditModal(event: PendingEvent) {
        val modal = EventModalFragment.newInstanceForPending(event)
        modal.onPendingEventSave = { updatedEvent ->
            // Find preview fragment and update the event
            val previewFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (previewFragment is AIPreviewFragment) {
                previewFragment.updateEvent(updatedEvent)
            }
            Toast.makeText(this, "Event updated", Toast.LENGTH_SHORT).show()
        }
        modal.onPendingEventDelete = { eventId ->
            val previewFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (previewFragment is AIPreviewFragment) {
                previewFragment.removePendingEvent(eventId)
            }
            Toast.makeText(this, "Change removed", Toast.LENGTH_SHORT).show()
        }
        modal.show(supportFragmentManager, "event_modal")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}
