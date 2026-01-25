package com.example.smartcalendar.ui.ai

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.smartcalendar.R
import com.example.smartcalendar.databinding.ItemChatMessageBinding

class ChatMessageAdapter(
    private val items: MutableList<ChatMessage>
) : RecyclerView.Adapter<ChatMessageAdapter.ViewHolder>() {

    fun addMessage(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    fun replaceLast(message: ChatMessage) {
        if (items.isEmpty()) {
            addMessage(message)
        } else {
            items[items.size - 1] = message
            notifyItemChanged(items.size - 1)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.messageText.text = message.text
            val density = binding.root.resources.displayMetrics.density
            val sideMargin = (48 * density).toInt()
            val params = (binding.messageContainer.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

            params.gravity = if (message.role == ChatRole.USER) Gravity.END else Gravity.START
            params.marginStart = if (message.role == ChatRole.USER) sideMargin else 0
            params.marginEnd = if (message.role == ChatRole.USER) 0 else sideMargin
            binding.messageContainer.layoutParams = params

            val background = binding.messageContainer.background as? GradientDrawable
                ?: GradientDrawable().apply {
                    cornerRadius = 18f * binding.root.resources.displayMetrics.density
                }
            val colorRes = when {
                message.isError -> R.color.ai_confidence_low
                message.role == ChatRole.USER -> R.color.primary_blue
                else -> R.color.ai_confidence_medium
            }
            background.setColor(ContextCompat.getColor(binding.root.context, colorRes))
            binding.messageContainer.background = background
        }
    }
}
