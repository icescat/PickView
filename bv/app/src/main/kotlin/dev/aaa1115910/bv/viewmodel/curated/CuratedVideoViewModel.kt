package dev.aaa1115910.bv.viewmodel.curated

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.entity.DailyWatchStats
import dev.aaa1115910.bv.entity.ParentalControlConfig
import dev.aaa1115910.bv.network.NasCategory
import dev.aaa1115910.bv.network.NasSeries
import dev.aaa1115910.bv.network.NasServerApi
import dev.aaa1115910.bv.network.NasVideoItem
import dev.aaa1115910.bv.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * 浏览模式：按分类或按系列
 */
enum class BrowseMode {
    CATEGORY,
    SERIES
}

class CuratedVideoViewModel(
    private val nasApi: NasServerApi = NasServerApi()
) : ViewModel() {

    var videos by mutableStateOf<List<NasVideoItem>>(emptyList())
        private set

    var filteredVideos by mutableStateOf<List<NasVideoItem>>(emptyList())
        private set

    var categories by mutableStateOf<List<NasCategory>>(emptyList())
        private set

    var seriesList by mutableStateOf<List<NasSeries>>(emptyList())
        private set

    var selectedCategory by mutableStateOf<String?>(null)
        private set

    var selectedSeriesId by mutableStateOf<String?>(null)
        private set

    var browseMode by mutableStateOf(BrowseMode.CATEGORY)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var remainingSeconds by mutableIntStateOf(Int.MAX_VALUE)
        private set

    var remainingCount by mutableIntStateOf(Int.MAX_VALUE)
        private set

    var isOutsideAllowedTime by mutableStateOf(false)
        private set

    var isLimitReached by mutableStateOf(false)
        private set

    private val _watchStats = MutableStateFlow(DailyWatchStats.createToday())
    val watchStats = _watchStats.asStateFlow()

    fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            nasApi.getCategories()
                .onSuccess { categories = it }
                .onFailure { }
        }
    }

    fun loadSeries() {
        viewModelScope.launch(Dispatchers.IO) {
            nasApi.getSeries()
                .onSuccess { seriesList = it }
                .onFailure { }
        }
    }

    /**
     * 从 NAS 服务器拉取家长控制配置，成功后覆盖本地 Prefs
     */
    fun loadParentalControlFromServer() {
        viewModelScope.launch(Dispatchers.IO) {
            nasApi.getParentalControl()
                .onSuccess { remote ->
                    Prefs.parentalControlEnabled = remote.enabled
                    Prefs.dailyTimeLimitMinutes = remote.daily_time_limit_minutes
                    Prefs.dailyVideoCountLimit = remote.daily_video_count_limit
                    Prefs.maxSingleVideoDurationMinutes = remote.max_single_video_duration_minutes
                    Prefs.allowedStartHour = remote.allowed_start_hour
                    Prefs.allowedStartMinute = remote.allowed_start_minute
                    Prefs.allowedEndHour = remote.allowed_end_hour
                    Prefs.allowedEndMinute = remote.allowed_end_minute
                    Prefs.resetHour = remote.reset_hour
                    Prefs.watchCompletionThreshold = (remote.watch_completion_threshold * 100).toInt()
                    Prefs.allowCurrentVideoFinish = remote.allow_current_video_finish
                    Prefs.shortMaxDurationSeconds = remote.short_max_duration_minutes * 60
                    Prefs.mediumMaxDurationSeconds = remote.medium_max_duration_minutes * 60
                    Prefs.shortVideoCountLimit = remote.short_video_count_limit
                    Prefs.mediumVideoCountLimit = remote.medium_video_count_limit
                    Prefs.longVideoCountLimit = remote.long_video_count_limit
                    Prefs.blockShortVideo = remote.block_short_video
                    Prefs.blockMediumVideo = remote.block_medium_video
                    Prefs.blockLongVideo = remote.block_long_video
                }
                .onFailure { /* 使用本地缓存 */ }
        }
    }

    /**
     * 从 NAS 服务器拉取当日观看统计，与本地合并（取较大值）
     */
    fun loadWatchStatsFromServer() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshStatsFromServer()
        }
    }

    /**
     * 拉取服务器统计并检查是否已达上限，供播放器启动时调用
     * @param avid 当前要播放的视频 avid，若在已看列表中则放行（允许重看）
     * @return 达上限返回提示文案，未达上限或已看过返回 null
     */
    suspend fun refreshStatsAndCheckLimit(avid: Long): String? {
        refreshStatsFromServer()
        return getLimitReachedMessage(avid)
    }

    private suspend fun refreshStatsFromServer() {
        val today = LocalDate.now().toString()
        nasApi.getWatchStats(today)
            .onSuccess { remote ->
                val local = _watchStats.value
                // watchedVideoIds 仅本地内存维护，不与服务端同步（单设备单次会话有效）
                if (local.date == today) {
                    _watchStats.value = local.copy(
                        watchedTimeSeconds = maxOf(local.watchedTimeSeconds, remote.watched_time_seconds),
                        watchedVideoCount = maxOf(local.watchedVideoCount, remote.watched_video_count),
                        shortVideoCount = maxOf(local.shortVideoCount, remote.short_video_count),
                        mediumVideoCount = maxOf(local.mediumVideoCount, remote.medium_video_count),
                        longVideoCount = maxOf(local.longVideoCount, remote.long_video_count),
                        date = today
                    )
                } else {
                    _watchStats.value = DailyWatchStats(
                        date = today,
                        watchedTimeSeconds = remote.watched_time_seconds,
                        watchedVideoCount = remote.watched_video_count,
                        shortVideoCount = remote.short_video_count,
                        mediumVideoCount = remote.medium_video_count,
                        longVideoCount = remote.long_video_count
                    )
                }
            }
            .onFailure { /* 使用本地统计 */ }
    }

    fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            errorMessage = null
            val result = when (browseMode) {
                BrowseMode.CATEGORY -> nasApi.getVideos(category = selectedCategory)
                BrowseMode.SERIES -> {
                    if (selectedSeriesId != null) {
                        nasApi.getSeriesVideos(selectedSeriesId!!)
                    } else {
                        // 没有选中系列时，获取所有视频
                        nasApi.getVideos()
                    }
                }
            }
            result
                .onSuccess { response ->
                    videos = response
                    refreshParentalControl()
                }
                .onFailure { e ->
                    errorMessage = e.message ?: "加载失败"
                }
            isLoading = false
        }
    }

    fun selectCategory(category: String?) {
        selectedCategory = category
        browseMode = BrowseMode.CATEGORY
        loadVideos()
    }

    fun selectSeries(seriesId: String?) {
        selectedSeriesId = seriesId
        browseMode = BrowseMode.SERIES
        loadVideos()
    }

    fun switchBrowseMode(mode: BrowseMode) {
        browseMode = mode
        if (mode == BrowseMode.CATEGORY) {
            selectedSeriesId = null
        } else {
            selectedCategory = null
        }
        loadVideos()
    }

    fun searchVideos(keyword: String) {
        if (keyword.isBlank()) {
            loadVideos()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            errorMessage = null
            nasApi.searchVideos(keyword)
                .onSuccess { response ->
                    videos = response
                    refreshParentalControl()
                }
                .onFailure { e ->
                    errorMessage = e.message ?: "搜索失败"
                }
            isLoading = false
        }
    }

    fun refreshParentalControl() {
        val config = loadParentalControlConfig()
        val stats = loadOrResetWatchStats()

        if (config.enabled && !config.isInAllowedTimeRange()) {
            isOutsideAllowedTime = true
            filteredVideos = emptyList()
            return
        }
        isOutsideAllowedTime = false

        remainingSeconds = stats.getRemainingTimeSeconds(config.dailyTimeLimitSeconds)
        remainingCount = stats.getRemainingVideoCount(config.dailyVideoCountLimit)

        if (!config.enabled) {
            filteredVideos = videos
            return
        }

        if (remainingSeconds <= 0 || remainingCount <= 0) {
            isLimitReached = true
            filteredVideos = emptyList()
            return
        }
        isLimitReached = false

        val maxDuration = config.maxSingleVideoDurationSeconds
        filteredVideos = videos.filter { video ->
            val videoType = config.classifyDuration(video.duration)
            // 0. 类型屏蔽：被屏蔽的类型直接不显示
            !isVideoTypeBlocked(config, videoType) &&
            // 1. 时长不超过剩余时间
            video.duration <= remainingSeconds &&
            // 2. 单视频时长限制
            (maxDuration <= 0 || video.duration <= maxDuration) &&
            // 3. 类型数量限制：短视频
            (stats.hasRemainingTypeCount(config.shortVideoCountLimit, stats.shortVideoCount) ||
                    videoType != 0) &&
            // 4. 类型数量限制：中视频
            (stats.hasRemainingTypeCount(config.mediumVideoCountLimit, stats.mediumVideoCount) ||
                    videoType != 1) &&
            // 5. 类型数量限制：长视频
            (stats.hasRemainingTypeCount(config.longVideoCountLimit, stats.longVideoCount) ||
                    videoType != 2)
        }
    }

    /**
     * 检查视频类型是否被屏蔽
     * @param videoType 0=短视频, 1=中视频, 2=长视频
     */
    private fun isVideoTypeBlocked(config: ParentalControlConfig, videoType: Int): Boolean {
        return when (videoType) {
            0 -> config.blockShortVideo
            1 -> config.blockMediumVideo
            2 -> config.blockLongVideo
            else -> false
        }
    }

    fun onVideoWatched(durationSeconds: Int) {
        val stats = _watchStats.value
        val newStats = stats.copy(
            watchedTimeSeconds = stats.watchedTimeSeconds + durationSeconds,
            date = LocalDate.now().toString()
        )
        _watchStats.value = newStats
        refreshParentalControl()
    }

    fun onVideoCompleted(videoDuration: Int, avid: Long) {
        val stats = _watchStats.value
        val config = loadParentalControlConfig()
        val videoType = config.classifyDuration(videoDuration)
        val newStats = stats.copy(
            watchedVideoCount = stats.watchedVideoCount + 1,
            shortVideoCount = stats.shortVideoCount + if (videoType == 0) 1 else 0,
            mediumVideoCount = stats.mediumVideoCount + if (videoType == 1) 1 else 0,
            longVideoCount = stats.longVideoCount + if (videoType == 2) 1 else 0,
            watchedVideoIds = stats.watchedVideoIds + avid,
            date = LocalDate.now().toString()
        )
        _watchStats.value = newStats
        refreshParentalControl()
        reportWatchStatsToServer()
    }

    /**
     * 上报当日观看统计到 NAS 服务器
     */
    private fun reportWatchStatsToServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = _watchStats.value
            val today = LocalDate.now().toString()
            nasApi.updateWatchStats(today, dev.aaa1115910.bv.network.NasWatchStatsUpdate(
                watched_time_seconds = stats.watchedTimeSeconds,
                watched_video_count = stats.watchedVideoCount,
                short_video_count = stats.shortVideoCount,
                medium_video_count = stats.mediumVideoCount,
                long_video_count = stats.longVideoCount
            ))
        }
    }

    private fun loadParentalControlConfig(): ParentalControlConfig {
        val startHour = Prefs.allowedStartHour
        val endHour = Prefs.allowedEndHour
        return ParentalControlConfig(
            enabled = Prefs.parentalControlEnabled,
            dailyTimeLimitSeconds = Prefs.dailyTimeLimitMinutes * 60,
            dailyVideoCountLimit = Prefs.dailyVideoCountLimit,
            maxSingleVideoDurationSeconds = Prefs.maxSingleVideoDurationMinutes * 60,
            allowedStartTime = if (startHour >= 0) LocalTime.of(startHour, Prefs.allowedStartMinute) else null,
            allowedEndTime = if (endHour >= 0) LocalTime.of(endHour, Prefs.allowedEndMinute) else null,
            resetHour = Prefs.resetHour,
            watchCompletionThreshold = Prefs.watchCompletionThreshold,
            allowCurrentVideoFinish = Prefs.allowCurrentVideoFinish,
            shortMaxDurationSeconds = Prefs.shortMaxDurationSeconds,
            mediumMaxDurationSeconds = Prefs.mediumMaxDurationSeconds,
            shortVideoCountLimit = Prefs.shortVideoCountLimit,
            mediumVideoCountLimit = Prefs.mediumVideoCountLimit,
            longVideoCountLimit = Prefs.longVideoCountLimit,
            blockShortVideo = Prefs.blockShortVideo,
            blockMediumVideo = Prefs.blockMediumVideo,
            blockLongVideo = Prefs.blockLongVideo
        )
    }

    private fun loadOrResetWatchStats(): DailyWatchStats {
        val stats = _watchStats.value
        val today = LocalDate.now().toString()
        if (!stats.isToday()) {
            val resetStats = DailyWatchStats.createToday()
            _watchStats.value = resetStats
            return resetStats
        }
        return stats
    }

    /**
     * 检查是否已达观看上限，返回提示文案；未达上限返回 null
     * @param avid 当前要播放的视频 avid，若在已看列表中则放行（允许重看不计入）
     */
    fun getLimitReachedMessage(avid: Long): String? {
        val config = loadParentalControlConfig()
        if (!config.enabled) return null
        val stats = loadOrResetWatchStats()

        // 已看过的视频允许重看，不计入限制
        if (avid in stats.watchedVideoIds) return null

        // 各类型数量上限（优先级：具体类型 > 总数）
        if (config.shortVideoCountLimit > 0 &&
            stats.shortVideoCount >= config.shortVideoCountLimit
        ) {
            return "您的短视频观看数量已达上限（${config.shortVideoCountLimit}个）"
        }
        if (config.mediumVideoCountLimit > 0 &&
            stats.mediumVideoCount >= config.mediumVideoCountLimit
        ) {
            return "您的中视频观看数量已达上限（${config.mediumVideoCountLimit}个）"
        }
        if (config.longVideoCountLimit > 0 &&
            stats.longVideoCount >= config.longVideoCountLimit
        ) {
            return "您的长视频观看数量已达上限（${config.longVideoCountLimit}个）"
        }
        // 每日总数量上限
        if (config.dailyVideoCountLimit > 0 &&
            stats.watchedVideoCount >= config.dailyVideoCountLimit
        ) {
            return "您的今日观看数量已达上限（${config.dailyVideoCountLimit}个）"
        }
        // 每日总时长上限
        if (config.dailyTimeLimitSeconds > 0 &&
            stats.watchedTimeSeconds >= config.dailyTimeLimitSeconds
        ) {
            val minutes = config.dailyTimeLimitSeconds / 60
            return "您的今日观看时长已达上限（${minutes}分钟）"
        }
        return null
    }
}
