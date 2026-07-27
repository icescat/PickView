package com.example.bilipick.network

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Ktor HTTP客户端配置
 */
object KtorClient {

    private const val TAG = "KtorClient"

    /**
     * JSON配置
     */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 创建HTTP客户端
     */
    fun createClient(): HttpClient {
        return HttpClient(Android) {
            // 安装内容协商插件，支持JSON序列化
            install(ContentNegotiation) {
                json(json)
            }

            // 安装日志插件
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d(TAG, message)
                    }
                }
                level = LogLevel.ALL
            }

            // 默认请求配置
            defaultRequest {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            // 超时配置（外网 DDNS 访问需要更长超时）
            engine {
                connectTimeout = 30000
                socketTimeout = 30000
            }
        }
    }
}
