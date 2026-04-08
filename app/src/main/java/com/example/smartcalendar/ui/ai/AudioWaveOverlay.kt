package com.example.smartcalendar.ui.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.example.smartcalendar.R

/**
 * Fullscreen overlay that shows animated ripple circles while recording audio.
 * Draws concentric expanding rings in primary_blue at decreasing opacity.
 */
class AudioWaveOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rippleCount = 4
    private val maxRadius: Float
        get() = width.coerceAtMost(height) * 0.35f

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.primary_blue)
    }

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary_blue)
    }

    private var animationProgress = 0f
    private var animator: ValueAnimator? = null

    init {
        // Inflate the overlay layout (labels + mic icon)
        LayoutInflater.from(context).inflate(R.layout.overlay_audio_wave, this, true)
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f - 40 * resources.displayMetrics.density // offset up slightly for icon

        // Center filled circle (mic background)
        circleFillPaint.alpha = 30
        canvas.drawCircle(cx, cy, 56 * resources.displayMetrics.density, circleFillPaint)

        // Expanding ripple rings
        for (i in 0 until rippleCount) {
            val phase = (animationProgress + i.toFloat() / rippleCount) % 1f
            val radius = 60 * resources.displayMetrics.density + phase * maxRadius
            val alpha = ((1f - phase) * 100).toInt().coerceIn(0, 255)

            ripplePaint.alpha = alpha
            canvas.drawCircle(cx, cy, radius, ripplePaint)
        }
    }

    fun show() {
        alpha = 0f
        visibility = VISIBLE
        animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    fun hide(onComplete: (() -> Unit)? = null) {
        animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                visibility = GONE
                onComplete?.invoke()
            }
            .start()
    }
}
