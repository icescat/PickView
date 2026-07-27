package com.example.bilipick.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bilipick.data.model.BiliVideoInfo
import com.example.bilipick.data.model.Category
import com.example.bilipick.data.model.Series
import com.example.bilipick.data.model.SeriesCreateRequest
import com.example.bilipick.data.model.Video
import com.example.bilipick.data.model.VideoCreateRequest
import com.example.bilipick.network.BiliApiService
import com.example.bilipick.network.NasApiService
import kotlinx.coroutines.launch

enum class LayoutMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    nasApiService: NasApiService,
    biliApiService: BiliApiService = BiliApiService(),
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Video?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var layoutMode by remember { mutableStateOf(LayoutMode.LIST) }
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchCategoryDialog by remember { mutableStateOf(false) }
    var showBatchSeriesDialog by remember { mutableStateOf(false) }

    fun loadVideos() {
        if (!nasApiService.isConfigured()) {
            errorMessage = "请先配置NAS服务器地址"
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            nasApiService.getVideos()
                .onSuccess { videoList -> videos = videoList }
                .onFailure { error -> errorMessage = error.message ?: "获取视频列表失败" }
            isLoading = false
        }
    }

    fun deleteVideo(videoId: String) {
        scope.launch {
            nasApiService.deleteVideo(videoId)
                .onSuccess { loadVideos() }
                .onFailure { error -> errorMessage = error.message ?: "删除失败" }
        }
    }

    fun batchDeleteVideos() {
        scope.launch {
            selectedIds.forEach { id -> nasApiService.deleteVideo(id) }
            isSelectMode = false
            selectedIds = emptySet()
            loadVideos()
        }
    }

    fun batchUpdateCategory(categoryName: String) {
        scope.launch {
            selectedIds.forEach { id -> nasApiService.updateVideoCategory(id, categoryName) }
            isSelectMode = false
            selectedIds = emptySet()
            showBatchCategoryDialog = false
            loadVideos()
        }
    }

    fun batchAddToSeries(series: Series?) {
        if (series == null) {
            showBatchSeriesDialog = false
            isSelectMode = false
            selectedIds = emptySet()
            return
        }
        scope.launch {
            selectedIds.forEach { id -> nasApiService.addVideoToSeries(series.id, id) }
            isSelectMode = false
            selectedIds = emptySet()
            showBatchSeriesDialog = false
            loadVideos()
        }
    }

    fun toggleSelect(videoId: String) {
        selectedIds = if (selectedIds.contains(videoId)) selectedIds - videoId else selectedIds + videoId
        if (selectedIds.isEmpty()) isSelectMode = false
    }

    fun exitSelectMode() {
        isSelectMode = false
        selectedIds = emptySet()
    }

    // 在B站官方App中打开视频；未安装则回退到浏览器
    fun openInBili(video: Video) {
        val biliIntent = Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://video/${video.bvid}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(biliIntent)
        } catch (e: Exception) {
            // B站App未安装，回退到浏览器打开网页版
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bilibili.com/video/${video.bvid}/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "未找到可打开的应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) { loadVideos() }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (isSelectMode) {
                TopAppBar(
                    title = { Text("已选择 ${selectedIds.size} 个") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    },
                    actions = {
                        TextButton(onClick = { selectedIds = videos.map { it.id }.toSet() }) { Text("全选") }
                        IconButton(onClick = { showBatchCategoryDialog = true }) {
                            Icon(Icons.Default.Category, contentDescription = "批量分类")
                        }
                        IconButton(onClick = { showBatchSeriesDialog = true }) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "批量加入系列")
                        }
                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "批量删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("精选视频") },
                    actions = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.contains("b23.tv") || clipText.contains("bilibili.com") || clipText.contains("BV")) {
                                LinkAddDialogHelper.inputUrl = clipText.trim()
                            }
                            showAddDialog = true
                        }) { Icon(Icons.Default.ContentPaste, contentDescription = "粘贴链接") }
                        IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "添加视频") }
                        IconButton(onClick = {
                            layoutMode = if (layoutMode == LayoutMode.LIST) LayoutMode.GRID else LayoutMode.LIST
                        }) {
                            Icon(if (layoutMode == LayoutMode.LIST) Icons.Default.GridView else Icons.Default.ViewList, contentDescription = "切换布局")
                        }
                        IconButton(onClick = { loadVideos() }) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                errorMessage != null -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { loadVideos() }) { Text("重试") }
                    }
                }
                videos.isEmpty() -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无视频", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("点击 + 或粘贴链接添加视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                layoutMode == LayoutMode.LIST -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(videos) { video ->
                            VideoListCard(
                                video = video, isSelectMode = isSelectMode, isSelected = selectedIds.contains(video.id),
                                onClick = { if (isSelectMode) toggleSelect(video.id) else openInBili(video) },
                                onLongClick = { if (!isSelectMode) { isSelectMode = true; selectedIds = setOf(video.id) } },
                                onDelete = { if (isSelectMode) toggleSelect(video.id) else showDeleteDialog = video }
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(videos) { video ->
                            VideoGridCard(
                                video = video, isSelectMode = isSelectMode, isSelected = selectedIds.contains(video.id),
                                onClick = { if (isSelectMode) toggleSelect(video.id) else openInBili(video) },
                                onLongClick = { if (!isSelectMode) { isSelectMode = true; selectedIds = setOf(video.id) } },
                                onDelete = { if (isSelectMode) toggleSelect(video.id) else showDeleteDialog = video }
                            )
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { video ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除视频「${video.title}」吗？") },
            confirmButton = { TextButton(onClick = { deleteVideo(video.id); showDeleteDialog = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消") } }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("批量删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 个视频吗？") },
            confirmButton = { TextButton(onClick = { batchDeleteVideos(); showBatchDeleteDialog = false }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } }
        )
    }

    if (showBatchCategoryDialog) {
        CategorySelectDialog(
            nasApiService = nasApiService,
            onDismiss = { showBatchCategoryDialog = false },
            onSelect = { categoryName -> batchUpdateCategory(categoryName) }
        )
    }

    if (showBatchSeriesDialog) {
        BatchSeriesSelectDialog(
            nasApiService = nasApiService,
            onDismiss = { showBatchSeriesDialog = false },
            onSelect = { series -> batchAddToSeries(series) }
        )
    }

    if (showAddDialog) {
        LinkAddDialog(biliApiService = biliApiService, nasApiService = nasApiService, onDismiss = { showAddDialog = false }, onSuccess = { showAddDialog = false; loadVideos() })
    }
}

@Composable
fun SelectCheckbox(isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(24.dp),
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
        shadowElevation = 2.dp
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "已选", modifier = Modifier.padding(2.dp), tint = Color.White)
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(2.dp).border(1.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape))
        }
    }
}

@Composable
fun VideoListCard(
    video: Video, isSelectMode: Boolean, isSelected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                Box(
                    modifier = Modifier.width(160.dp).fillMaxHeight().clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (video.cover.isNotEmpty()) {
                        AsyncImage(model = video.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = formatDuration(video.duration), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Column {
                        Text(text = video.upName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                Text(text = "由 ${video.addedBy} 添加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (video.category.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = video.category,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            if (!isSelectMode) {
                                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isSelectMode) {
            SelectCheckbox(
                isSelected = isSelected, onClick = onClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
    }
}

@Composable
fun VideoGridCard(
    video: Video, isSelectMode: Boolean, isSelected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
        ) {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (video.cover.isNotEmpty()) {
                        AsyncImage(model = video.cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = formatDuration(video.duration), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(text = video.upName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (video.category.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = video.category,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        if (!isSelectMode) {
                            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        if (isSelectMode) {
            SelectCheckbox(
                isSelected = isSelected, onClick = onClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) "${minutes}分${remainingSeconds}秒" else "${remainingSeconds}秒"
}

@Composable
fun LinkAddDialog(
    biliApiService: BiliApiService,
    nasApiService: NasApiService,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var inputUrl by remember { mutableStateOf(LinkAddDialogHelper.inputUrl) }
    var parseStep by remember { mutableStateOf(ParseStep.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoInfo by remember { mutableStateOf<BiliVideoInfo?>(null) }
    var bvid by remember { mutableStateOf<String?>(null) }
    // 系列选择状态
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var showSeriesDialog by remember { mutableStateOf(false) }
    var showCreateSeriesDialog by remember { mutableStateOf(false) }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        if (clipText.isNotBlank()) inputUrl = clipText.trim()
    }

    fun extractBvidFromText(text: String): String? {
        "BV[\\w]+".toRegex().find(text)?.let { return it.value }
        "https?://[\\w.-]+[\\w/.-]*".toRegex().find(text)?.let { return it.value }
        return null
    }

    suspend fun parseAndFetch(url: String) {
        parseStep = ParseStep.PARSING; errorMessage = null
        val extracted = extractBvidFromText(url)
        if (extracted != null && extracted.startsWith("BV")) { bvid = extracted }
        else if (extracted != null && (extracted.contains("b23.tv") || extracted.contains("bilibili.com"))) {
            val result = biliApiService.getBvidFromShortUrl(extracted)
            if (result == null) { errorMessage = "无法从链接中提取视频ID"; parseStep = ParseStep.ERROR; return }
            bvid = result
        } else { errorMessage = "无法识别链接"; parseStep = ParseStep.ERROR; return }
        parseStep = ParseStep.FETCHING_INFO
        val info = biliApiService.getVideoInfo(bvid!!)
        if (info == null) { errorMessage = "获取视频信息失败"; parseStep = ParseStep.ERROR; return }
        videoInfo = info
        // 预加载系列列表
        nasApiService.getSeries().onSuccess { seriesList = it }
        parseStep = ParseStep.CONFIRMING
    }

    suspend fun pushToNas() {
        parseStep = ParseStep.PUSHING
        nasApiService.addVideo(VideoCreateRequest(
            bvid = bvid ?: "", title = videoInfo?.title ?: "", upName = videoInfo?.owner?.name ?: "",
            duration = videoInfo?.duration ?: 0, category = videoInfo?.tname?.ifBlank { "其他" } ?: "其他",
            cover = videoInfo?.pic ?: "", addedBy = android.os.Build.MODEL
        )).onSuccess { video ->
            // 若选择了系列，将视频加入系列
            val targetSeries = selectedSeries
            if (targetSeries != null) {
                nasApiService.addVideoToSeries(targetSeries.id, video.id)
                    .onFailure { e -> errorMessage = "视频已推送，但加入系列失败: ${e.message}" }
            }
            parseStep = ParseStep.SUCCESS; kotlinx.coroutines.delay(800); onSuccess()
        }.onFailure { errorMessage = it.message; parseStep = ParseStep.ERROR }
    }

    AlertDialog(
        onDismissRequest = { if (parseStep != ParseStep.PUSHING) onDismiss() },
        title = { Text("添加视频") },
        text = {
            when (parseStep) {
                ParseStep.IDLE, ParseStep.ERROR -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = inputUrl, onValueChange = { inputUrl = it }, label = { Text("B站视频链接或BV号") }, placeholder = { Text("粘贴链接或输入BV号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { pasteFromClipboard() }) { Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("粘贴") }
                        }
                        errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Text(text = "支持格式：\n• BV号\n• 短链接（b23.tv）\n• 完整链接", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ParseStep.PARSING -> { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { CircularProgressIndicator(); Spacer(modifier = Modifier.height(12.dp)); Text("正在解析链接...") } }
                ParseStep.FETCHING_INFO -> { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { CircularProgressIndicator(); Spacer(modifier = Modifier.height(12.dp)); Text("正在获取视频信息...") } }
                ParseStep.CONFIRMING -> {
                    videoInfo?.let { info ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(info.title, style = MaterialTheme.typography.titleMedium)
                            Text("UP主: ${info.owner.name}")
                            Text("分类: ${info.tname ?: "其他"}")
                            Text("时长: ${formatDuration(info.duration)}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("加入系列（可选）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { showSeriesDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = selectedSeries?.title ?: "不加入系列",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                IconButton(onClick = { showCreateSeriesDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "新建系列")
                                }
                            }
                        }
                    }
                }
                ParseStep.PUSHING -> { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { CircularProgressIndicator(); Spacer(modifier = Modifier.height(12.dp)); Text("正在推送到NAS...") } }
                ParseStep.SUCCESS -> { Text("✓ 推送成功", color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            when (parseStep) {
                ParseStep.IDLE, ParseStep.ERROR -> TextButton(onClick = { if (inputUrl.isNotBlank()) scope.launch { parseAndFetch(inputUrl) } }, enabled = inputUrl.isNotBlank()) { Text("解析") }
                ParseStep.CONFIRMING -> TextButton(onClick = { scope.launch { pushToNas() } }) { Text("确认推送") }
                ParseStep.SUCCESS -> TextButton(onClick = onSuccess) { Text("确定") }
                else -> {}
            }
        },
        dismissButton = { if (parseStep != ParseStep.PUSHING && parseStep != ParseStep.SUCCESS) TextButton(onClick = onDismiss) { Text("取消") } }
    )

    // 系列选择对话框
    if (showSeriesDialog) {
        SeriesSelectDialog(
            seriesList = seriesList,
            selectedSeries = selectedSeries,
            onDismiss = { showSeriesDialog = false },
            onSelect = {
                selectedSeries = it
                showSeriesDialog = false
            }
        )
    }

    // 新建系列对话框
    if (showCreateSeriesDialog) {
        CreateSeriesDialog(
            nasApiService = nasApiService,
            onDismiss = { showCreateSeriesDialog = false },
            onCreated = { newSeries ->
                seriesList = seriesList + newSeries
                selectedSeries = newSeries
                showCreateSeriesDialog = false
            }
        )
    }
}

enum class ParseStep { IDLE, PARSING, FETCHING_INFO, CONFIRMING, PUSHING, SUCCESS, ERROR }

@Composable
fun CategorySelectDialog(
    nasApiService: NasApiService,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        nasApiService.getCategories()
            .onSuccess { categories = it; isLoading = false }
            .onFailure { errorMessage = it.message; isLoading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择分类") },
        text = {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                errorMessage != null -> {
                    Text(text = errorMessage ?: "加载分类失败", color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.forEach { category ->
                            Surface(
                                onClick = { onSelect(category.name) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = category.name,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

object LinkAddDialogHelper { var inputUrl: String = "" }

/**
 * 批量加入系列选择对话框
 */
@Composable
fun BatchSeriesSelectDialog(
    nasApiService: NasApiService,
    onDismiss: () -> Unit,
    onSelect: (Series?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        nasApiService.getSeries()
            .onSuccess { seriesList = it; isLoading = false }
            .onFailure { errorMessage = it.message; isLoading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入系列") },
        text = {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                errorMessage != null -> {
                    Text(text = errorMessage ?: "加载系列失败", color = MaterialTheme.colorScheme.error)
                }
                seriesList.isEmpty() -> {
                    Text("暂无系列，请先创建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        seriesList.forEach { series ->
                            Surface(
                                onClick = { onSelect(series) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = series.title,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("新建系列")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showCreateDialog) {
        CreateSeriesDialog(
            nasApiService = nasApiService,
            onDismiss = { showCreateDialog = false },
            onCreated = { newSeries ->
                seriesList = seriesList + newSeries
                showCreateDialog = false
            }
        )
    }
}
