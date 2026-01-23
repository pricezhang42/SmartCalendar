package com.example.smartcalendar.ui.ai

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartcalendar.R
import com.example.smartcalendar.data.model.PendingEvent
import com.example.smartcalendar.data.model.PendingOperation
import com.example.smartcalendar.databinding.ItemPendingEventBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying pending AI-extracted events.
 */
class PendingEventAdapter(
    private val onEventClick: (PendingEvent) -> Unit,
    private val onEventLongClick: (PendingEvent) -> Unit,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (String) -> Boolean,
    private val onSelectionToggle: (PendingEvent) -> Unit
) : ListAdapter<PendingEvent, PendingEventAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPendingEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPendingEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: PendingEvent) {
            binding.eventTitle.text = event.title
            binding.actionText.text = when (event.operationType) {
                PendingOperation.CREATE -> binding.root.context.getString(R.string.ai_action_create)
                PendingOperation.UPDATE -> binding.root.context.getString(R.string.ai_action_update)
                PendingOperation.DELETE -> binding.root.context.getString(R.string.ai_action_delete)
            }

            val actionColor = when (event.operationType) {
                PendingOperation.CREATE -> ContextCompat.getColor(binding.root.context, R.color.primary_blue)
                PendingOperation.UPDATE -> ContextCompat.getColor(binding.root.context, R.color.ai_confidence_medium)
                PendingOperation.DELETE -> ContextCompat.getColor(binding.root.context, R.color.ai_confidence_low)
            }
            (binding.actionText.background as? GradientDrawable)?.setColor(actionColor)
                ?: run {
                    val drawable = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 16f * binding.root.context.resources.displayMetrics.density
                        setColor(actionColor)
                    }
                    binding.actionText.background = drawable
                }

            val selectionMode = isSelectionMode()
            val selected = isSelected(event.id)
            binding.selectionCheck.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.selectionCheck.isChecked = selected
            binding.selectionCheck.setOnClickListener {
                onSelectionToggle(event)
            }

            val strokeWidth = if (selected) {
                (2 * binding.root.context.resources.displayMetrics.density).toInt()
            } else {
                0
            }
            binding.eventCard.strokeWidth = strokeWidth
            binding.eventCard.strokeColor = ContextCompat.getColor(binding.root.context, R.color.primary_blue)

            // Format date/time
            val dateTimeText = formatDateTime(event)
            binding.eventDateTime.text = dateTimeText

            // Location
            if (!event.location.isNullOrBlank()) {
                binding.locationContainer.visibility = View.VISIBLE
                binding.eventLocation.text = event.location
            } else {
                binding.locationContainer.visibility = View.GONE
            }

            // Recurrence
            if (!event.recurrenceRule.isNullOrBlank()) {
                binding.recurrenceContainer.visibility = View.VISIBLE
                binding.eventRecurrence.text = formatRecurrence(event.recurrenceRule)
            } else {
                binding.recurrenceContainer.visibility = View.GONE
            }

            // Confidence indicator
            val confidencePercent = (event.confidence * 100).toInt()
            binding.confidenceText.text = "$confidencePercent%"

            val confidenceColor = when {
                event.confidence >= 0.8f -> ContextCompat.getColor(binding.root.context, R.color.ai_confidence_high)
                event.confidence >= 0.5f -> ContextCompat.getColor(binding.root.context, R.color.ai_confidence_medium)
                else -> ContextCompat.getColor(binding.root.context, R.color.ai_confidence_low)
            }

            (binding.confidenceText.background as? GradientDrawable)?.setColor(confidenceColor)
                ?: run {
                    val drawable = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 16f * binding.root.context.resources.displayMetrics.density
                        setColor(confidenceColor)
                    }
                    binding.confidenceText.background = drawable
                }
            binding.confidenceText.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.white))

            // Click listener
            binding.eventCard.setOnClickListener {
                if (selectionMode) {
                    onSelectionToggle(event)
                } else {
                    onEventClick(event)
                }
            }
            binding.eventCard.setOnLongClickListener {
                onEventLongClick(event)
                true
            }

            if (event.operationType == PendingOperation.DELETE) {
                binding.editHintText.text = binding.root.context.getString(R.string.ai_tap_to_review)
            } else {
                binding.editHintText.text = binding.root.context.getString(R.string.ai_tap_to_edit)
            }
        }

        private fun formatDateTime(event: PendingEvent): String {
            if (event.startTime == null) {
                return "Time not specified"
            }

            val startDate = Date(event.startTime)
            val dateStr = dateFormat.format(startDate)

            return if (event.isAllDay) {
                "$dateStr (All day)"
            } else {
                val startTimeStr = timeFormat.format(startDate)
                val endTimeStr = event.endTime?.let { timeFormat.format(Date(it)) }
                if (endTimeStr != null) {
                    "$dateStr, $startTimeStr - $endTimeStr"
                } else {
                    "$dateStr, $startTimeStr"
                }
            }
        }

        private fun formatRecurrence(rrule: String?): String {
            if (rrule.isNullOrBlank()) return ""

            val parts = rrule.split(";").associate {
                val kv = it.split("=")
                if (kv.size == 2) kv[0] to kv[1] else "" to ""
            }

            val freq = parts["FREQ"] ?: return "Repeating"
            val byday = parts["BYDAY"]

            return when (freq) {
                "DAILY" -> "Repeats daily"
                "WEEKLY" -> {
                    if (byday != null) {
                        val days = byday.split(",").map { dayCodeToName(it) }
                        "Weekly on ${days.joinToString(", ")}"
                    } else {
                        "Repeats weekly"
                    }
                }
                "MONTHLY" -> "Repeats monthly"
                "YEARLY" -> "Repeats yearly"
                else -> "Repeating"
            }
        }

        private fun dayCodeToName(code: String): String {
            return when (code) {
                "MO" -> "Mon"
                "TU" -> "Tue"
                "WE" -> "Wed"
                "TH" -> "Thu"
                "FR" -> "Fri"
                "SA" -> "Sat"
                "SU" -> "Sun"
                else -> code
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PendingEvent>() {
        override fun areItemsTheSame(oldItem: PendingEvent, newItem: PendingEvent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PendingEvent, newItem: PendingEvent): Boolean {
            return oldItem == newItem
        }
    }
}
