package dev.aaa1115910.bv.player

import android.os.Handler
import android.os.Looper

class WatchTimeTracker(
    private val player: AbstractVideoPlayer,
    private val onWatchTimeUpdate: (watchedSeconds: Int) -> Unit,
    private val onVideoCompleted: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMs = 10_000L

    private var totalWatchedMs: Long = 0
    private var lastUpdateTimeMs: Long = 0
    private var isTracking = false
    private var hasCountedAsWatched = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isTracking) return

            val now = System.currentTimeMillis()
            if (lastUpdateTimeMs > 0) {
                val elapsed = now - lastUpdateTimeMs
                if (player.isPlaying) {
                    totalWatchedMs += elapsed
                    val watchedSeconds = (totalWatchedMs / 1000).toInt()
                    onWatchTimeUpdate(watchedSeconds)

                    checkVideoCompletion()
                }
            }
            lastUpdateTimeMs = now

            handler.postDelayed(this, updateIntervalMs)
        }
    }

    fun start() {
        if (isTracking) return
        isTracking = true
        lastUpdateTimeMs = System.currentTimeMillis()
        handler.postDelayed(updateRunnable, updateIntervalMs)
    }

    fun stop() {
        if (!isTracking) return
        isTracking = false
        handler.removeCallbacks(updateRunnable)

        val now = System.currentTimeMillis()
        if (lastUpdateTimeMs > 0 && player.isPlaying) {
            val elapsed = now - lastUpdateTimeMs
            totalWatchedMs += elapsed
            val watchedSeconds = (totalWatchedMs / 1000).toInt()
            onWatchTimeUpdate(watchedSeconds)
            checkVideoCompletion()
        }
        lastUpdateTimeMs = 0
    }

    fun release() {
        stop()
    }

    fun getTotalWatchedSeconds(): Int = (totalWatchedMs / 1000).toInt()

    private fun checkVideoCompletion() {
        if (hasCountedAsWatched) return
        val duration = player.duration
        if (duration <= 0) return

        val currentPosition = player.currentPosition
        val progress = currentPosition.toFloat() / duration.toFloat()
        val threshold = 0.3f  // 30%以上计入有效观看，30%以下为试看不计数

        if (progress >= threshold) {
            hasCountedAsWatched = true
            onVideoCompleted()
        }
    }

    companion object {
        fun create(
            player: AbstractVideoPlayer,
            onWatchTimeUpdate: (Int) -> Unit,
            onVideoCompleted: () -> Unit
        ): WatchTimeTracker {
            return WatchTimeTracker(player, onWatchTimeUpdate, onVideoCompleted)
        }
    }
}
