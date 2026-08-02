package com.ssy281.liveoverflow.overlay

import kotlinx.coroutines.*

class HeatSystem(
    private val onHeatChanged: (Int) -> Unit,
    private val onHeatMax: () -> Unit
) {
    var heat = 0
        private set
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            while (isActive) {
                delay(30_000)
                if (heat > 0) {
                    heat = (heat - 1).coerceAtLeast(0)
                    withContext(Dispatchers.Main) { onHeatChanged(heat) }
                }
            }
        }
    }

    fun stop() { job?.cancel() }

    fun addHeat(amount: Int) {
        heat = (heat + amount).coerceAtMost(100)
        if (heat >= 100) onHeatMax()
    }

    fun reset() { heat = 0 }
}
