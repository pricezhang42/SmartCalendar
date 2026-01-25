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
    private var inputFragment: AIInputFragment? = null
    private var previewFragment: AIPreviewFragment? = null

    companion object {
        private const val TAG_INPUT = "ai_input"
        private const val TAG_PREVIEW = "ai_preview"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_assistant)

        currentSessionId = savedInstanceState?.getString("session_id")
        inputFragment = supportFragmentManager.findFragmentByTag(TAG_INPUT) as? AIInputFragment
        previewFragment = supportFragmentManager.findFragmentByTag(TAG_PREVIEW) as? AIPreviewFragment

        if (savedInstanceState == null) {
            showInputFragment()
        } else {
            updateVisibleFragment()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("session_id", currentSessionId)
        super.onSaveInstanceState(outState)
    }

    private fun showInputFragment() {
        if (inputFragment == null) {
            inputFragment = AIInputFragment().apply {
                onSessionCreated = { sessionId ->
                    currentSessionId = sessionId
                    ensurePreviewFragment(sessionId)
                }
                onReviewRequested = { sessionId ->
                    currentSessionId = sessionId
                    showPreviewFragment(sessionId)
                }
                onClose = {
                    finish()
                }
            }
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, inputFragment!!, TAG_INPUT)
                .commit()
        } else {
            val transaction = supportFragmentManager.beginTransaction()
            transaction.show(inputFragment!!)
            previewFragment?.let { transaction.hide(it) }
            transaction.commit()
        }
    }

    private fun ensurePreviewFragment(sessionId: String) {
        if (previewFragment == null) {
            previewFragment = AIPreviewFragment.newInstance(sessionId).apply {
                onEventClick = { event ->
                    showEventEditModal(event)
                }
                onComplete = {
                    setResult(RESULT_OK)
                    finish()
                }
                onBack = {
                    showInputFragment()
                }
            }
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, previewFragment!!, TAG_PREVIEW)
                .hide(previewFragment!!)
                .commit()
        } else {
            previewFragment?.updateSession(sessionId)
        }
    }

    private fun showPreviewFragment(sessionId: String) {
        ensurePreviewFragment(sessionId)
        val transaction = supportFragmentManager.beginTransaction()
        inputFragment?.let { transaction.hide(it) }
        previewFragment?.let { transaction.show(it) }
        transaction.commit()
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

    private fun updateVisibleFragment() {
        val showingPreview = previewFragment?.isVisible == true
        val sessionId = currentSessionId
        if (showingPreview && sessionId != null) {
            showPreviewFragment(sessionId)
        } else {
            showInputFragment()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (previewFragment?.isVisible == true) {
            showInputFragment()
            return
        }
        super.onBackPressed()
    }
}
