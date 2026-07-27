package com.example.bilipick.network

import android.content.Context
import android.util.Log
import com.example.bilipick.data.model.Category
import com.example.bilipick.data.model.CategoryCreateRequest
import com.example.bilipick.data.model.CategoryUpdateRequest
import com.example.bilipick.data.model.ParentalControlConfig
import com.example.bilipick.data.model.Series
import com.example.bilipick.data.model.SeriesCreateRequest
import com.example.bilipick.data.model.SeriesUpdateRequest
import com.example.bilipick.data.model.SeriesVideoAddRequest
import com.example.bilipick.data.model.Video
import com.example.bilipick.data.model.VideoCategoryUpdate
import com.example.bilipick.data.model.VideoCreateRequest
import com.example.bilipick.data.model.WatchStats
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*

/**
 * NAS服务器API服务
 */
class NasApiService(context: Context) {

    private val prefs = context.getSharedPreferences("bilipick_prefs", Context.MODE_PRIVATE)
    private val client: HttpClient = KtorClient.createClient()
    private val TAG = "NasApiService"

    /**
     * 获取NAS服务器基础URL
     * 默认为空，用户需在设置中配置
     */
    fun getBaseUrl(): String {
        val url = prefs.getString("nas_server_url", "") ?: ""
        return if (url.endsWith("/")) url.dropLast(1) else url
    }

    /**
     * 保存NAS服务器地址
     */
    fun saveBaseUrl(url: String) {
        prefs.edit().putString("nas_server_url", url).apply()
    }

    /**
     * 获取API Key
     * 默认为空，用户需在设置中配置
     */
    fun getApiKey(): String = prefs.getString("nas_api_key", "") ?: ""

    /**
     * 保存API Key
     */
    fun saveApiKey(key: String) {
        prefs.edit().putString("nas_api_key", key).apply()
    }

    /**
     * 检查是否已配置NAS服务器
     */
    fun isConfigured(): Boolean {
        return getBaseUrl().isNotEmpty()
    }

    /**
     * 在当前请求构建器上应用 API Key header
     */
    private fun HttpRequestBuilder.applyApiKey() {
        val key = getApiKey()
        if (key.isNotEmpty()) header("X-API-Key", key)
    }

    /**
     * 统一解析错误响应，401 时特别提示 API Key 问题
     */
    private suspend fun errorMessage(response: HttpResponse): String {
        val code = response.status.value
        val body = try { response.body<String>() } catch (_: Exception) { "" }
        return when (code) {
            401 -> "API Key 无效或未配置（请在设置中检查 API Key）"
            else -> "HTTP $code: $body"
        }
    }

    /**
     * 添加视频到NAS
     */
    suspend fun addVideo(request: VideoCreateRequest): Result<Video> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) {
            return Result.failure(Exception("未配置NAS服务器地址"))
        }

        return try {
            val response = client.post("$baseUrl/api/videos") {
                contentType(ContentType.Application.Json)
                setBody(request)
                applyApiKey()
            }

            if (response.status.isSuccess()) {
                val video: Video = response.body()
                Result.success(video)
            } else {
                Result.failure(Exception("添加失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加视频失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取所有视频
     */
    suspend fun getVideos(): Result<List<Video>> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) {
            return Result.failure(Exception("未配置NAS服务器地址"))
        }

        return try {
            val response = client.get("$baseUrl/api/videos") {
                applyApiKey()
            }

            if (response.status.isSuccess()) {
                val videos: List<Video> = response.body()
                Result.success(videos)
            } else {
                Result.failure(Exception("获取视频列表失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取视频列表失败", e)
            Result.failure(e)
        }
    }

    /**
     * 删除视频
     */
    suspend fun deleteVideo(videoId: String): Result<Unit> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) {
            return Result.failure(Exception("未配置NAS服务器地址"))
        }

        return try {
            val response = client.delete("$baseUrl/api/videos/$videoId") {
                applyApiKey()
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除视频失败", e)
            Result.failure(e)
        }
    }

    /**
     * 健康检查：调用需要鉴权的 /api/categories 接口，
     * 同时验证服务器可达性和 API Key 有效性。
     * 返回 Result<String>：成功时返回服务器版本，失败时返回详细错误信息。
     */
    suspend fun healthCheck(): Result<String> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置服务器地址"))
        val key = getApiKey()
        Log.d(TAG, "healthCheck: baseUrl=$baseUrl, apiKeyLength=${key.length}, apiKeyPrefix=${if (key.length > 4) key.substring(0, 4) else key}")
        return try {
            val response = client.get("$baseUrl/api/categories") {
                header("X-API-Key", key)
            }
            Log.d(TAG, "healthCheck: response code=${response.status.value}")
            if (response.status.isSuccess()) {
                Result.success("连接成功（服务器和 API Key 均有效）")
            } else {
                val body = try { response.body<String>() } catch (_: Exception) { "" }
                Result.failure(Exception("服务器返回 HTTP ${response.status.value}: $body"))
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "DNS解析失败", e)
            Result.failure(Exception("DNS 解析失败：无法解析服务器域名（${e.message}）"))
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "连接失败", e)
            Result.failure(Exception("连接被拒绝：服务器可能未启动或端口未开放（${e.message}）"))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "连接超时", e)
            Result.failure(Exception("连接超时：服务器响应超时（${e.message}）"))
        } catch (e: javax.net.ssl.SSLException) {
            Log.e(TAG, "SSL错误", e)
            Result.failure(Exception("SSL 错误：${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "健康检查失败", e)
            Result.failure(Exception("连接失败：${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    suspend fun getCategories(): Result<List<Category>> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.get("$baseUrl/api/categories") {
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                val categories: List<Category> = response.body()
                Result.success(categories)
            } else {
                Result.failure(Exception("获取分类失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取分类失败", e)
            Result.failure(e)
        }
    }

    suspend fun createCategory(name: String, displayOrder: Int = 0): Result<Category> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.post("$baseUrl/api/categories") {
                contentType(ContentType.Application.Json)
                setBody(CategoryCreateRequest(name = name, displayOrder = displayOrder))
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                val category: Category = response.body()
                Result.success(category)
            } else {
                Result.failure(Exception("创建分类失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建分类失败", e)
            Result.failure(Exception("创建分类异常: ${e.message}"))
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.delete("$baseUrl/api/categories/$categoryId") {
                applyApiKey()
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception("删除分类失败: ${errorMessage(response)}"))
        } catch (e: Exception) {
            Log.e(TAG, "删除分类失败", e)
            Result.failure(e)
        }
    }

    suspend fun updateCategory(categoryId: String, name: String): Result<Category> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.put("$baseUrl/api/categories/$categoryId") {
                contentType(ContentType.Application.Json)
                setBody(CategoryUpdateRequest(name = name))
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("修改分类失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "修改分类失败", e)
            Result.failure(e)
        }
    }

    suspend fun updateVideoCategory(videoId: String, category: String): Result<Video> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.put("$baseUrl/api/videos/$videoId/category") {
                contentType(ContentType.Application.Json)
                setBody(VideoCategoryUpdate(category = category))
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                val video: Video = response.body()
                Result.success(video)
            } else {
                Result.failure(Exception("更新分类失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新视频分类失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取所有系列
     */
    suspend fun getSeries(): Result<List<Series>> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.get("$baseUrl/api/series") {
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                val series: List<Series> = response.body()
                Result.success(series)
            } else {
                Result.failure(Exception("获取系列列表失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取系列列表失败", e)
            Result.failure(e)
        }
    }

    /**
     * 创建新系列
     */
    suspend fun createSeries(request: SeriesCreateRequest): Result<Series> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.post("$baseUrl/api/series") {
                contentType(ContentType.Application.Json)
                setBody(request)
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                val series: Series = response.body()
                Result.success(series)
            } else {
                Result.failure(Exception("创建系列失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建系列失败", e)
            Result.failure(Exception("创建系列异常: ${e.message}"))
        }
    }

    /**
     * 修改系列
     */
    suspend fun updateSeries(seriesId: String, title: String, description: String): Result<Series> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.put("$baseUrl/api/series/$seriesId") {
                contentType(ContentType.Application.Json)
                setBody(SeriesUpdateRequest(title = title, description = description))
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("修改系列失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "修改系列失败", e)
            Result.failure(e)
        }
    }

    /**
     * 删除系列
     */
    suspend fun deleteSeries(seriesId: String): Result<Unit> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.delete("$baseUrl/api/series/$seriesId") {
                applyApiKey()
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception("删除系列失败: ${errorMessage(response)}"))
        } catch (e: Exception) {
            Log.e(TAG, "删除系列失败", e)
            Result.failure(e)
        }
    }

    /**
     * 将视频加入系列
     */
    suspend fun addVideoToSeries(seriesId: String, videoId: String): Result<Unit> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.post("$baseUrl/api/series/$seriesId/videos") {
                contentType(ContentType.Application.Json)
                setBody(SeriesVideoAddRequest(video_ids = listOf(videoId)))
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("加入系列失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "加入系列失败", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 家长控制
    // =========================================================================

    suspend fun getParentalControl(): Result<ParentalControlConfig> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.get("$baseUrl/api/parental-control") {
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("获取配置失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取家长控制配置失败", e)
            Result.failure(e)
        }
    }

    suspend fun saveParentalControl(config: ParentalControlConfig): Result<ParentalControlConfig> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.post("$baseUrl/api/parental-control") {
                contentType(ContentType.Application.Json)
                setBody(config)
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("保存配置失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存家长控制配置失败", e)
            Result.failure(e)
        }
    }

    suspend fun getWatchStats(date: String): Result<WatchStats> {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) return Result.failure(Exception("未配置NAS服务器地址"))
        return try {
            val response = client.get("$baseUrl/api/watch-stats") {
                parameter("date", date)
                applyApiKey()
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("获取统计失败: ${errorMessage(response)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取观看统计失败", e)
            Result.failure(e)
        }
    }
}
