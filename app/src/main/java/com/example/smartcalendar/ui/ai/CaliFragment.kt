package com.example.smartcalendar.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.PendingEvent
import com.example.smartcalendar.ui.event.EventModalFragment

/**
 * Container fragment for the Cali AI assistant tab.
 * Replaces AIAssistantActivity's role for the bottom nav integration.
 * Manages AIInputFragment and AIPreviewFragment as child fragments.
 */
class CaliFragment : Fragment() {

    private val viewModel: CaliViewModel by activityViewModels()

    private var inputFragment: AIInputFragment? = null
    private var previewFragment: AIPreviewFragment? = null

    companion object {
        private const val TAG_INPUT = "cali_input"
        private const val TAG_PREVIEW = "cali_preview"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_cali, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputFragment = childFragmentManager.findFragmentByTag(TAG_INPUT) as? AIInputFragment
        previewFragment = childFragmentManager.findFragmentByTag(TAG_PREVIEW) as? AIPreviewFragment

        if (savedInstanceState == null && inputFragment == null) {
            showInputFragment()
        } else {
            wireCallbacks()
            updateVisibleFragment()
        }
    }

    private fun wireInputCallbacks(fragment: AIInputFragment) {
        fragment.onSessionCreated = { sessionId ->
            viewModel.currentSessionId = sessionId
            ensurePreviewFragment(sessionId)
        }
        fragment.onReviewRequested = { sessionId ->
            viewModel.currentSessionId = sessionId
            viewModel.isInPreviewMode = true
            showPreviewFragment(sessionId)
        }
        fragment.onClose = {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    private fun wirePreviewCallbacks(fragment: AIPreviewFragment) {
        fragment.onEventClick = { event -> showEventEditModal(event) }
        fragment.onComplete = { onAssistantComplete() }
        fragment.onBack = {
            viewModel.isInPreviewMode = false
            showInputFragment()
        }
    }

    private fun wireCallbacks() {
        inputFragment?.let { wireInputCallbacks(it) }
        previewFragment?.let { wirePreviewCallbacks(it) }
    }

    private fun showInputFragment() {
        if (inputFragment == null) {
            inputFragment = AIInputFragment().also { wireInputCallbacks(it) }
            childFragmentManager.beginTransaction()
                .add(R.id.caliFragmentContainer, inputFragment!!, TAG_INPUT)
                .commit()
        } else {
            val transaction = childFragmentManager.beginTransaction()
            transaction.show(inputFragment!!)
            previewFragment?.let { transaction.hide(it) }
            transaction.commit()
        }
    }

    private fun ensurePreviewFragment(sessionId: String) {
        if (previewFragment == null) {
            previewFragment = AIPreviewFragment.newInstance(sessionId).also { wirePreviewCallbacks(it) }
            childFragmentManager.beginTransaction()
                .add(R.id.caliFragmentContainer, previewFragment!!, TAG_PREVIEW)
                .hide(previewFragment!!)
                .commit()
        }
    }

    private fun showPreviewFragment(sessionId: String) {
        ensurePreviewFragment(sessionId)
        previewFragment?.updateSession(sessionId)
        val transaction = childFragmentManager.beginTransaction()
        inputFragment?.let { transaction.hide(it) }
        previewFragment?.let { transaction.show(it) }
        transaction.commit()
    }

    private fun showEventEditModal(event: PendingEvent) {
        val modal = EventModalFragment.newInstanceForPending(event)
        modal.onPendingEventSave = { updatedEvent ->
            previewFragment?.updateEvent(updatedEvent)
            Toast.makeText(requireContext(), "Event updated", Toast.LENGTH_SHORT).show()
        }
        modal.onPendingEventDelete = { eventId ->
            previewFragment?.removePendingEvent(eventId)
            Toast.makeText(requireContext(), "Change removed", Toast.LENGTH_SHORT).show()
        }
        modal.show(childFragmentManager, "event_modal")
    }

    private fun onAssistantComplete() {
        viewModel.isInPreviewMode = false
        viewModel.currentSessionId = null
        viewModel.messages.clear()
        // Navigate back to calendar
        activity?.onBackPressedDispatcher?.onBackPressed()
    }

    private fun updateVisibleFragment() {
        val sessionId = viewModel.currentSessionId
        if (viewModel.isInPreviewMode && sessionId != null) {
            showPreviewFragment(sessionId)
        } else {
            showInputFragment()
        }
    }

    fun handleBackPress(): Boolean {
        if (previewFragment?.isVisible == true) {
            viewModel.isInPreviewMode = false
            showInputFragment()
            return true
        }
        return false
    }

    // --- Audio Wave Overlay ---

    private var audioOverlay: AudioWaveOverlay? = null

    fun showAudioOverlay() {
        if (audioOverlay != null) return
        val overlay = AudioWaveOverlay(requireContext())
        overlay.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        (view as? ViewGroup)?.addView(overlay)
        overlay.show()
        audioOverlay = overlay
    }

    fun hideAudioOverlay() {
        audioOverlay?.hide {
            (view as? ViewGroup)?.removeView(audioOverlay)
            audioOverlay = null
        }
    }
}
