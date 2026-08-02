package com.ssy281.liveoverflow.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.ssy281.liveoverflow.MainActivity;
import com.ssy281.liveoverflow.R;

import java.util.List;
import java.util.Random;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;
    private TextView bubbleView;
    private WindowManager.LayoutParams bubbleParams;

    private float initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long downTime;
    private Handler handler;
    private String lastPkg = "";
    private String lastReaction = "";
    private long lastReactionTime = 0;

    private final Handler bubbleHandler = new Handler();
    private final Handler appCheckHandler = new Handler();
    private final Handler idleHandler = new Handler();
    private final Handler drinkReminderHandler = new Handler();

    private static final String[][] PHRASES = {
            {"戳我干嘛！", "嗯？", "诶嘿~", "在呢在呢"},
            {"许星阔！怎么啦？", "宝宝你戳到我了~", "嘶...好痒"},
            {"别揉啦，发型乱了！", "唔...轻点嘛", "抱抱就抱抱！", "呜哇，晕了晕了"},
    };

    private static final String[] IDLE_PHRASES = {
            "好无聊啊…", "宝宝在干嘛呢？", "想喝奶茶~", 
            "趴好了，等你来戳", "盯——", "喵..."
    };

    private final Random random = new Random();

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(1, buildNotification());
        createOverlayView();
        createBubbleView();
        startAppDetection();
        startIdleBubble();
        startDrinkReminder();
        // 检查使用情况访问权限
        checkUsageStatsPermission();
    }
    
    private void checkUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000, now);
            if (stats == null || stats.isEmpty()) {
                showBubble("给我「使用情况访问」权限，就能看到切应用啦~");
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "overlay_channel",
                    "许星阔桌宠",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("桌宠悬浮窗运行中");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, "overlay_channel")
                .setContentTitle("许星阔桌宠")
                .setContentText("正在运行中…")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createOverlayView() {
        overlayView = new ImageView(this);
        ((ImageView) overlayView).setImageResource(R.mipmap.ic_launcher);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        windowManager.addView(overlayView, params);

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = (int) (initialX + event.getRawX() - initialTouchX);
                        params.y = (int) (initialY + event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - downTime;
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (duration > 500) {
                            // 长按：按住超过半秒直接触发第三组撒娇台词，不受位移影响
                            onPetClick(3);
                        } else if (Math.abs(dx) < 15 && Math.abs(dy) < 15) {
                            // 短按：随机台词
                            onPetClick(random.nextInt(2) == 0 ? 1 : 2);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void onPetClick(int type) {
        String[] pool = PHRASES[Math.min(type - 1, PHRASES.length - 1)];
        String msg = pool[random.nextInt(pool.length)];
        showBubble(msg);
        Toast.makeText(OverlayService.this, "许星阔在此~", Toast.LENGTH_SHORT).show();
    }

    private void createBubbleView() {
        bubbleView = new TextView(this);
        bubbleView.setPadding(24, 14, 24, 14);
        bubbleView.setTextColor(Color.WHITE);
        bubbleView.setBackgroundColor(0xCCFF6B6B);
        bubbleView.setTextSize(14);
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setVisibility(View.GONE);
        bubbleView.setElevation(16f);

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = params.x;
        bubbleParams.y = params.y - 160; // 在悬浮窗上方

        windowManager.addView(bubbleView, bubbleParams);
    }

    private void showBubble(String text) {
        bubbleHandler.removeCallbacksAndMessages(null);
        bubbleView.setText(text);
        bubbleView.setVisibility(View.VISIBLE);
        // 位置跟随悬浮窗
        bubbleParams.x = params.x;
        bubbleParams.y = params.y - 160;
        try {
            windowManager.updateViewLayout(bubbleView, bubbleParams);
        } catch (Exception ignored) {}

        // 淡入动画
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(200);
        bubbleView.startAnimation(fadeIn);

        // 4秒后隐藏
        bubbleHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
                fadeOut.setDuration(300);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        bubbleView.setVisibility(View.GONE);
                    }
                    @Override public void onAnimationStart(Animation animation) {}
                    @Override public void onAnimationRepeat(Animation animation) {}
                });
                bubbleView.startAnimation(fadeOut);
            }
        }, 4000);
    }

    private void startAppDetection() {
        appCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkForegroundApp();
                appCheckHandler.postDelayed(this, 1200);
            }
        }, 1200);
    }

    private void checkForegroundApp() {
        String pkg = getForegroundPackage();
        // 如果始终返回自身包名，说明未授予"使用情况访问权限"
        if (pkg != null && pkg.equals(getPackageName())) {
            return; // 权限未授予时静默跳过，不重复提示
        }
        if (pkg != null && !pkg.equals(lastPkg)) {
            String reaction;
            if (pkg.contains("taobao") || pkg.contains("jingdong")) {
                reaction = "买东西？我帮你挑！";
            } else if (pkg.contains("bilibili") || pkg.contains("aweme")) {
                reaction = "在刷视频呢？";
            } else if (pkg.contains("weixin") || pkg.contains("tencent.mm") || pkg.contains("qq")) {
                reaction = "聊天呢？我也在~";
            } else if (pkg.contains("com.android")) {
                reaction = "";
            } else {
                reaction = "换软件啦？";
            }
            if (!reaction.isEmpty() && !reaction.equals(lastReaction) && 
                    System.currentTimeMillis() - lastReactionTime > 10000) {
                showBubble(reaction);
                lastReaction = reaction;
                lastReactionTime = System.currentTimeMillis();
            }
            lastPkg = pkg;
        }
    }

    private String getForegroundPackage() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                long time = System.currentTimeMillis();
                List<UsageStats> stats = usm.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, time - 8000, time);
                if (stats != null && !stats.isEmpty()) {
                    UsageStats recent = null;
                    for (UsageStats s : stats) {
                        if (recent == null || s.getLastTimeUsed() > recent.getLastTimeUsed()) {
                            recent = s;
                        }
                    }
                    if (recent != null) return recent.getPackageName();
                }
            }
        } catch (Exception e) {
            return getPackageName();
        }
        return getPackageName();
    }

    private void startIdleBubble() {
        idleHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (random.nextInt(100) < 60) {
                    String msg = IDLE_PHRASES[random.nextInt(IDLE_PHRASES.length)];
                    showBubble(msg);
                }
                idleHandler.postDelayed(this, 30000 + random.nextInt(60000));
            }
        }, 60000);
    }

    private void startDrinkReminder() {
        drinkReminderHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showBubble("该喝水啦！");
                drinkReminderHandler.postDelayed(this, 45 * 60 * 1000);
            }
        }, 45 * 60 * 1000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        appCheckHandler.removeCallbacksAndMessages(null);
        idleHandler.removeCallbacksAndMessages(null);
        drinkReminderHandler.removeCallbacksAndMessages(null);
        bubbleHandler.removeCallbacksAndMessages(null);
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
        if (bubbleView != null && windowManager != null) {
            windowManager.removeView(bubbleView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
