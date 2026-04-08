package com.example.smartcalendar.ui.ai

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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

    companion object {
        const val TYPING_INDICATOR = "•  •  •"
        private val TYPING_FRAMES = arrayOf("•", "•  •", "•  •  •")
    }

    inner class ViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var typingHandler: Handler? = null
        private var typingRunnable: Runnable? = null

        fun bind(message: ChatMessage) {
            // Stop any previous typing animation
            stopTypingAnimation()

            if (message.text == TYPING_INDICATOR) {
                startTypingAnimation()
            } else {
                binding.messageText.text = message.text
            }
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

            val bubbleColor: Int
            val textColor: Int

            when {
                message.isError -> {
                    bubbleColor = ContextCompat.getColor(binding.root.context, R.color.ai_confidence_low)
                    textColor = Color.WHITE
                }
                message.role == ChatRole.USER -> {
                    bubbleColor = ContextCompat.getColor(binding.root.context, R.color.primary_blue)
                    textColor = Color.WHITE
                }
                else -> {
                    // AI messages: light gray bubble with dark text (matches Figma)
                    bubbleColor = Color.parseColor("#EDEDED")
                    textColor = Color.parseColor("#1A1A1A")
                }
            }

            background.setColor(bubbleColor)
            binding.messageContainer.background = background
            binding.messageText.setTextColor(textColor)
        }

        private fun startTypingAnimation() {
            var frame = 0
            typingHandler = Handler(Looper.getMainLooper())
            typingRunnable = object : Runnable {
                override fun run() {
                    binding.messageText.text = TYPING_FRAMES[frame % TYPING_FRAMES.size]
                    frame++
                    typingHandler?.postDelayed(this, 400)
                }
            }
            typingRunnable?.run()
        }

        private fun stopTypingAnimation() {
            typingRunnable?.let { typingHandler?.removeCallbacks(it) }
            typingHandler = null
            typingRunnable = null
        }
    }
}
