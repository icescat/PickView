package com.example.bilipick.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bilipick.data.model.BiliVideoInfo
import com.example.bilipick.data.model.Category
import com.example.bilipick.data.model.Series
import com.example.bilipick.data.model.SeriesCreateRequest
import com.example.bilipick.data.model.VideoCreateRequest
import com.example.bilipick.network.BiliApiService
import com.example.bilipick.network.NasApiService
import kotlinx.coroutines.launch

@Composable
fun ShareHandlerScreen(
    sharedText: String,
    nasApiService: NasApiService,
    biliApiService: BiliApiService,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(ShareStep.PARSING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoInfo by remember { mutableStateOf<BiliVideoInfo?>(null) }
    var bvid by remember { mutableStateOf<String?>(null) }
    // 系列选择状态
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var showSeriesDialog by remember { mutableStateOf(false) }
    var showCreateSeriesDialog by remember { mutableStateOf(false) }
    // 归档分类选择状态（默认不分类）
    var categoryList by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    /**
     * 从分享文本中提取URL或BV号
     * 支持格式：
     * - "标题 https://b23.tv/xxx"
     * - "标题 https://www.bilibili.com/video/BVxxx"
     * - "https://b23.tv/xxx"
     * - "BV1rwLP6HEay"
     */
    fun extractFromSharedText(text: String): String? {
        // 1. 先尝试提取BV号
        val bvRegex = "BV[\\w]+".toRegex()
        bvRegex.find(text)?.let { return it.value }

        // 2. 尝试提取短链接
        val shortUrlRegex = "https?://b23\\.tv/[\\w]+".toRegex()
        shortUrlRegex.find(text)?.let { return it.value }

        // 3. 尝试提取完整链接
        val fullUrlRegex = "https?://www\\.bilibili\\.com/video/[\\w\\-/?=&]+".toRegex()
        fullUrlRegex.find(text)?.let { return it.value }

        // 4. 尝试提取任意URL
        val urlRegex = "https?://[\\w.-]+[\\w/.-]*".toRegex()
        urlRegex.find(text)?.let { return it.value }

        return null
    }

    LaunchedEffect(sharedText) {
        currentStep = ShareStep.PARSING

        // 从分享文本中提取链接或BV号
        val extracted = extractFromSharedText(sharedText)

        if (extracted == null) {
            errorMessage = "无法从分享内容中提取视频链接"
            currentStep = ShareStep.ERROR
            return@LaunchedEffect
        }

        // 如果提取到的是BV号，直接使用
        if (extracted.startsWith("BV")) {
            bvid = extracted
        } else {
            // 如果是短链接，访问获取BV号
            val result = biliApiService.getBvidFromShortUrl(extracted)
            if (result == null) {
                errorMessage = "无法从链接中提取视频ID"
                currentStep = ShareStep.ERROR
                return@LaunchedEffect
            }
            bvid = result
        }

        // 获取视频详情
        currentStep = ShareStep.FETCHING_INFO
        val info = biliApiService.getVideoInfo(bvid!!)

        if (info == null) {
            errorMessage = "获取视频信息失败"
            currentStep = ShareStep.ERROR
            return@LaunchedEffect
        }

        videoInfo = info
        // 预加载系列列表与归档分类列表，供确认步骤选择
        nasApiService.getSeries().onSuccess { seriesList = it }
        nasApiService.getCategories().onSuccess { categoryList = it }
        currentStep = ShareStep.CONFIRMING
    }

    AlertDialog(
        onDismissRequest = {
            if (currentStep != ShareStep.PUSHING) {
                onDismiss()
            }
        },
        title = { Text("推送视频到NAS") },
        text = {
            when (currentStep) {
                ShareStep.PARSING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在解析链接...")
                    }
                }
                ShareStep.FETCHING_INFO -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在获取视频信息...")
                    }
                }
                ShareStep.CONFIRMING -> {
                    videoInfo?.let { info ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "UP主: ${info.owner.name}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "B站分类: ${info.tname?.ifBlank { "其他" } ?: "其他"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "时长: ${formatDuration(info.duration)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // 归档分类选择（可选，默认不分类）
                            Text(
                                text = "归档分类（可选）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                onClick = { showCategoryDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = selectedCategory?.name ?: "不分类",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            // 系列选择（可选）
                            Text(
                                text = "加入系列（可选）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                ShareStep.PUSHING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在推送到NAS...")
                    }
                }
                ShareStep.SUCCESS -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ 推送成功",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                ShareStep.ERROR -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✗ ${errorMessage ?: "未知错误"}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (currentStep) {
                ShareStep.CONFIRMING -> {
                    TextButton(
                        onClick = {
                            scope.launch {
                                currentStep = ShareStep.PUSHING

                                val request = VideoCreateRequest(
                                    bvid = bvid ?: "",
                                    title = videoInfo?.title ?: "",
                                    upName = videoInfo?.owner?.name ?: "",
                                    duration = videoInfo?.duration ?: 0,
                                    category = selectedCategory?.name ?: "",
                                    cover = videoInfo?.pic ?: "",
                                    addedBy = android.os.Build.MODEL
                                )

                                nasApiService.addVideo(request)
                                    .onSuccess { video ->
                                        // 若选择了系列，将视频加入系列
                                        val targetSeries = selectedSeries
                                        if (targetSeries != null) {
                                            nasApiService.addVideoToSeries(targetSeries.id, video.id)
                                                .onFailure { e ->
                                                    // 加入系列失败不阻断主流程，仅记录错误
                                                    errorMessage = "视频已推送，但加入系列失败: ${e.message}"
                                                }
                                        }
                                        currentStep = ShareStep.SUCCESS
                                        kotlinx.coroutines.delay(1000)
                                        onSuccess()
                                    }
                                    .onFailure { error ->
                                        errorMessage = error.message
                                        currentStep = ShareStep.ERROR
                                    }
                            }
                        }
                    ) {
                        Text("确认推送")
                    }
                }
                ShareStep.SUCCESS -> {
                    TextButton(onClick = onSuccess) {
                        Text("确定")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (currentStep != ShareStep.PUSHING && currentStep != ShareStep.SUCCESS) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )

    // 归档分类选择对话框
    if (showCategoryDialog) {
        ArchiveCategorySelectDialog(
            categoryList = categoryList,
            selectedCategory = selectedCategory,
            onDismiss = { showCategoryDialog = false },
            onSelect = {
                selectedCategory = it
                showCategoryDialog = false
            }
        )
    }

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

/**
 * 归档分类选择对话框（含"不分类"选项）
 */
@Composable
fun ArchiveCategorySelectDialog(
    categoryList: List<Category>,
    selectedCategory: Category?,
    onDismiss: () -> Unit,
    onSelect: (Category?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择归档分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 不分类选项
                Surface(
                    onClick = { onSelect(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedCategory == null)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "不分类",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (categoryList.isEmpty()) {
                    Text(
                        text = "暂无归档分类，可在设置中创建",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    categoryList.forEach { category ->
                        Surface(
                            onClick = { onSelect(category) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedCategory?.id == category.id)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
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
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

enum class ShareStep {
    PARSING,
    FETCHING_INFO,
    CONFIRMING,
    PUSHING,
    SUCCESS,
    ERROR
}

/**
 * 系列选择对话框（含"不加入系列"选项）
 */
@Composable
fun SeriesSelectDialog(
    seriesList: List<Series>,
    selectedSeries: Series?,
    onDismiss: () -> Unit,
    onSelect: (Series?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择系列") },
        text = {
            if (seriesList.isEmpty()) {
                Text("暂无系列，请先创建", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 不加入系列选项
                    Surface(
                        onClick = { onSelect(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedSeries == null)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "不加入系列",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    seriesList.forEach { series ->
                        Surface(
                            onClick = { onSelect(series) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedSeries?.id == series.id)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
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
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 新建系列对话框
 */
@Composable
fun CreateSeriesDialog(
    nasApiService: NasApiService,
    onDismiss: () -> Unit,
    onCreated: (Series) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("新建系列") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("系列标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "请输入系列标题"
                        return@TextButton
                    }
                    scope.launch {
                        isCreating = true
                        errorMessage = null
                        nasApiService.createSeries(
                            SeriesCreateRequest(title = title.trim(), description = description.trim())
                        ).onSuccess { onCreated(it) }
                            .onFailure { e ->
                                errorMessage = e.message ?: "创建失败"
                                isCreating = false
                            }
                    }
                },
                enabled = !isCreating && title.isNotBlank()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("创建")
                }
            }
        },
        dismissButton = {
            if (!isCreating) {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}分${remainingSeconds}秒"
    } else {
        "${remainingSeconds}秒"
    }
}
