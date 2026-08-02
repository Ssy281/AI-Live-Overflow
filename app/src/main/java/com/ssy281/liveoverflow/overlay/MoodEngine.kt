package com.ssy281.liveoverflow.overlay

import java.util.Calendar

class MoodEngine {
    enum class Mood { NORMAL, HAPPY, SHY, ANGRY, SLEEPY, LONELY, JEALOUS }

    private var idleMinutes = 0
    private var lastInteraction = System.currentTimeMillis()

    fun onInteract() {
        idleMinutes = 0
        lastInteraction = System.currentTimeMillis()
    }

    fun getMood(appPackage: String? = null, heat: Int = 0): Mood {
        val now = System.currentTimeMillis()
        val idleMs = now - lastInteraction
        idleMinutes = (idleMs / 60_000).toInt()

        return when {
            heat >= 80 -> Mood.ANGRY
            appPackage == "com.taobao.taobao" || appPackage == "com.jingdong.app.mall" -> Mood.JEALOUS
            appPackage == "com.ss.android.ugc.aweme" -> Mood.JEALOUS
            appPackage == "com.tencent.wework" || appPackage == "com.alibaba.android.rimet" -> Mood.NORMAL
            idleMinutes >= 30 -> Mood.SLEEPY
            idleMinutes >= 20 -> Mood.LONELY
            idleMinutes >= 15 -> Mood.LONELY
            idleMinutes >= 10 -> Mood.SHY
            idleMinutes >= 5 -> Mood.SHY
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 0..5 -> Mood.SLEEPY
            else -> Mood.HAPPY
        }
    }
}
