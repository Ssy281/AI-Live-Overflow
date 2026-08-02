package com.ssy281.liveoverflow.overlay

import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.ssy281.liveoverflow.MainActivity
import com.ssy281.liveoverflow.R
import kotlinx.coroutines.*

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var overlayView: ImageView
    private var gestureEngine: GestureEngine? = null
    private var appDetector: AppDetector? = null
    private var heatSystem: HeatSystem? = null
    private var moodEngine: MoodEngine? = null
    private var bubbleManager: BubbleManager? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var heatOverlay: View? = null

    private val phrases = mapOf(
        "daily" to listOf("今天天气真好呀~", "想喝奶茶了...", "看到你就开心！"),
        "clingy" to listOf("你别走嘛~", "再陪我一会儿好不好？", "我一个人好无聊哦..."),
        "chaos" to listOf("啊啊啊好多事！", "脑袋要爆炸了！", "今天是什么日子来着？"),
        "night" to listOf("这么晚还不睡...", "晚安啦宝宝~", "偷偷跟你说，我有点困了...")
    )

    private val appReactions = mapOf(
        "com.taobao.taobao" to "又买东西！让我看看~",
        "com.jingdong.app.mall" to "京东？又要剁手了是吧！",
        "com.ss.android.ugc.aweme" to "刷抖音不陪我...我吃醋了！",
        "com.tencent.wework" to "上班加油哦~",
        "com.alibaba.android.rimet" to "钉钉又响了，快去处理~",
        "com.tencent.mm" to "回微信呢？记得回我消息哦！"
    )

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        bubbleManager = BubbleManager(this)
        moodEngine = MoodEngine()

        gestureEngine = GestureEngine(
            onClick = { onPetClick(1) },
            onDoubleClick = { onPetClick(2) },
            onLongPress = { onPetClick(3) },
            onFling = { bubbleManager?.showBubble("呜哇——！", BubbleStyle.ANGRY) }
        )

        appDetector = AppDetector(this) { pkg ->
            moodEngine?.let { engine ->
                val mood = engine.getMood(pkg, heatSystem?.heat ?: 0)
                val reaction = appReactions[pkg] ?: getPhraseForMood(mood)
                val style = when {
                    pkg.contains("taobao") || pkg.contains("jingdong") -> BubbleStyle.JEALOUS
                    pkg.contains("aweme") -> BubbleStyle.HEART
                    else -> BubbleStyle.NORMAL
                }
                bubbleManager?.showBubble(reaction, style)
            }
        }

        heatSystem = HeatSystem(
            onHeatChanged = { updateHeatOverlay(it) },
            onHeatMax = { bubbleManager?.showBubble("啊啊啊要爆炸了！！", BubbleStyle.ANGRY) }
        )

        appDetector?.start(scope)
        heatSystem?.start(scope)

        startSelfTalk()
        startIdleCheck()
        startScheduledBehavior()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, "overlay_channel")
            .setContentTitle("许星阔桌宠")
            .setContentText(getNotificationWhisper())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
        startNotificationWhisperUpdater()
        createOverlayView()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        appDetector?.stop()
        heatSystem?.stop()
        removeOverlayView()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_channel", "许星阔桌宠",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "桌宠前台服务通知" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createOverlayView() {
        overlayView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val params = WindowManager.LayoutParams(
            200, 200,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        wm.addView(overlayView, params)

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        overlayView.setOnTouchListener { v, event ->
            gestureEngine?.onTouch(v, event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    wm.updateViewLayout(overlayView, params)
                }
            }
            true
        }
    }

    private fun removeOverlayView() {
        try { wm.removeView(overlayView) } catch (_: Exception) {}
        try { wm.removeView(heatOverlay) } catch (_: Exception) {}
    }

    private fun onPetClick(taps: Int) {
        heatSystem?.addHeat(taps * 5)
        moodEngine?.onInteract()
        val msg = when (taps) {
            1 -> listOf("嗯？", "诶嘿~", "戳我干嘛！", "别闹~").random()
            2 -> listOf("啊啊别戳了！", "痒死啦！", "讨厌！").random()
            3 -> "……你到底要戳几下啦！"
            else -> "呜哇哇哇！！"
        }
        bubbleManager?.showBubble(msg, BubbleStyle.HEART)
    }

    private fun updateHeatOverlay(heat: Int) {
        try { wm.removeView(heatOverlay) } catch (_: Exception) {}
        if (heat > 30) {
            heatOverlay = View(this).apply {
                setBackgroundColor(Color.argb((heat * 2.55 * 0.3).toInt(), 255, 0, 0))
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(heatOverlay, params)
        }
    }

    private fun getPhraseForMood(mood: MoodEngine.Mood): String {
        val pool = when (mood) {
            MoodEngine.Mood.SLEEPY -> phrases["night"]!!
            MoodEngine.Mood.LONELY -> phrases["clingy"]!!
            MoodEngine.Mood.HAPPY -> phrases["daily"]!!
            else -> phrases["daily"]!!
        }
        return pool.random()
    }

    private fun startSelfTalk() {
        scope.launch {
            while (isActive) {
                delay((30_000..120_000).random().toLong())
                val mood = moodEngine?.getMood() ?: MoodEngine.Mood.HAPPY
                if (mood != MoodEngine.Mood.SLEEPY) {
                    bubbleManager?.showBubble(getPhraseForMood(mood), BubbleStyle.NORMAL)
                }
            }
        }
    }

    private fun startIdleCheck() {
        scope.launch {
            while (isActive) {
                delay(60_000)
                val mood = moodEngine?.getMood() ?: MoodEngine.Mood.HAPPY
                val idleMsg = when (mood) {
                    MoodEngine.Mood.LONELY -> "好无聊哦...你还不理我吗？"
                    MoodEngine.Mood.SHY -> "偷偷看你一眼..."
                    MoodEngine.Mood.SLEEPY -> "呼...呼..."
                    else -> null
                }
                idleMsg?.let { bubbleManager?.showBubble(it, BubbleStyle.WHISPER) }
            }
        }
    }

    private fun startScheduledBehavior() {
        scope.launch {
            while (isActive) {
                delay(1_200_000) // 20 min
                if (Math.random() < 0.3) {
                    bubbleManager?.showBubble("该喝水啦！", BubbleStyle.HEART)
                }
            }
        }
    }

    private val nightWhispers = listOf("夜深了...", "偷偷醒着陪你", "晚安世界")
    private val morningWhispers = listOf("早安！新的一天~", "今天也要开心哦", "阳光真好！")
    private val dayWhispers = listOf("在你身边真好", "正在守护你~", "有什么需要帮忙的吗？")

    private fun getNotificationWhisper(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> nightWhispers.random()
            in 6..9 -> morningWhispers.random()
            else -> dayWhispers.random()
        }
    }

    private fun startNotificationWhisperUpdater() {
        scope.launch {
            while (isActive) {
                delay(3_600_000)
                val nm = getSystemService(NotificationManager::class.java)
                val notification = NotificationCompat.Builder(this@OverlayService, "overlay_channel")
                    .setContentTitle("许星阔桌宠")
                    .setContentText(getNotificationWhisper())
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .build()
                nm.notify(1, notification)
            }
        }
    }
}
