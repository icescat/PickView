package com.example.bilipick.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bilipick.data.model.Category
import com.example.bilipick.data.model.ParentalControlConfig
import com.example.bilipick.data.model.Series
import com.example.bilipick.data.model.SeriesCreateRequest
import com.example.bilipick.network.NasApiService
import com.example.bilipick.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 设置页面 - 主入口，提供子页面跳转
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nasApiService: NasApiService,
    modifier: Modifier = Modifier
) {
    var currentSubPage by remember { mutableStateOf<SettingsSubPage>(SettingsSubPage.MAIN) }

    when (currentSubPage) {
        SettingsSubPage.MAIN -> SettingsMainPage(
            nasApiService = nasApiService,
            modifier = modifier,
            onNavigate = { currentSubPage = it }
        )
        SettingsSubPage.SERVER_CONFIG -> ServerConfigPage(
            nasApiService = nasApiService,
            onBack = { currentSubPage = SettingsSubPage.MAIN },
            modifier = modifier
        )
        SettingsSubPage.CATEGORY_MANAGE -> CategoryManagePage(
            nasApiService = nasApiService,
            onBack = { currentSubPage = SettingsSubPage.MAIN },
            modifier = modifier
        )
        SettingsSubPage.SERIES_MANAGE -> SeriesManagePage(
            nasApiService = nasApiService,
            onBack = { currentSubPage = SettingsSubPage.MAIN },
            modifier = modifier
        )
        SettingsSubPage.PARENTAL_CONTROL -> ParentalControlPage(
            nasApiService = nasApiService,
            onBack = { currentSubPage = SettingsSubPage.MAIN },
            modifier = modifier
        )
        SettingsSubPage.USAGE_HELP -> UsageHelpPage(
            onBack = { currentSubPage = SettingsSubPage.MAIN },
            modifier = modifier
        )
    }
}

enum class SettingsSubPage {
    MAIN, SERVER_CONFIG, CATEGORY_MANAGE, SERIES_MANAGE, PARENTAL_CONTROL, USAGE_HELP
}

/**
 * 设置主页面 - 带分组和视觉优化的跳转入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainPage(
    nasApiService: NasApiService,
    modifier: Modifier,
    onNavigate: (SettingsSubPage) -> Unit
) {
    Scaffold(
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 服务器配置
            item {
                SettingsSectionHeader(title = "连接")
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Cloud,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTintColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = "服务器配置",
                    subtitle = "配置 NAS 服务器地址和 API Key",
                    onClick = { onNavigate(SettingsSubPage.SERVER_CONFIG) }
                )
            }

            // 内容管理
            item {
                SettingsSectionHeader(title = "内容管理")
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Category,
                    iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTintColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = "分类管理",
                    subtitle = "管理视频分类（语文、数学等）",
                    onClick = { onNavigate(SettingsSubPage.CATEGORY_MANAGE) }
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Collections,
                    iconBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTintColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = "系列管理",
                    subtitle = "管理视频系列（同一套视频）",
                    onClick = { onNavigate(SettingsSubPage.SERIES_MANAGE) }
                )
            }

            // 安全
            item {
                SettingsSectionHeader(title = "安全与控制")
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.FamilyRestroom,
                    iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTintColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = "家长控制",
                    subtitle = "设置观看时长、时段、视频类型限制",
                    onClick = { onNavigate(SettingsSubPage.PARENTAL_CONTROL) }
                )
            }

            // 其他
            item {
                SettingsSectionHeader(title = "其他")
            }
            item {
                SettingsNavItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "使用说明",
                    subtitle = "了解如何使用 BiliPick",
                    onClick = { onNavigate(SettingsSubPage.USAGE_HELP) }
                )
            }

            // 底部间距
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 设置页头部 - 应用信息
 */
@Composable
private fun SettingsHeader() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "BiliPick",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "精选视频推送助手",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 设置分组标题
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 设置项卡片 - 带彩色图标容器
 */
@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    iconBackgroundColor: androidx.compose.ui.graphics.Color,
    iconTintColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 彩色图标容器
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconBackgroundColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTintColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 通用子页面 TopBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPageTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                title,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * 服务器配置子页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerConfigPage(
    nasApiService: NasApiService,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(nasApiService.getBaseUrl()) }
    var apiKey by remember { mutableStateOf(nasApiService.getApiKey()) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    var showSaveSuccess by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { SubPageTopBar(title = "服务器配置", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 说明卡片
            InfoCard(
                icon = Icons.Default.Info,
                text = "配置飞牛 NAS 服务器地址，用于存储精选视频",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // 服务器地址
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "连接信息",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("服务器地址") },
                        placeholder = { Text("http://<nas-ip>:9530") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("your-api-key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // 测试连接按钮
            Button(
                onClick = {
                    nasApiService.saveBaseUrl(serverUrl)
                    nasApiService.saveApiKey(apiKey)
                    scope.launch {
                        isTesting = true
                        testResult = nasApiService.healthCheck()
                        isTesting = false
                    }
                },
                enabled = serverUrl.isNotEmpty() && !isTesting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试连接")
                }
            }

            // 测试结果
            AnimatedVisibility(
                visible = testResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                testResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.isSuccess)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (result.isSuccess)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = result.fold(
                                    onSuccess = { it },
                                    onFailure = { it.message ?: "连接失败" }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (result.isSuccess)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // 保存按钮
            Button(
                onClick = {
                    nasApiService.saveBaseUrl(serverUrl)
                    nasApiService.saveApiKey(apiKey)
                    showSaveSuccess = true
                },
                enabled = serverUrl.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存设置")
            }
        }
    }

    if (showSaveSuccess) {
        AlertDialog(
            onDismissRequest = { showSaveSuccess = false },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("保存成功") },
            text = { Text("NAS 服务器配置已保存") },
            confirmButton = {
                TextButton(onClick = { showSaveSuccess = false }) { Text("确定") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * 信息提示卡片
 */
@Composable
private fun InfoCard(
    icon: ImageVector,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
        }
    }
}

/**
 * 分类管理子页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManagePage(
    nasApiService: NasApiService,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isAddingCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var isCreatingCategory by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf<Category?>(null) }
    var showEditCategoryDialog by remember { mutableStateOf<Category?>(null) }
    var editCategoryName by remember { mutableStateOf("") }
    var isUpdatingCategory by remember { mutableStateOf(false) }

    fun loadCategories() {
        scope.launch {
            isLoading = true
            nasApiService.getCategories()
                .onSuccess { categories = it }
                .onFailure {
                    categories = emptyList()
                    Toast.makeText(context, "加载分类失败: ${it.message}", Toast.LENGTH_LONG).show()
                }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadCategories() }

    Scaffold(
        modifier = modifier,
        topBar = {
            SubPageTopBar(
                title = "分类管理",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        newCategoryName = ""
                        isCreatingCategory = false
                        isAddingCategory = !isAddingCategory
                    }) {
                        Icon(
                            if (isAddingCategory) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (isAddingCategory) "收起" else "新增分类"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 新增分类输入区
            AnimatedVisibility(
                visible = isAddingCategory,
                enter = fadeIn() + androidx.compose.animation.expandVertically(),
                exit = fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "新增分类",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("分类名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    isCreatingCategory = true
                                    nasApiService.createCategory(newCategoryName.trim())
                                        .onSuccess {
                                            isAddingCategory = false
                                            newCategoryName = ""
                                            loadCategories()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "创建失败: ${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                    isCreatingCategory = false
                                }
                            },
                            enabled = newCategoryName.isNotBlank() && !isCreatingCategory,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isCreatingCategory) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("添加分类")
                            }
                        }
                    }
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                categories.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.Category,
                        title = "暂无分类",
                        subtitle = "点击右上角 + 添加第一个分类"
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories, key = { it.id }) { category ->
                            CategoryItemCard(
                                name = category.name,
                                onEdit = {
                                    editCategoryName = category.name
                                    showEditCategoryDialog = category
                                },
                                onDelete = { showDeleteCategoryDialog = category }
                            )
                        }
                    }
                }
            }
        }
    }

    showDeleteCategoryDialog?.let { category ->
        AlertDialog(
            onDismissRequest = { showDeleteCategoryDialog = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("删除分类") },
            text = { Text("确定要删除分类「${category.name}」吗？该分类下的视频不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        nasApiService.deleteCategory(category.id)
                            .onSuccess { loadCategories() }
                        showDeleteCategoryDialog = null
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCategoryDialog = null }) { Text("取消") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    showEditCategoryDialog?.let { category ->
        AlertDialog(
            onDismissRequest = { showEditCategoryDialog = null },
            title = { Text("修改分类名称") },
            text = {
                OutlinedTextField(
                    value = editCategoryName,
                    onValueChange = { editCategoryName = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editCategoryName.isNotBlank() && !isUpdatingCategory,
                    onClick = {
                        scope.launch {
                            isUpdatingCategory = true
                            nasApiService.updateCategory(category.id, editCategoryName.trim())
                                .onSuccess { loadCategories() }
                                .onFailure {
                                    Toast.makeText(context, "修改失败: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            isUpdatingCategory = false
                            showEditCategoryDialog = null
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditCategoryDialog = null }) { Text("取消") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * 分类列表项卡片
 */
@Composable
private fun CategoryItemCard(
    name: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 系列管理子页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesManagePage(
    nasApiService: NasApiService,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isAddingSeries by remember { mutableStateOf(false) }
    var newSeriesName by remember { mutableStateOf("") }
    var newSeriesDesc by remember { mutableStateOf("") }
    var isCreatingSeries by remember { mutableStateOf(false) }
    var showEditSeriesDialog by remember { mutableStateOf<Series?>(null) }
    var editSeriesTitle by remember { mutableStateOf("") }
    var editSeriesDesc by remember { mutableStateOf("") }
    var isUpdatingSeries by remember { mutableStateOf(false) }
    var showDeleteSeriesDialog by remember { mutableStateOf<Series?>(null) }

    fun loadSeries() {
        scope.launch {
            isLoading = true
            nasApiService.getSeries()
                .onSuccess { seriesList = it }
                .onFailure {
                    seriesList = emptyList()
                    Toast.makeText(context, "加载系列失败: ${it.message}", Toast.LENGTH_LONG).show()
                }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadSeries() }

    Scaffold(
        modifier = modifier,
        topBar = {
            SubPageTopBar(
                title = "系列管理",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        newSeriesName = ""
                        newSeriesDesc = ""
                        isCreatingSeries = false
                        isAddingSeries = !isAddingSeries
                    }) {
                        Icon(
                            if (isAddingSeries) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (isAddingSeries) "收起" else "新增系列"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 新增系列输入区
            AnimatedVisibility(
                visible = isAddingSeries,
                enter = fadeIn() + androidx.compose.animation.expandVertically(),
                exit = fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "新增系列",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        OutlinedTextField(
                            value = newSeriesName,
                            onValueChange = { newSeriesName = it },
                            label = { Text("系列名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newSeriesDesc,
                            onValueChange = { newSeriesDesc = it },
                            label = { Text("系列描述（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    isCreatingSeries = true
                                    nasApiService.createSeries(
                                        SeriesCreateRequest(
                                            title = newSeriesName.trim(),
                                            description = newSeriesDesc.trim()
                                        )
                                    )
                                        .onSuccess {
                                            isAddingSeries = false
                                            newSeriesName = ""
                                            newSeriesDesc = ""
                                            loadSeries()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "创建失败: ${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                    isCreatingSeries = false
                                }
                            },
                            enabled = newSeriesName.isNotBlank() && !isCreatingSeries,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isCreatingSeries) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("创建系列")
                            }
                        }
                    }
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                seriesList.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.Collections,
                        title = "暂无系列",
                        subtitle = "系列用于把多个视频归为一组\n如同一套教程"
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(seriesList, key = { it.id }) { series ->
                            SeriesItemCard(
                                title = series.title,
                                description = series.description,
                                onEdit = {
                                    editSeriesTitle = series.title
                                    editSeriesDesc = series.description
                                    showEditSeriesDialog = series
                                },
                                onDelete = { showDeleteSeriesDialog = series }
                            )
                        }
                    }
                }
            }
        }
    }

    showEditSeriesDialog?.let { series ->
        AlertDialog(
            onDismissRequest = { showEditSeriesDialog = null },
            title = { Text("修改系列") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editSeriesTitle,
                        onValueChange = { editSeriesTitle = it },
                        label = { Text("系列名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSeriesDesc,
                        onValueChange = { editSeriesDesc = it },
                        label = { Text("系列描述（可选）") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editSeriesTitle.isNotBlank() && !isUpdatingSeries,
                    onClick = {
                        scope.launch {
                            isUpdatingSeries = true
                            nasApiService.updateSeries(series.id, editSeriesTitle.trim(), editSeriesDesc.trim())
                                .onSuccess { loadSeries() }
                                .onFailure {
                                    Toast.makeText(context, "修改失败: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            isUpdatingSeries = false
                            showEditSeriesDialog = null
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditSeriesDialog = null }) { Text("取消") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    showDeleteSeriesDialog?.let { series ->
        AlertDialog(
            onDismissRequest = { showDeleteSeriesDialog = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("删除系列") },
            text = { Text("确定要删除系列「${series.title}」吗？系列内的视频不会被删除，但会变为未归类。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        nasApiService.deleteSeries(series.id)
                            .onSuccess { loadSeries() }
                        showDeleteSeriesDialog = null
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesDialog = null }) { Text("取消") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
@Composable
private fun SeriesItemCard(
    title: String,
    description: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 空状态视图
 */
@Composable
private fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * 家长控制子页面 - 优化版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentalControlPage(
    nasApiService: NasApiService,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var config by remember { mutableStateOf(ParentalControlConfig()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // 双滑块状态（分钟单位，1~60）
    val maxRange = 60f
    var shortMax by remember { mutableStateOf(5f) }
    var mediumMax by remember { mutableStateOf(30f) }

    LaunchedEffect(Unit) {
        nasApiService.getParentalControl()
            .onSuccess { remote ->
                config = remote
                shortMax = remote.short_max_duration_minutes.toFloat().coerceIn(1f, maxRange - 2f)
                mediumMax = remote.medium_max_duration_minutes.toFloat().coerceIn(shortMax + 1f, maxRange)
            }
        loading = false
    }

    Scaffold(
        modifier = modifier,
        topBar = { SubPageTopBar(title = "家长控制", onBack = onBack) }
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 总开关
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (config.enabled)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (config.enabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (config.enabled)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "启用家长控制",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "开启后将限制孩子的观看行为",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { config = config.copy(enabled = it) }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = config.enabled,
                    enter = fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = fadeOut() + androidx.compose.animation.shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 时长限制
                        SettingsSectionCard(
                            title = "时长限制",
                            icon = Icons.Default.Timer
                        ) {
                            NumberInputField(
                                value = config.daily_time_limit_minutes,
                                onValueChange = {
                                    config = config.copy(daily_time_limit_minutes = it)
                                },
                                label = "每日观看时长限制（分钟）",
                                hint = "0 = 不限制"
                            )
                            NumberInputField(
                                value = config.max_single_video_duration_minutes,
                                onValueChange = {
                                    config = config.copy(max_single_video_duration_minutes = it)
                                },
                                label = "单视频最大时长（分钟）",
                                hint = "0 = 不限制"
                            )
                        }

                        // 视频 类型划分（自定义渐变色条）
                        SettingsSectionCard(
                            title = "视频类型划分",
                            icon = Icons.Default.Speed
                        ) {
                            Text(
                                "拖动分割点划分短视频、中视频、长视频的时长范围",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))

                            VideoTypeGradientBar(
                                shortMax = shortMax,
                                mediumMax = mediumMax,
                                maxRange = maxRange,
                                onShortMaxChange = { shortMax = it },
                                onMediumMaxChange = { mediumMax = it }
                            )
                        }

                        // 各类型数量限制
                        SettingsSectionCard(
                            title = "每日各类型数量限制",
                            icon = Icons.Default.Tune,
                            hint = "0 = 不限制"
                        ) {
                            NumberInputField(
                                value = config.short_video_count_limit,
                                onValueChange = {
                                    config = config.copy(short_video_count_limit = it)
                                },
                                label = "短视频每日数量限制"
                            )
                            NumberInputField(
                                value = config.medium_video_count_limit,
                                onValueChange = {
                                    config = config.copy(medium_video_count_limit = it)
                                },
                                label = "中视频每日数量限制"
                            )
                            NumberInputField(
                                value = config.long_video_count_limit,
                                onValueChange = {
                                    config = config.copy(long_video_count_limit = it)
                                },
                                label = "长视频每日数量限制"
                            )
                        }

                        // 视频类型屏蔽
                        SettingsSectionCard(
                            title = "视频类型屏蔽",
                            icon = Icons.Default.Lock,
                            hint = "开启后该类型视频完全不显示"
                        ) {
                            BlockTypeToggleRow(
                                label = "屏蔽短视频",
                                description = "TV/Pad 端不显示短视频",
                                checked = config.block_short_video,
                                onCheckedChange = {
                                    config = config.copy(block_short_video = it)
                                }
                            )
                            BlockTypeToggleRow(
                                label = "屏蔽中视频",
                                description = "TV/Pad 端不显示中视频",
                                checked = config.block_medium_video,
                                onCheckedChange = {
                                    config = config.copy(block_medium_video = it)
                                }
                            )
                            BlockTypeToggleRow(
                                label = "屏蔽长视频",
                                description = "TV/Pad 端不显示长视频",
                                checked = config.block_long_video,
                                onCheckedChange = {
                                    config = config.copy(block_long_video = it)
                                }
                            )
                        }

                        // 允许观看时段
                        SettingsSectionCard(
                            title = "允许观看时段",
                            icon = Icons.Default.Schedule,
                            hint = "设为 -1 表示不限制"
                        ) {
                            TimeRangeInput(
                                startHour = config.allowed_start_hour,
                                startMinute = config.allowed_start_minute,
                                endHour = config.allowed_end_hour,
                                endMinute = config.allowed_end_minute,
                                onStartHourChange = { config = config.copy(allowed_start_hour = it) },
                                onStartMinuteChange = { config = config.copy(allowed_start_minute = it) },
                                onEndHourChange = { config = config.copy(allowed_end_hour = it) },
                                onEndMinuteChange = { config = config.copy(allowed_end_minute = it) }
                            )
                        }

                        // 其他设置
                        SettingsSectionCard(
                            title = "其他",
                            icon = Icons.Default.Settings
                        ) {
                            NumberInputField(
                                value = config.reset_hour,
                                onValueChange = {
                                    config = config.copy(reset_hour = it.coerceIn(0, 23))
                                },
                                label = "每日重置时间（小时）",
                                hint = "0-23"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "允许当前视频播完",
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "到达限制时，允许当前视频播放完毕",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = config.allow_current_video_finish,
                                    onCheckedChange = {
                                        config = config.copy(allow_current_video_finish = it)
                                    }
                                )
                            }
                        }
                    }
                }

                // 保存按钮
                Button(
                    onClick = {
                        saving = true
                        val toSave = config.copy(
                            short_max_duration_minutes = shortMax.roundToInt(),
                            medium_max_duration_minutes = mediumMax.roundToInt()
                        )
                        scope.launch {
                            nasApiService.saveParentalControl(toSave)
                                .onSuccess {
                                    config = it
                                    message = "保存成功"
                                }
                                .onFailure { message = "保存失败: ${it.message}" }
                            saving = false
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存中...")
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存并同步到服务器")
                    }
                }

                // 保存结果
                AnimatedVisibility(
                    visible = message != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    message?.let { msg ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.startsWith("保存成功"))
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (msg.startsWith("保存成功"))
                                        Icons.Default.CheckCircle
                                    else
                                        Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (msg.startsWith("保存成功"))
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (msg.startsWith("保存成功"))
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 数字输入字段 - 带提示文字
 */
@Composable
private fun NumberInputField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    hint: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { onValueChange(it.toIntOrNull() ?: 0) },
            label = { Text(label) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        if (hint.isNotEmpty()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 类型屏蔽开关行
 */
@Composable
private fun BlockTypeToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 时段输入 - 改进的布局
 */
@Composable
private fun TimeRangeInput(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartHourChange: (Int) -> Unit,
    onStartMinuteChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    onEndMinuteChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 开始时间
        Text(
            text = "开始时间",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = startHour.toString(),
                onValueChange = { onStartHourChange(it.toIntOrNull() ?: -1) },
                label = { Text("时") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Text(
                text = ":",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = startMinute.toString(),
                onValueChange = { onStartMinuteChange(it.toIntOrNull() ?: 0) },
                label = { Text("分") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 结束时间
        Text(
            text = "结束时间",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = endHour.toString(),
                onValueChange = { onEndHourChange(it.toIntOrNull() ?: -1) },
                label = { Text("时") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Text(
                text = ":",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = endMinute.toString(),
                onValueChange = { onEndMinuteChange(it.toIntOrNull() ?: 0) },
                label = { Text("分") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

/**
 * 视频类型渐变色条 - 类似绘图软件的渐变编辑器
 * 用 Canvas 画三段色带，用 draggable 手柄拖拽
 */
@Composable
private fun VideoTypeGradientBar(
    shortMax: Float,
    mediumMax: Float,
    maxRange: Float,
    onShortMaxChange: (Float) -> Unit,
    onMediumMaxChange: (Float) -> Unit
) {
    val shortColor = androidx.compose.ui.graphics.Color(0xFFFF7043)
    val mediumColor = androidx.compose.ui.graphics.Color(0xFF2196F3)
    val longColor = androidx.compose.ui.graphics.Color(0xFF26A69A)

    // fraction: 0=1分钟, 1=60分钟；确保中段至少 1 分钟
    val minFraction = 1f / maxRange  // 1分钟对应的 fraction
    val gapFraction = 1f / maxRange  // 1分钟间隔对应的 fraction
    val shortF = (shortMax / maxRange).coerceIn(minFraction, 1f - gapFraction)
    val mediumF = (mediumMax / maxRange).coerceIn(shortF + gapFraction, 1f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 色条 + 手柄
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            val barHeight = 28.dp
            val thumbSize = 28.dp
            val density = LocalDensity.current
            val barHeightPx = with(density) { barHeight.toPx() }
            val thumbSizePx = with(density) { thumbSize.toPx() }
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val containerHeightPx = with(density) { maxHeight.toPx() }
            val barTop = (containerHeightPx - barHeightPx) / 2f

            // Canvas 画三段色带
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                val w = size.width
                val x1 = w * shortF
                val x2 = w * mediumF

                // 短视频段
                drawRoundRect(
                    color = shortColor,
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(x1, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
                // 中视频段
                drawRect(
                    color = mediumColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x1, 0f),
                    size = androidx.compose.ui.geometry.Size(x2 - x1, size.height)
                )
                // 长视频段
                drawRoundRect(
                    color = longColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x2, 0f),
                    size = androidx.compose.ui.geometry.Size(w - x2, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
            }

            // 手柄 1：短/中分割（最小 1 分钟，最大 mediumMax - 1 分钟）
            GradientDraggableThumb(
                fraction = shortF,
                totalWidthPx = totalWidthPx,
                barTopPx = barTop,
                barHeightPx = barHeightPx,
                thumbSizePx = thumbSizePx,
                thumbColor = shortColor,
                label = "${shortMax.roundToInt()}分",
                onFractionChange = { onShortMaxChange((it * maxRange).coerceIn(1f, mediumMax - 1f)) }
            )

            // 手柄 2：中/长分割（最小 shortMax + 1 分钟，最大 60 分钟）
            GradientDraggableThumb(
                fraction = mediumF,
                totalWidthPx = totalWidthPx,
                barTopPx = barTop,
                barHeightPx = barHeightPx,
                thumbSizePx = thumbSizePx,
                thumbColor = mediumColor,
                label = "${mediumMax.roundToInt()}分",
                onFractionChange = { onMediumMaxChange((it * maxRange).coerceIn(shortMax + 1f, maxRange)) }
            )
        }

        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GradientLegendItem(
                color = shortColor,
                label = "短视频",
                range = "0~${shortMax.roundToInt()}分",
                modifier = Modifier.weight(1f)
            )
            GradientLegendItem(
                color = mediumColor,
                label = "中视频",
                range = "${shortMax.roundToInt()}~${mediumMax.roundToInt()}分",
                modifier = Modifier.weight(1f)
            )
            GradientLegendItem(
                color = longColor,
                label = "长视频",
                range = ">${mediumMax.roundToInt()}分",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 可拖拽手柄 - 用 offset 定位，draggable 处理水平拖拽
 */
@Composable
private fun BoxWithConstraintsScope.GradientDraggableThumb(
    fraction: Float,
    totalWidthPx: Float,
    barTopPx: Float,
    barHeightPx: Float,
    thumbSizePx: Float,
    thumbColor: androidx.compose.ui.graphics.Color,
    label: String,
    onFractionChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .offset {
                val xPx = totalWidthPx * fraction - thumbSizePx / 2f
                val yPx = barTopPx + barHeightPx / 2f - thumbSizePx / 2f
                androidx.compose.ui.unit.IntOffset(xPx.roundToInt(), yPx.roundToInt())
            }
            .size(with(density) { thumbSizePx.toDp() })
            .draggable(
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // delta 是像素增量，直接转为 fraction 增量
                    val fractionDelta = delta / totalWidthPx
                    val newFraction = (fraction + fractionDelta).coerceIn(0f, 1f)
                    onFractionChange(newFraction)
                },
                onDragStarted = { dragging = true },
                onDragStopped = { dragging = false }
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(with(density) { thumbSizePx.toDp() }),
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.White,
            shadowElevation = if (dragging) 8.dp else 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(with(density) { (thumbSizePx * 0.6f).toDp() }),
                    shape = CircleShape,
                    color = thumbColor
                ) {}
            }
        }
    }

    // 标签
    Box(
        modifier = Modifier.offset {
            val labelWidth = 48f
            val xPx = totalWidthPx * fraction - labelWidth / 2f
            val yPx = barTopPx - 24f
            androidx.compose.ui.unit.IntOffset(xPx.roundToInt(), yPx.roundToInt())
        }
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 渐变色条图例项
 */
@Composable
private fun GradientLegendItem(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    range: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = CircleShape,
            color = color
        ) {}
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = color
            )
            Text(
                text = range,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 设置分组卡片 - 带图标和可选提示
 */
@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    hint: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (hint.isNotEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

/**
 * 使用说明子页面 - 步骤卡片版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageHelpPage(
    onBack: () -> Unit,
    modifier: Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { SubPageTopBar(title = "使用说明", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 快速上手
            Text(
                text = "快速上手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 步骤列表
            StepCard(stepNumber = 1, title = "配置服务器", description = "在「服务器配置」中填写 NAS 服务器地址和 API Key")
            StepCard(stepNumber = 2, title = "测试连接", description = "点击「测试连接」验证配置是否正确")
            StepCard(stepNumber = 3, title = "找到视频", description = "在 B 站 APP 中找到想添加的视频")
            StepCard(stepNumber = 4, title = "分享到 BiliPick", description = "点击分享按钮，选择「BiliPick」")
            StepCard(stepNumber = 5, title = "确认推送", description = "在推送确认页可选择分类和系列")
            StepCard(stepNumber = 6, title = "TV 端观看", description = "视频会自动推送到 NAS，在 TV 端即可观看")

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 功能说明
            Text(
                text = "功能说明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            FeatureCard(
                icon = Icons.Default.Category,
                title = "分类管理",
                description = "创建语文、数学、外语等学科分类"
            )
            FeatureCard(
                icon = Icons.Default.Collections,
                title = "系列管理",
                description = "把同一套视频归为系列（如某 UP 主的教程合集）"
            )
            FeatureCard(
                icon = Icons.Default.VideoLibrary,
                title = "视频列表",
                description = "查看已推送的视频，支持批量加入系列"
            )
            FeatureCard(
                icon = Icons.Default.PlayArrow,
                title = "TV 端",
                description = "分类/系列双维度浏览，观看状态自动记录"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 默认配置
            Text(
                text = "默认配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConfigItem(label = "服务器地址", value = "http://<nas-ip>:9530")
                    ConfigItem(label = "API Key", value = "<your-api-key>")
                    Text(
                        text = "TV 端已内置，无需配置",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 步骤卡片
 */
@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 步骤编号
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stepNumber.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 功能说明卡片
 */
@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 配置信息项
 */
@Composable
private fun ConfigItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
