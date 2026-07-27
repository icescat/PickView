package com.example.bilipick.network

import android.util.Log
import com.example.bilipick.data.model.BiliVideoInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class BiliApiService {

    private val TAG = "BiliApiService"

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
            level = LogLevel.ALL
        }
        followRedirects = true
    }

    /**
     * 从短链接获取BV号
     */
    suspend fun getBvidFromShortUrl(shortUrl: String): String? {
        return try {
            val response: HttpResponse = client.get(shortUrl)
            val finalUrl = response.request.url.toString()
            Log.d(TAG, "最终URL: $finalUrl")
            extractBvidFromUrl(finalUrl)
        } catch (e: Exception) {
            Log.e(TAG, "获取BV号失败", e)
            null
        }
    }

    private fun extractBvidFromUrl(url: String): String? {
        val regex = "video/(BV[\\w]+)".toRegex()
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)
    }

    /**
     * 获取视频详情
     */
    suspend fun getVideoInfo(bvid: String): BiliVideoInfo? {
        return try {
            val response: HttpResponse = client.get("https://api.bilibili.com/x/web-interface/view") {
                parameter("bvid", bvid)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header("Referer", "https://www.bilibili.com")
            }

            val responseText = response.body<String>()
            Log.d(TAG, "B站API响应: $responseText")

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(responseText).jsonObject
            val data = root["data"]?.jsonObject ?: return null

            val bvidValue = data["bvid"]?.jsonPrimitive?.content ?: bvid
            val title = data["title"]?.jsonPrimitive?.content ?: ""
            val ownerName = data["owner"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
            val duration = data["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val tname = data["tname"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull ?: "其他"
            val pic = data["pic"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull ?: ""

            BiliVideoInfo(
                bvid = bvidValue,
                title = title,
                owner = BiliVideoInfo.Owner(name = ownerName),
                duration = duration,
                tname = tname,
                pic = pic
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取视频信息失败", e)
            null
        }
    }
}
