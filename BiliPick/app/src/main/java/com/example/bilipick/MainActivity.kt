package com.example.bilipick

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.bilipick.network.BiliApiService
import com.example.bilipick.network.NasApiService
import com.example.bilipick.ui.screens.SettingsScreen
import com.example.bilipick.ui.screens.VideoListScreen
import com.example.bilipick.ui.screens.ShareHandlerScreen
import com.example.bilipick.ui.theme.BiliPickTheme

class MainActivity : ComponentActivity() {

    private lateinit var nasApiService: NasApiService
    private val biliApiService = BiliApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nasApiService = NasApiService(this)

        // 处理分享链接
        handleShareIntent(intent)

        setContent {
            BiliPickTheme {
                BiliPickApp(
                    nasApiService = nasApiService,
                    biliApiService = biliApiService
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * 处理分享链接
     */
    private fun handleShareIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    sharedText?.let {
                        // 保存分享链接，在UI中处理
                        ShareDataHolder.sharedUrl = it
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data
                data?.let {
                    ShareDataHolder.sharedUrl = it.toString()
                }
            }
        }
    }
}

/**
 * 全局分享数据持有者
 */
object ShareDataHolder {
    var sharedUrl: String? = null
}

@Composable
fun BiliPickApp(
    nasApiService: NasApiService,
    biliApiService: BiliApiService
) {
    var currentDestination by remember { mutableStateOf(AppDestinations.VIDEO_LIST) }

    // 检查是否有分享链接
    val sharedUrl = ShareDataHolder.sharedUrl
    var showShareDialog by remember { mutableStateOf(sharedUrl != null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "视频列表") },
                    label = { Text("视频列表") },
                    selected = currentDestination == AppDestinations.VIDEO_LIST,
                    onClick = { currentDestination = AppDestinations.VIDEO_LIST }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "推送记录") },
                    label = { Text("推送记录") },
                    selected = currentDestination == AppDestinations.HISTORY,
                    onClick = { currentDestination = AppDestinations.HISTORY }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = currentDestination == AppDestinations.SETTINGS,
                    onClick = { currentDestination = AppDestinations.SETTINGS }
                )
            }
        }
    ) { innerPadding ->
        when (currentDestination) {
            AppDestinations.VIDEO_LIST -> {
                VideoListScreen(
                    nasApiService = nasApiService,
                    biliApiService = biliApiService,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            AppDestinations.HISTORY -> {
                VideoListScreen(
                    nasApiService = nasApiService,
                    biliApiService = biliApiService,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            AppDestinations.SETTINGS -> {
                SettingsScreen(
                    nasApiService = nasApiService,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    // 显示分享处理对话框
    if (showShareDialog && sharedUrl != null) {
        ShareHandlerScreen(
            sharedText = sharedUrl,
            nasApiService = nasApiService,
            biliApiService = biliApiService,
            onDismiss = {
                showShareDialog = false
                ShareDataHolder.sharedUrl = null
            },
            onSuccess = {
                showShareDialog = false
                ShareDataHolder.sharedUrl = null
                // 切换到视频列表页
                currentDestination = AppDestinations.VIDEO_LIST
            }
        )
    }
}

enum class AppDestinations {
    VIDEO_LIST,
    HISTORY,
    SETTINGS
}
