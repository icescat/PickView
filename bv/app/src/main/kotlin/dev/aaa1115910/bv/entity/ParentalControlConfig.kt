package dev.aaa1115910.bv.entity

import java.time.LocalTime

/**
 * 家长控制配置
 * 用于管理孩子的观看限制，包括时长限制、数量限制、时段限制等
 */
data class ParentalControlConfig(
    /** 是否启用家长控制 */
    val enabled: Boolean = false,
    
    /** 
     * 每日总观看时长限制（秒）
     * 0 表示不限制
     */
    val dailyTimeLimitSeconds: Int = 0,
    
    /**
     * 每日可观看视频数量限制
     * 0 表示不限制
     */
    val dailyVideoCountLimit: Int = 0,
    
    /**
     * 单视频最大时长限制（秒）
     * 用于强制只能看短视频
     * 0 表示不限制
     */
    val maxSingleVideoDurationSeconds: Int = 0,
    
    /**
     * 允许观看的开始时间
     * null 表示不限制
     */
    val allowedStartTime: LocalTime? = null,
    
    /**
     * 允许观看的结束时间
     * null 表示不限制
     */
    val allowedEndTime: LocalTime? = null,
    
    /**
     * 重置时间（几点重置每日限制）
     * 默认凌晨 0 点
     */
    val resetHour: Int = 0,
    
    /**
     * 观看完成判定阈值（百分比）
     * 观看进度超过此值才计入已观看数量
     */
    val watchCompletionThreshold: Int = 80,
    
    /**
     * 是否允许当前视频播完（人性化设置）
     * true: 即使时间/数量用完，也允许当前视频播完
     * false: 时间/数量用完立即停止
     */
    val allowCurrentVideoFinish: Boolean = true,
    
    /**
     * 白名单模式
     * true: 只统计/限制白名单外的视频
     * false: 所有视频都受限制
     */
    val whitelistOnlyForStats: Boolean = true,
    
    /**
     * 允许的 UP 主 ID 列表（白名单）
     */
    val allowedUpIds: Set<Long> = emptySet(),
    
    /**
     * 禁止的 UP 主 ID 列表（黑名单）
     */
    val blockedUpIds: Set<Long> = emptySet(),
    
    /**
     * 禁止的关键词列表
     */
    val blockedKeywords: Set<String> = emptySet(),

    /**
     * 短视频最大时长（秒）
     * 超过此值为中视频或长视频
     */
    val shortMaxDurationSeconds: Int = 5 * 60,

    /**
     * 中视频最大时长（秒）
     * 超过此值为长视频
     */
    val mediumMaxDurationSeconds: Int = 15 * 60,

    /**
     * 短视频每日数量限制
     * 0 表示不限制
     */
    val shortVideoCountLimit: Int = 0,

    /**
     * 中视频每日数量限制
     * 0 表示不限制
     */
    val mediumVideoCountLimit: Int = 0,

    /**
     * 长视频每日数量限制
     * 0 表示不限制
     */
    val longVideoCountLimit: Int = 0,

    /**
     * 屏蔽短视频（TV/Pad 端完全不显示）
     */
    val blockShortVideo: Boolean = false,

    /**
     * 屏蔽中视频（TV/Pad 端完全不显示）
     */
    val blockMediumVideo: Boolean = false,

    /**
     * 屏蔽长视频（TV/Pad 端完全不显示）
     */
    val blockLongVideo: Boolean = false
) {
    companion object {
        const val DEFAULT_DAILY_TIME_LIMIT_MINUTES = 10
        const val DEFAULT_DAILY_VIDEO_COUNT = 2
        const val DEFAULT_MAX_VIDEO_DURATION_MINUTES = 10
        const val DEFAULT_WATCH_COMPLETION_THRESHOLD = 80
        
        /**
         * 创建默认的 10 分钟时长限制配置
         */
        fun createDefaultTimeLimit(minutes: Int = DEFAULT_DAILY_TIME_LIMIT_MINUTES): ParentalControlConfig {
            return ParentalControlConfig(
                enabled = true,
                dailyTimeLimitSeconds = minutes * 60,
                allowCurrentVideoFinish = true
            )
        }
        
        /**
         * 创建默认的 2 个视频数量限制配置
         */
        fun createDefaultCountLimit(count: Int = DEFAULT_DAILY_VIDEO_COUNT): ParentalControlConfig {
            return ParentalControlConfig(
                enabled = true,
                dailyVideoCountLimit = count,
                watchCompletionThreshold = DEFAULT_WATCH_COMPLETION_THRESHOLD,
                allowCurrentVideoFinish = true
            )
        }
        
        /**
         * 创建综合限制配置
         */
        fun createComprehensiveLimit(
            timeMinutes: Int = DEFAULT_DAILY_TIME_LIMIT_MINUTES,
            count: Int = DEFAULT_DAILY_VIDEO_COUNT,
            maxDurationMinutes: Int = DEFAULT_MAX_VIDEO_DURATION_MINUTES
        ): ParentalControlConfig {
            return ParentalControlConfig(
                enabled = true,
                dailyTimeLimitSeconds = timeMinutes * 60,
                dailyVideoCountLimit = count,
                maxSingleVideoDurationSeconds = maxDurationMinutes * 60,
                watchCompletionThreshold = DEFAULT_WATCH_COMPLETION_THRESHOLD,
                allowCurrentVideoFinish = true
            )
        }
    }
    
    /**
     * 检查当前是否在允许观看的时间段内
     */
    fun isInAllowedTimeRange(): Boolean {
        if (allowedStartTime == null || allowedEndTime == null) return true
        
        val now = LocalTime.now()
        return if (allowedStartTime <= allowedEndTime) {
            // 正常时间段，如 19:00-21:00
            now in allowedStartTime..allowedEndTime
        } else {
            // 跨午夜时间段，如 22:00-07:00
            now >= allowedStartTime || now <= allowedEndTime
        }
    }
    
    /**
     * 检查是否启用了任何限制
     */
    fun hasAnyLimit(): Boolean {
        return enabled && (
            dailyTimeLimitSeconds > 0 ||
            dailyVideoCountLimit > 0 ||
            maxSingleVideoDurationSeconds > 0 ||
            allowedStartTime != null
        )
    }
    
    /**
     * 获取时长限制的友好显示文本
     */
    fun getTimeLimitDisplay(): String {
        return when {
            dailyTimeLimitSeconds <= 0 -> "无限制"
            dailyTimeLimitSeconds < 60 -> "${dailyTimeLimitSeconds}秒"
            dailyTimeLimitSeconds < 3600 -> "${dailyTimeLimitSeconds / 60}分钟"
            else -> "${dailyTimeLimitSeconds / 3600}小时${(dailyTimeLimitSeconds % 3600) / 60}分钟"
        }
    }
    
    /**
     * 获取单视频时长限制的友好显示文本
     */
    fun getMaxDurationDisplay(): String {
        return when {
            maxSingleVideoDurationSeconds <= 0 -> "无限制"
            maxSingleVideoDurationSeconds < 60 -> "${maxSingleVideoDurationSeconds}秒"
            else -> "${maxSingleVideoDurationSeconds / 60}分钟"
        }
    }

    /**
     * 根据视频时长判断视频类型
     * @return 0=短视频, 1=中视频, 2=长视频
     */
    fun classifyDuration(durationSeconds: Int): Int {
        return when {
            durationSeconds <= shortMaxDurationSeconds -> 0
            durationSeconds <= mediumMaxDurationSeconds -> 1
            else -> 2
        }
    }
}

/**
 * 今日观看统计
 */
data class DailyWatchStats(
    /** 日期（用于判断是否跨天） */
    val date: String,
    
    /** 今日已观看总时长（秒） */
    val watchedTimeSeconds: Int = 0,
    
    /** 今日已观看完成的视频数量 */
    val watchedVideoCount: Int = 0,

    /** 今日已观看短视频数量 */
    val shortVideoCount: Int = 0,

    /** 今日已观看中视频数量 */
    val mediumVideoCount: Int = 0,

    /** 今日已观看长视频数量 */
    val longVideoCount: Int = 0,

    /** 今日已有效观看（进度≥20%）的视频 avid 列表，用于重看放行 */
    val watchedVideoIds: Set<Long> = emptySet(),

    /** 当前正在观看的视频（用于 allowCurrentVideoFinish 逻辑） */
    val currentVideoAid: Long? = null,
    
    /** 当前视频开始观看的时间戳 */
    val currentVideoStartTime: Long? = null,
    
    /** 当前视频已观看时长 */
    val currentVideoWatchedSeconds: Int = 0
) {
    companion object {
        fun createToday(): DailyWatchStats {
            return DailyWatchStats(
                date = java.time.LocalDate.now().toString()
            )
        }
    }
    
    /**
     * 检查是否是今天的数据
     */
    fun isToday(): Boolean {
        return date == java.time.LocalDate.now().toString()
    }
    
    /**
     * 获取剩余可观看时长
     */
    fun getRemainingTimeSeconds(dailyLimitSeconds: Int): Int {
        if (dailyLimitSeconds <= 0) return Int.MAX_VALUE
        return maxOf(0, dailyLimitSeconds - watchedTimeSeconds - currentVideoWatchedSeconds)
    }
    
    /**
     * 获取剩余可观看视频数量
     */
    fun getRemainingVideoCount(dailyLimitCount: Int): Int {
        if (dailyLimitCount <= 0) return Int.MAX_VALUE
        // 当前视频如果已经看完，则计入已观看数量
        val currentCount = if (currentVideoAid != null) 1 else 0
        return maxOf(0, dailyLimitCount - watchedVideoCount - currentCount)
    }
    
    /**
     * 检查是否还有剩余时长
     */
    fun hasRemainingTime(dailyLimitSeconds: Int): Boolean {
        return getRemainingTimeSeconds(dailyLimitSeconds) > 0
    }
    
    /**
     * 检查是否还有剩余视频数量
     */
    fun hasRemainingVideoCount(dailyLimitCount: Int): Boolean {
        return getRemainingVideoCount(dailyLimitCount) > 0
    }

    /**
     * 获取某类型视频剩余可观看数量
     * @param typeLimit 该类型的限制数量
     * @param typeCount 该类型已观看数量
     */
    fun getRemainingTypeCount(typeLimit: Int, typeCount: Int): Int {
        if (typeLimit <= 0) return Int.MAX_VALUE
        return maxOf(0, typeLimit - typeCount)
    }

    /**
     * 检查某类型视频是否还有剩余数量
     */
    fun hasRemainingTypeCount(typeLimit: Int, typeCount: Int): Boolean {
        return getRemainingTypeCount(typeLimit, typeCount) > 0
    }
}
