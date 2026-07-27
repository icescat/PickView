package dev.aaa1115910.bv.network

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 运行时单例，用于在 HomeContent 点击视频时记录当前 NAS 视频信息，
 * 供 VideoPlayerV3Activity 上报观看状态、VideoInfoScreen 显示入库/观看时间使用。
 */
object NasVideoSession {
    var currentVideoId: String? = null
    var currentBvid: String? = null
    var currentAddedAt: String? = null
    var currentLastWatchedAt: String? = null

    fun set(
        videoId: String?,
        bvid: String?,
        addedAt: String? = null,
        lastWatchedAt: String? = null
    ) {
        currentVideoId = videoId
        currentBvid = bvid
        currentAddedAt = addedAt
        currentLastWatchedAt = lastWatchedAt
    }

    fun clear() {
        currentVideoId = null
        currentBvid = null
        currentAddedAt = null
        currentLastWatchedAt = null
    }

    private val isoParser = DateTimeFormatter.ISO_DATE_TIME
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** 将 ISO 时间字符串格式化为友好显示，解析失败返回原始字符串。 */
    fun formatTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(raw, isoParser).format(displayFormatter)
        } catch (e: Exception) {
            raw
        }
    }
}
