package dev.aaa1115910.bv.screen.main

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.util.toAv
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.network.NasSeries
import dev.aaa1115910.bv.network.NasVideoItem
import dev.aaa1115910.bv.network.NasVideoSession
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.curated.BrowseMode
import dev.aaa1115910.bv.viewmodel.curated.CuratedVideoViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeContent(
    navFocusRequester: FocusRequester,
    browseMode: BrowseMode,
    userViewModel: UserViewModel = koinViewModel(),
    curatedViewModel: CuratedVideoViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val watchStats by curatedViewModel.watchStats.collectAsState()

    LaunchedEffect(userViewModel.isLogin) {
        if (userViewModel.isLogin) {
            userViewModel.updateUserInfo()
        } else {
            userViewModel.clearUserInfo()
        }
    }

    LaunchedEffect(Unit) {
        curatedViewModel.loadParentalControlFromServer()
        curatedViewModel.loadWatchStatsFromServer()
        curatedViewModel.loadCategories()
        curatedViewModel.loadSeries()
        curatedViewModel.loadVideos()
    }

    // 根据 browseMode 参数切换浏览模式
    LaunchedEffect(browseMode) {
        curatedViewModel.switchBrowseMode(browseMode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(navFocusRequester)
    ) {
        // 根据模式显示不同的 TabRow（由左侧导航栏控制）
        when (browseMode) {
            BrowseMode.CATEGORY -> {
                if (curatedViewModel.categories.isNotEmpty()) {
                    CategoryTabRow(
                        categories = curatedViewModel.categories,
                        selectedCategory = curatedViewModel.selectedCategory,
                        onCategorySelected = { curatedViewModel.selectCategory(it) }
                    )
                }
            }
            BrowseMode.SERIES -> {
                if (curatedViewModel.seriesList.isNotEmpty()) {
                    SeriesTabRow(
                        seriesList = curatedViewModel.seriesList,
                        selectedSeriesId = curatedViewModel.selectedSeriesId,
                        onSeriesSelected = { curatedViewModel.selectSeries(it) }
                    )
                }
            }
        }

        if (curatedViewModel.remainingSeconds != Int.MAX_VALUE) {
            RemainingTimeBar(
                remainingSeconds = curatedViewModel.remainingSeconds,
                remainingCount = curatedViewModel.remainingCount
            )
        }

        when {
            curatedViewModel.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            curatedViewModel.errorMessage != null -> {
                ErrorView(
                    message = curatedViewModel.errorMessage!!,
                    onRetry = { curatedViewModel.loadVideos() }
                )
            }

            curatedViewModel.isOutsideAllowedTime -> {
                MessageView("现在不是观看时间，请稍后再来吧！")
            }

            curatedViewModel.isLimitReached -> {
                MessageView("今天观看额度已用完，明天再看吧！")
            }

            curatedViewModel.filteredVideos.isEmpty() -> {
                val msg = when (browseMode) {
                    BrowseMode.CATEGORY -> if (curatedViewModel.selectedCategory != null)
                        "该分类下暂无视频" else "暂无精选视频，请家长添加视频"
                    BrowseMode.SERIES -> if (curatedViewModel.selectedSeriesId != null)
                        "该系列下暂无视频" else "暂无系列，请家长在Web管理页创建"
                }
                MessageView(msg)
            }

            else -> {
                VideoGrid(
                    videos = curatedViewModel.filteredVideos,
                    onClickVideo = { video ->
                        // 记录当前 NAS 视频信息，供播放器上报观看状态、详情页显示入库/观看时间
                        NasVideoSession.set(
                            videoId = video.id,
                            bvid = video.bvid,
                            addedAt = video.added_at,
                            lastWatchedAt = video.last_watched_at
                        )
                        VideoInfoActivity.actionStart(
                            context = context as Activity,
                            aid = video.bvid.toAv(),
                            fromSeason = false
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryTabRow(
    categories: List<dev.aaa1115910.bv.network.NasCategory>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    val tabNames = listOf("全部") + categories.map { it.name }

    TabRow(
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
        selectedTabIndex = if (selectedCategory == null) 0 else {
            val idx = categories.indexOfFirst { it.name == selectedCategory }
            if (idx >= 0) idx + 1 else 0
        },
        separator = { Spacer(modifier = Modifier.width(8.dp)) }
    ) {
        tabNames.forEachIndexed { index, name ->
            Tab(
                modifier = Modifier.clickable {
                    onCategorySelected(if (index == 0) null else categories[index - 1].name)
                },
                selected = (index == 0 && selectedCategory == null) ||
                        (index > 0 && categories.getOrNull(index - 1)?.name == selectedCategory),
                onFocus = {
                    onCategorySelected(if (index == 0) null else categories[index - 1].name)
                },
                onClick = {}
            ) {
                Text(
                    text = name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesTabRow(
    seriesList: List<NasSeries>,
    selectedSeriesId: String?,
    onSeriesSelected: (String?) -> Unit
) {
    val tabNames = listOf("全部") + seriesList.map { it.title }

    TabRow(
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
        selectedTabIndex = if (selectedSeriesId == null) 0 else {
            val idx = seriesList.indexOfFirst { it.id == selectedSeriesId }
            if (idx >= 0) idx + 1 else 0
        },
        separator = { Spacer(modifier = Modifier.width(8.dp)) }
    ) {
        tabNames.forEachIndexed { index, name ->
            Tab(
                modifier = Modifier.clickable {
                    onSeriesSelected(if (index == 0) null else seriesList[index - 1].id)
                },
                selected = (index == 0 && selectedSeriesId == null) ||
                        (index > 0 && seriesList.getOrNull(index - 1)?.id == selectedSeriesId),
                onFocus = {
                    onSeriesSelected(if (index == 0) null else seriesList[index - 1].id)
                },
                onClick = {}
            ) {
                Text(
                    text = name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun RemainingTimeBar(
    remainingSeconds: Int,
    remainingCount: Int
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60

    Surface(
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (remainingSeconds < Int.MAX_VALUE) {
                Text(
                    text = "剩余 ${minutes}分${seconds}秒",
                    color = if (remainingSeconds < 300) Color(0xFFFF6B6B) else Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (remainingCount < Int.MAX_VALUE) {
                Text(
                    text = "还可看 ${remainingCount} 个视频",
                    color = if (remainingCount <= 1) Color(0xFFFF6B6B) else Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun VideoGrid(
    videos: List<NasVideoItem>,
    onClickVideo: (NasVideoItem) -> Unit
) {
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }

    TvLazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(
            start = 48.dp, end = 48.dp, top = 8.dp, bottom = 64.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        itemsIndexed(items = videos, key = { _, video -> video.bvid }) { index, video ->
            VideoCard(
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                video = video,
                onClick = { onClickVideo(video) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoCard(
    modifier: Modifier = Modifier,
    video: NasVideoItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        onClick = {},
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium
        )
    ) {
        Column {
            Box {
                AsyncImage(
                    model = video.cover,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .height(140.dp),
                    contentScale = ContentScale.Crop
                )
                // 观看状态角标
                WatchBadge(
                    status = video.watch_status,
                    progress = video.watch_progress,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                )
            }
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = video.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = video.up_name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = (video.duration * 1000L).formatHourMinSec(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                if (video.category.isNotBlank()) {
                    Text(
                        text = video.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchBadge(
    status: String?,
    progress: Float,
    modifier: Modifier = Modifier
) {
    if (status == null || status == "unwatched") return
    val (text, color) = when (status) {
        "watching" -> "在看 ${(progress * 100).toInt()}%" to Color(0xFFE65100)
        "watched" -> "已看" to Color(0xFF43A047)
        else -> return
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color.Yellow,
                modifier = Modifier.height(48.dp).width(48.dp)
            )
            Text(text = message, color = Color.White)
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun MessageView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
