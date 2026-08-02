package com.ssy281.liveoverflow.overlay

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.*

class AppDetector(
    private val context: Context,
    private val onAppChanged: (String) -> Unit
) {
    private var currentPackage = ""
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val pkg = getForegroundPackage()
                if (pkg != currentPackage && pkg != null) {
                    currentPackage = pkg
                    withContext(Dispatchers.Main) { onAppChanged(pkg) }
                }
                delay(3000)
            }
        }
    }

    fun stop() { job?.cancel() }

    private fun getForegroundPackage(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - 5000, now
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } else null
    }
}
