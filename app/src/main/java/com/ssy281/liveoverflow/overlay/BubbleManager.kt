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
import com.ssy281.liveoverflow.R

class BubbleManager(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    /** 手动皮肤覆盖：非空时所有气泡固定用该皮肤，null = 随情绪自动选择 */
    @Volatile
    private var skinOverride: BubbleStyle? = null

    fun setSkinOverride(style: BubbleStyle?) {
        skinOverride = style
    }

    fun isSkinOverridden(): Boolean = skinOverride != null

    private fun dp(v: Float): Int = (v * context.resources.displayMetrics.density).toInt()

    fun showBubble(text: String, style: BubbleStyle, overlayY: Int = 120) {
        val active = skinOverride ?: style
        val bubbleY = if (overlayY > 180) overlayY - 160 else 80
        handler.post {
            removeBubble()

            val tv = TextView(context).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setBackgroundResource(active.bgRes)
                setTextColor(active.textColor)
                setPadding(dp(active.padL), dp(active.padT), dp(active.padR), dp(active.padB))
                minWidth = dp(active.minW)
                minHeight = dp(active.minH)
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

/**
 * 气泡皮肤 = 手绘9-patch底图 + 文字配色 + 内容留白(contentInsets) + 最小尺寸(原图dp尺寸)
 * 数据源自 Cute-bubbles (zaochuanyitian)：capInsets 决定拉伸保护(装饰不变形)，
 * contentInsets 决定文字距边距离。9-patch 图在 drawable-nodpi/。
 */
data class BubbleStyle(
    val bgRes: Int,
    val textColor: Int,
    val padL: Float, val padT: Float, val padR: Float, val padB: Float,
    val minW: Float, val minH: Float
) {
    companion object {
        /** 日常提醒：光环·灰（深灰光晕渐变，白字） */
        val NORMAL = BubbleStyle(
            R.drawable.bubble_halo_gray, 0xFFFFFFFF.toInt(),
            22.0f, 19.0f, 22.7f, 14.7f, 142f, 60f
        )

        /** 心动：光环·粉（浅粉光晕，深红字） */
        val HEART = BubbleStyle(
            R.drawable.bubble_halo_pink, 0xFFC0392B.toInt(),
            22.3f, 14.3f, 23.0f, 17.0f, 142f, 60f
        )

        /** 悄悄话：偷看猫（粉白底，猫猫从右边探头，暖褐字） */
        val WHISPER = BubbleStyle(
            R.drawable.bubble_peek_cat, 0xFF5C4040.toInt(),
            22.7f, 20.0f, 30.0f, 9.0f, 141f, 60f
        )

        /** 生气：漫画框（黑白粗线漫画格，白字咆哮） */
        val ANGRY = BubbleStyle(
            R.drawable.bubble_comic, 0xFFFFFFFF.toInt(),
            18.0f, 14.0f, 19.0f, 21.7f, 110f, 60f
        )

        /** 吃醋：月亮兔（夜色淡紫，深紫字） */
        val JEALOUS = BubbleStyle(
            R.drawable.bubble_moon_bunny, 0xFF453B78.toInt(),
            34.3f, 15.7f, 29.3f, 26.0f, 126f, 60f
        )
    }
}
