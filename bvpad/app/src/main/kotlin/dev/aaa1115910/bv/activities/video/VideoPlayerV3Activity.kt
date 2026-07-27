package dev.aaa1115910.bv.activities.video

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.aaa1115910.biliapi.entity.user.Author
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.network.NasServerApi
import dev.aaa1115910.bv.network.NasVideoSession
import dev.aaa1115910.bv.player.WatchTimeTracker
import dev.aaa1115910.bv.screen.VideoPlayerV3Screen
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.curated.CuratedVideoViewModel
import dev.aaa1115910.bv.viewmodel.player.VideoPlayerV3ViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class VideoPlayerV3Activity : ComponentActivity() {
    private val playerViewModel: VideoPlayerV3ViewModel by viewModel()
    private val curatedViewModel: CuratedVideoViewModel by viewModel()
    private val nasApi: NasServerApi by inject()
    private var watchTimeTracker: WatchTimeTracker? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentAvid: Long = 0

    companion object {
        private val logger = KotlinLogging.logger { }
        private var currentInstance: VideoPlayerV3Activity? = null

        fun actionStart(
            context: Context,
            avid: Long,
            cid: Long,
            title: String,
            partTitle: String,
            played: Int,
            fromSeason: Boolean,
            subType: Int? = null,
            epid: Int? = null,
            seasonId: Int? = null,
            proxyArea: ProxyArea = ProxyArea.MainLand,
            author: Author? = null
        ) {
            currentInstance?.finish()
            context.startActivity(
                Intent(context, VideoPlayerV3Activity::class.java).apply {
                    putExtra("avid", avid)
                    putExtra("cid", cid)
                    putExtra("title", title)
                    putExtra("partTitle", partTitle)
                    putExtra("played", played)
                    putExtra("fromSeason", fromSeason)
                    putExtra("subType", subType)
                    putExtra("epid", epid)
                    putExtra("seasonId", seasonId)
                    putExtra("proxy_area", proxyArea.ordinal)
                    putExtra("author_mid", author?.mid)
                    putExtra("author_name", author?.name)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentInstance = this

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            BVTheme {
                VideoPlayerV3Screen()
            }
        }

        // 初始化viewmodel参数
        initViewModelFromIntent()

        // 家长控制：启动时检查是否已达观看上限，达上限则弹窗并退出
        if (Prefs.parentalControlEnabled) {
            checkLimitBeforePlay(currentAvid)
        }

        // 初始化播放器
        playerViewModel.initVideoPlayer(applicationContext)
        // 加载视频资源并播放
        playerViewModel.loadVideoWithResources()

        // 初始化观看时长追踪
        if (Prefs.parentalControlEnabled) {
            playerViewModel.videoPlayer?.let { player ->
                watchTimeTracker = WatchTimeTracker(
                    player = player,
                    onWatchTimeUpdate = { watchedSeconds ->
                        logger.fInfo { "Watched $watchedSeconds seconds" }
                        reportWatchStatus("watching", watchedSeconds)
                    },
                    onVideoCompleted = {
                        logger.fInfo { "Video completed" }
                        val totalWatched = watchTimeTracker?.getTotalWatchedSeconds() ?: 0
                        reportWatchStatus("watched", totalWatched)
                        val durationSec = ((playerViewModel.videoPlayer?.duration ?: 0) / 1000).toInt()
                        curatedViewModel.onVideoCompleted(durationSec, currentAvid)
                    }
                )
                watchTimeTracker?.start()
            }
        }
    }

    /**
     * 上报观看状态到 NAS 服务端
     */
    private fun reportWatchStatus(status: String, watchedDuration: Int) {
        val videoId = NasVideoSession.currentVideoId ?: return
        val player = playerViewModel.videoPlayer ?: return
        val duration = player.duration
        val progress = if (duration > 0) {
            (player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        scope.launch {
            nasApi.updateWatchStatus(
                videoId = videoId,
                status = status,
                progress = progress,
                watchedDuration = watchedDuration
            ).onFailure { e ->
                logger.fInfo { "Failed to report watch status: ${e.message}" }
            }
        }
    }

    /**
     * 启动播放前从服务器拉取最新统计，若已达观看上限则弹窗并退出，不允许播放
     * 已看过的视频允许重看，不拦截
     */
    private fun checkLimitBeforePlay(avid: Long) {
        if (avid <= 0) return
        scope.launch {
            val message = curatedViewModel.refreshStatsAndCheckLimit(avid)
            if (message != null && !isFinishing && !isDestroyed) {
                runOnUiThread {
                    AlertDialog.Builder(this@VideoPlayerV3Activity)
                        .setTitle("观看提醒")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("我知道了") { _, _ -> finish() }
                        .show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 视频全屏播放，隐藏状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onPause() {
        super.onPause()

        // 恢复状态栏
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())

        playerViewModel.videoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance === this) {
            currentInstance = null
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isFinishing) {
            // 最后一次上报观看状态
            watchTimeTracker?.release()
            if (NasVideoSession.currentVideoId != null) {
                reportWatchStatus("watching", watchTimeTracker?.getTotalWatchedSeconds() ?: 0)
            }
            NasVideoSession.clear()
            watchTimeTracker = null
            playerViewModel.detachPlayer()
        }
        scope.cancel()
    }

    private fun initViewModelFromIntent() {
        if (intent.hasExtra("avid")) {
            val aid = intent.getLongExtra("avid", 170001)
            currentAvid = aid
            val cid = intent.getLongExtra("cid", 170001)
            val title = intent.getStringExtra("title") ?: "Unknown Title"
            val played = intent.getIntExtra("played", 0)
            val fromSeason = intent.getBooleanExtra("fromSeason", false)
            val subType = intent.getIntExtra("subType", 0)
            val epid = intent.getIntExtra("epid", 0)
            val seasonId = intent.getIntExtra("seasonId", 0)
            val proxyArea = ProxyArea.entries[intent.getIntExtra("proxy_area", 0)]
            val author_mid = intent.getLongExtra("author_mid", 0)
            val author_name = intent.getStringExtra("author_name")
            logger.fInfo { "Launch parameter: [aid=$aid, cid=$cid]" }

            playerViewModel.init(
                aid = aid,
                cid = cid,
                epid = epid.takeIf { it != 0 },
                title = title,
                lastPlayed = played,
                fromSeason = fromSeason,
                subType = subType,
                seasonId = seasonId,
                proxyArea = proxyArea,
                authorMid = author_mid,
                authorName = author_name ?: ""
            )
        } else {
            logger.fInfo { "Null launch parameter" }
        }
    }
}
