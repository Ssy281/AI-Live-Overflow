package com.ssy281.liveoverflow.overlay

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*

class AppDetector(
    private val context: Context,
    private val onAppChanged: (String) -> Unit,
    private val onPermissionMissing: () -> Unit = {}
) {
    private var currentPackage = ""
    private var job: Job? = null

    companion object {
        private const val TAG = "AppDetector"
        
        fun hasUsageStatsPermission(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun start(scope: CoroutineScope) {
        if (!hasUsageStatsPermission(context)) {
            Log.w(TAG, "没有使用情况访问权限！请去设置→安全→使用情况访问 授权")
            onPermissionMissing()
            return
        }
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val pkg = getForegroundPackage()
                Log.d(TAG, "检测前台应用: $pkg")
                if (pkg != null && pkg != currentPackage) {
                    currentPackage = pkg
                    withContext(Dispatchers.Main) { onAppChanged(pkg) }
                }
                delay(2000)
            }
        }
    }

    fun stop() { job?.cancel() }

    private fun getForegroundPackage(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - 10000, now
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } else null
    }
}
