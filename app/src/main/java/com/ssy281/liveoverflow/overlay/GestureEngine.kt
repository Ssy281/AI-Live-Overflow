package com.ssy281.liveoverflow.overlay

import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class GestureEngine(
    private val onClick: () -> Unit,
    private val onDoubleClick: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onFling: () -> Unit
) {
    companion object {
        private const val CLICK_THRESHOLD = 200L
        private const val DOUBLE_CLICK_INTERVAL = 400L
        private const val LONG_PRESS_DURATION = 600L
        private const val FLING_VELOCITY = 3000f
        private const val TAP_COUNT_WINDOW = 2000L
    }

    private var downTime = 0L
    private var lastUpTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var tapCount = 0
    private var lastTapTime = 0L
    private val tapTimes = mutableListOf<Long>()

    fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                downX = event.rawX
                downY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                val now = System.currentTimeMillis()
                val elapsed = now - downTime
                val dx = abs(event.rawX - downX)
                val dy = abs(event.rawY - downY)
                val velocity = hypot(event.rawX - downX, event.rawY - downY) / (elapsed + 1).toFloat() * 1000f

                tapTimes.add(now)
                tapTimes.removeAll { now - it > TAP_COUNT_WINDOW }
                tapCount = tapTimes.size

                view.performClick()

                when {
                    elapsed > LONG_PRESS_DURATION && dx < 30 && dy < 30 -> {
                        onLongPress()
                        tapTimes.clear()
                        tapCount = 0
                    }
                    velocity > FLING_VELOCITY -> {
                        onFling()
                        tapTimes.clear()
                        tapCount = 0
                    }
                    now - lastUpTime < DOUBLE_CLICK_INTERVAL -> {
                        onDoubleClick()
                        tapTimes.clear()
                        tapCount = 0
                    }
                    elapsed < CLICK_THRESHOLD && dx < 30 && dy < 30 -> {
                        when {
                            tapCount >= 8 -> {
                                onClick()
                                tapTimes.clear()
                                tapCount = 0
                            }
                            tapCount >= 5 -> {
                                onClick()
                                tapTimes.clear()
                                tapCount = 0
                            }
                            tapCount >= 3 -> {
                                onClick()
                                tapTimes.clear()
                                tapCount = 0
                            }
                            else -> onClick()
                        }
                    }
                }
                lastUpTime = now
            }
        }
        return true
    }

    private fun hypot(a: Float, b: Float): Float = kotlin.math.sqrt((a * a + b * b).toDouble()).toFloat()
}
