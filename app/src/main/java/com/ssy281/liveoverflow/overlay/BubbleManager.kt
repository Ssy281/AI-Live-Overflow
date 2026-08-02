package com.ssy281.liveoverflow.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.LinearLayout
import android.util.TypedValue

class BubbleManager(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: View? = null
    private val handler = Handler(Looper.getMainLooper())

        fun showBubble(text: String, style: BubbleStyle, overlayY: Int = 120) {
        val bubbleY = if (overlayY > 180) overlayY - 160 else 80
        handler.post {
            removeBubble()

            val tv = TextView(context).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(24, 18, 24, 18)
                setBackgroundColor(style.bgColor)
                setTextColor(style.textColor)
                gravity = Gravity.CENTER
                elevation = 8f
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = bubbleY
            }

            wm.addView(tv, params)
            bubbleView = tv

            val fadeIn = ObjectAnimator.ofFloat(tv, "alpha", 0f, 1f)
            val slideDown = ObjectAnimator.ofFloat(tv, "translationY", -30f, 0f)
            AnimatorSet().apply {
                playTogether(fadeIn, slideDown)
                duration = 300
                start()
            }

            handler.postDelayed({ removeBubble() }, 4000)
        }
    }

    fun removeBubble() {
        bubbleView?.let { v ->
            val fadeOut = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f).apply { duration = 200 }
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    try { wm.removeView(v) } catch (_: Exception) {}
                }
            })
            fadeOut.start()
        }
        bubbleView = null
    }
}

data class BubbleStyle(val bgColor: Int, val textColor: Int) {
    companion object {
        val NORMAL = BubbleStyle(0xFFF5F5F5.toInt(), 0xFF333333.toInt())
        val HEART = BubbleStyle(0xFFFFE0E6.toInt(), 0xFFC0392B.toInt())
        val WHISPER = BubbleStyle(0xFFE8E8E8.toInt(), 0xFF666666.toInt())
        val ANGRY = BubbleStyle(0xFFFF4444.toInt(), 0xFFFFFFFF.toInt())
        val JEALOUS = BubbleStyle(0xFFE0FFE0.toInt(), 0xFF2E7D32.toInt())
    }
}
