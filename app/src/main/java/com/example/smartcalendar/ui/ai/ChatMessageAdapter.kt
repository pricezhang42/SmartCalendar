package com.example.smartcalendar.ui.ai

import android.graphics.Color
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

    companion object {
        const val TYPING_INDICATOR = "•••"
        private val TYPING_FRAMES = arrayOf("•", "••", "•••")
        private const val TYPING_DELAY_MS = 400L
        private const val SIDE_MARGIN_DP = 48
        private const val CORNER_RADIUS_DP = 18f
        private val AI_BUBBLE_COLOR = Color.parseColor("#EDEDED")
        private val AI_TEXT_COLOR = Color.parseColor("#1A1A1A")
    }

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
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.stopTypingAnimation()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val density = binding.root.resources.displayMetrics.density
        private val sideMargin = (SIDE_MARGIN_DP * density).toInt()
        private val cornerRadius = CORNER_RADIUS_DP * density

        private var typingRunnable: Runnable? = null

        fun bind(message: ChatMessage) {
            stopTypingAnimation()

            if (message.text == TYPING_INDICATOR) {
                startTypingAnimation()
            } else {
                binding.messageText.text = message.text
            }

            val params = (binding.messageContainer.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            val isUser = message.role == ChatRole.USER
            params.gravity = if (isUser) Gravity.END else Gravity.START
            params.marginStart = if (isUser) sideMargin else 0
            params.marginEnd = if (isUser) 0 else sideMargin
            binding.messageContainer.layoutParams = params

            val background = binding.messageContainer.background as? GradientDrawable
                ?: GradientDrawable().apply { this.cornerRadius = this@ViewHolder.cornerRadius }

            val (bubbleColor, textColor) = when {
                message.isError -> ContextCompat.getColor(binding.root.context, R.color.ai_confidence_low) to Color.WHITE
                isUser -> ContextCompat.getColor(binding.root.context, R.color.primary_blue) to Color.WHITE
                else -> AI_BUBBLE_COLOR to AI_TEXT_COLOR
            }

            background.setColor(bubbleColor)
            binding.messageContainer.background = background
            binding.messageText.setTextColor(textColor)
        }

        private fun startTypingAnimation() {
            // Set initial text immediately so the bubble isn't empty
            binding.messageText.text = TYPING_FRAMES[0]
            var frame = 1
            typingRunnable = object : Runnable {
                override fun run() {
                    if (!binding.root.isAttachedToWindow) return
                    binding.messageText.text = TYPING_FRAMES[frame % TYPING_FRAMES.size]
                    frame++
                    binding.root.postDelayed(this, TYPING_DELAY_MS)
                }
            }
            // Post the first animation frame instead of running synchronously
            binding.root.postDelayed(typingRunnable!!, TYPING_DELAY_MS)
        }

        fun stopTypingAnimation() {
            typingRunnable?.let { binding.root.removeCallbacks(it) }
            typingRunnable = null
        }
    }
}
