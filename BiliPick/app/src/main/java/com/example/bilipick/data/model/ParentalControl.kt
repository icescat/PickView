package com.example.bilipick.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ParentalControlConfig(
    val enabled: Boolean = false,
    val daily_time_limit_minutes: Int = 0,
    val daily_video_count_limit: Int = 0,
    val max_single_video_duration_minutes: Int = 0,
    val allowed_start_hour: Int = -1,
    val allowed_start_minute: Int = 0,
    val allowed_end_hour: Int = -1,
    val allowed_end_minute: Int = 0,
    val reset_hour: Int = 0,
    val watch_completion_threshold: Float = 0.8f,
    val allow_current_video_finish: Boolean = true,
    val short_max_duration_minutes: Int = 5,
    val medium_max_duration_minutes: Int = 15,
    val short_video_count_limit: Int = 0,
    val medium_video_count_limit: Int = 0,
    val long_video_count_limit: Int = 0,
    val block_short_video: Boolean = false,
    val block_medium_video: Boolean = false,
    val block_long_video: Boolean = false
)

@Serializable
data class WatchStats(
    val date: String,
    val watched_time_seconds: Int = 0,
    val watched_video_count: Int = 0,
    val short_video_count: Int = 0,
    val medium_video_count: Int = 0,
    val long_video_count: Int = 0
)
