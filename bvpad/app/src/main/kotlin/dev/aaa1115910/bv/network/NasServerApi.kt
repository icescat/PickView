package dev.aaa1115910.bv.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Serializable
data class NasVideoItem(
    val id: String,
    val bvid: String,
    val title: String,
    val up_name: String,
    val duration: Int,
    val category: String = "",
    val cover: String = "",
    val added_at: String = "",
    val added_by: String = "",
    val series_id: String = "",
    val episode_index: Int = 0,
    val watch_status: String? = null,
    val watch_progress: Float = 0f,
    val last_watched_at: String? = null
)

@Serializable
data class NasCategory(
    val id: String,
    val name: String,
    val display_order: Int = 0
)

@Serializable
data class NasSeries(
    val id: String,
    val title: String,
    val cover: String = "",
    val description: String = "",
    val display_order: Int = 0,
    val created_at: String = "",
    val video_count: Int = 0
)

@Serializable
data class NasWatchStatusUpdate(
    val status: String,
    val progress: Float = 0f,
    val watched_duration: Int = 0
)

@Serializable
data class NasWatchStatus(
    val video_id: String,
    val status: String = "unwatched",
    val progress: Float = 0f,
    val last_watched_at: String? = null,
    val watched_duration: Int = 0
)

@Serializable
data class NasHealthResponse(
    val status: String
)

@Serializable
data class NasParentalControlConfig(
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
    val block_long_video: Boolean = false,
    val updated_at: String? = null
)

@Serializable
data class NasWatchStats(
    val date: String,
    val watched_time_seconds: Int = 0,
    val watched_video_count: Int = 0,
    val short_video_count: Int = 0,
    val medium_video_count: Int = 0,
    val long_video_count: Int = 0,
    val updated_at: String? = null
)

@Serializable
data class NasWatchStatsUpdate(
    val watched_time_seconds: Int = 0,
    val watched_video_count: Int = 0,
    val short_video_count: Int = 0,
    val medium_video_count: Int = 0,
    val long_video_count: Int = 0
)

@Single
class NasServerApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    private fun baseUrl(): String {
        return dev.aaa1115910.bv.util.Prefs.nasServerUrl.trimEnd('/')
    }

    private fun apiKey(): String = dev.aaa1115910.bv.util.Prefs.nasApiKey

    suspend fun getVideos(
        category: String? = null,
        seriesId: String? = null
    ): Result<List<NasVideoItem>> = runCatching {
        val response = client.get("${baseUrl()}/api/videos") {
            header("X-API-Key", apiKey())
            if (category != null) parameter("category", category)
            if (seriesId != null) parameter("series_id", seriesId)
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<List<NasVideoItem>>()
    }

    suspend fun getCategories(): Result<List<NasCategory>> = runCatching {
        val response = client.get("${baseUrl()}/api/categories") {
            header("X-API-Key", apiKey())
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<List<NasCategory>>()
    }

    suspend fun searchVideos(keyword: String): Result<List<NasVideoItem>> = runCatching {
        val response = client.get("${baseUrl()}/api/videos/search") {
            header("X-API-Key", apiKey())
            parameter("keyword", keyword)
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<List<NasVideoItem>>()
    }

    suspend fun getSeries(): Result<List<NasSeries>> = runCatching {
        val response = client.get("${baseUrl()}/api/series") {
            header("X-API-Key", apiKey())
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<List<NasSeries>>()
    }

    suspend fun getSeriesVideos(seriesId: String): Result<List<NasVideoItem>> = runCatching {
        val response = client.get("${baseUrl()}/api/series/$seriesId/videos") {
            header("X-API-Key", apiKey())
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<List<NasVideoItem>>()
    }

    suspend fun updateWatchStatus(
        videoId: String,
        status: String,
        progress: Float,
        watchedDuration: Int
    ): Result<NasWatchStatus> = runCatching {
        val body = NasWatchStatusUpdate(
            status = status,
            progress = progress,
            watched_duration = watchedDuration
        )
        val response = client.post("${baseUrl()}/api/videos/$videoId/watch-status") {
            header("X-API-Key", apiKey())
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<NasWatchStatus>()
    }

    suspend fun checkHealth(): Result<NasHealthResponse> = runCatching {
        val response = client.get("${baseUrl()}/api/health")
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<NasHealthResponse>()
    }

    suspend fun getParentalControl(): Result<NasParentalControlConfig> = runCatching {
        val response = client.get("${baseUrl()}/api/parental-control") {
            header("X-API-Key", apiKey())
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<NasParentalControlConfig>()
    }

    suspend fun getWatchStats(date: String): Result<NasWatchStats> = runCatching {
        val response = client.get("${baseUrl()}/api/watch-stats") {
            header("X-API-Key", apiKey())
            parameter("date", date)
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<NasWatchStats>()
    }

    suspend fun updateWatchStats(date: String, update: NasWatchStatsUpdate): Result<NasWatchStats> = runCatching {
        val response = client.post("${baseUrl()}/api/watch-stats") {
            header("X-API-Key", apiKey())
            parameter("date", date)
            contentType(ContentType.Application.Json)
            setBody(update)
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status.value}")
        }
        response.body<NasWatchStats>()
    }
}
