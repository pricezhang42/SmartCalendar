package com.example.smartcalendar.ui.ai

import androidx.lifecycle.ViewModel

/**
 * ViewModel to persist AI chat state across tab switches.
 * Scoped to the Activity so it survives fragment recreation.
 */
class CaliViewModel : ViewModel() {
    val messages = mutableListOf<ChatMessage>()
    var currentSessionId: String? = null
    var isInPreviewMode: Boolean = false
}
