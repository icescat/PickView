# BiliTV Code Wiki

> B站精选视频家长控制播放生态 - 完整代码文档
> 版本：v1.4.0 | 更新日期：2026-06-18

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [模块详解](#3-模块详解)
   - 3.1 [fnosserver - NAS 服务端](#31-fnosserver---nas-服务端)
   - 3.2 [bv - Android TV 客户端](#32-bv---android-tv-客户端)
   - 3.3 [BiliPick - Android 手机端](#33-bilipick---android-手机端)
4. [关键类与函数说明](#4-关键类与函数说明)
5. [依赖关系](#5-依赖关系)
6. [数据流与通信](#6-数据流与通信)
7. [项目运行方式](#7-项目运行方式)

---

## 1. 项目概述

BiliTV 是一套完整的**家长控制视频播放生态**，为儿童提供安全的 B 站视频观看体验。系统由三个协同工作的子项目组成：

| 子项目 | 技术栈 | 职责 | 目标设备 |
|--------|--------|------|----------|
| `fnosserver` | Python + FastAPI + SQLite | NAS 服务端，存储精选视频、提供 API 和 Web 管理页 | 飞牛 NAS / Docker |
| `bv` | Kotlin + Jetpack Compose + Koin | TV 播放器，基于 BV 项目改造，只播放 NAS 上的视频 | Android TV |
| `BiliPick` | Kotlin + Jetpack Compose | 家长端 APP，推送 B 站视频到 NAS | Android 手机 |

**核心工作流：**
1. 家长在手机端粘贴 B 站链接 → APP 解析视频信息 → 推送到 NAS
2. NAS 服务端存储视频元数据，提供 REST API 和 Web 管理页
3. TV 端从 NAS 拉取精选视频列表，在家长控制限制下播放

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    BiliTV 系统架构                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐         ┌─────────────────┐              │
│  │  BiliPick    │         │   fnosserver    │              │
│  │  (手机端)     │ HTTP    │   (NAS 服务端)  │              │
│  │              │ ──────> │                 │              │
│  │  - 分享接收  │  REST   │  - FastAPI     │              │
│  │  - 视频解析  │  API    │  - SQLite      │              │
│  │  - 推送管理  │         │  - Web 管理页   │              │
│  └──────────────┘         └────────┬────────┘              │
│                                    │                        │
│                                    │ REST API               │
│                                    v                        │
│                           ┌─────────────────┐              │
│                           │      bv         │              │
│                           │  (TV 客户端)    │              │
│                           │                 │              │
│                           │  - 精选视频列表  │              │
│                           │  - 家长控制      │              │
│                           │  - 视频播放      │              │
│                           │  - 观看追踪      │              │
│                           └─────────────────┘              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 模块详解

### 3.1 fnosserver - NAS 服务端

**位置：** `fnosserver/`
**技术栈：** Python 3.11 + FastAPI + SQLite + httpx
**端口：** 9530（docker-compose）/ 9525（Dockerfile）

#### 3.1.1 目录结构

```
fnosserver/
├── app/
│   ├── __init__.py          # 包标识（空文件）
│   ├── main.py              # FastAPI 入口 + 路由 + Web 管理页 HTML
│   ├── models.py            # Pydantic 数据模型
│   └── database.py          # SQLite 数据库操作
├── Dockerfile               # Docker 镜像构建配置
├── docker-compose.yml       # Docker Compose 编排
└── requirements.txt         # Python 依赖
```

#### 3.1.2 应用入口与启动流程

入口文件：[main.py](file:///e:/UserData/Documents/VS/BiliTV/fnosserver/app/main.py)

```python
app = FastAPI(
    title="B站精选NAS服务端",
    description="家长控制视频播放系统的服务端",
    version="1.4.0"
)

# 启用 CORS 中间件，允许跨域访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

启动命令（Docker）：
```bash
uvicorn main:app --host 0.0.0.0 --port 9530
```

#### 3.1.3 数据模型

文件：[models.py](file:///e:/UserData/Documents/VS/BiliTV/fnosserver/app/models.py)

| 模型类 | 用途 | 关键字段 |
|--------|------|----------|
| `VideoBase` | 视频基类 | bvid, title, up_name, duration, category, cover |
| `VideoCreate` | 创建视频请求 | 继承 VideoBase + added_by |
| `Video` | 数据库实体 | 继承 VideoBase + id, added_at, added_by |
| `VideoResponse` | API 响应 | 完整字段 |
| `VideoCategoryUpdate` | 更新分类 | category |
| `CategoryBase` | 分类基类 | name, display_order |
| `CategoryCreate` | 创建分类请求 | 继承 CategoryBase |
| `Category` | 分类实体 | 继承 CategoryBase + id |
| `CategoryUpdate` | 更新分类 | name?, display_order? |
| `CategoryResponse` | 分类响应 | 继承 CategoryBase + id |
| `BatchVideoIds` | 批量删除 | ids: List[str] |
| `BatchVideoCategoryUpdate` | 批量移动分类 | ids, category |

#### 3.1.4 数据库设计

文件：[database.py](file:///e:/UserData/Documents/VS/BiliTV/fnosserver/app/database.py)

**videos 表：**
```sql
CREATE TABLE videos (
    id TEXT PRIMARY KEY,           -- UUID 前8位
    bvid TEXT UNIQUE NOT NULL,     -- B站视频BV号
    title TEXT NOT NULL,           -- 视频标题
    up_name TEXT NOT NULL,         -- UP主名称
    duration INTEGER NOT NULL,     -- 视频时长（秒）
    category TEXT NOT NULL DEFAULT '',  -- 分类名称
    cover TEXT NOT NULL DEFAULT '',     -- 封面URL
    added_at TEXT NOT NULL,        -- 添加时间（ISO格式）
    added_by TEXT NOT NULL         -- 添加者
)
```

**categories 表：**
```sql
CREATE TABLE categories (
    id TEXT PRIMARY KEY,                  -- UUID 前8位
    name TEXT UNIQUE NOT NULL,             -- 分类名称
    display_order INTEGER NOT NULL DEFAULT 0  -- 显示排序
)
```

**默认分类（初始化时自动创建）：**
- 科技(0)、地理(1)、历史(2)、英语(3)、生物(4)、纪录片(5)、其他(6)

#### 3.1.5 API 路由总览

| 方法 | 路径 | 函数 | 功能 |
|------|------|------|------|
| GET | `/` | `read_root` | 服务状态检查 |
| GET | `/api/resolve?url=` | `resolve_bilibili_url` | 解析 B 站链接获取视频信息 |
| POST | `/api/videos` | `create_video` | 添加视频（重复 BV 自动覆盖） |
| GET | `/api/videos?category=` | `get_videos` | 获取视频列表（可按分类过滤） |
| GET | `/api/videos/{video_id}` | `get_video` | 获取单个视频 |
| PUT | `/api/videos/{video_id}/category` | `update_video_category` | 修改视频分类 |
| DELETE | `/api/videos/{video_id}` | `delete_video` | 删除视频 |
| DELETE | `/api/videos/bvid/{bvid}` | `delete_video_by_bvid` | 按 BV 号删除 |
| POST | `/api/videos/batch-delete` | `batch_delete_videos` | 批量删除 |
| POST | `/api/videos/batch-move` | `batch_move_videos` | 批量移动分类 |
| GET | `/api/categories` | `get_categories` | 获取分类列表 |
| POST | `/api/categories` | `create_category` | 创建分类 |
| PUT | `/api/categories/{cat_id}` | `update_category` | 修改分类 |
| DELETE | `/api/categories/{cat_id}` | `delete_category` | 删除分类 |
| GET | `/api/health` | `health_check` | 健康检查 |
| GET | `/admin` | `admin_page` | Web 管理页面 |

#### 3.1.6 B 站链接解析逻辑

文件：[main.py](file:///e:/UserData/Documents/VS/BiliTV/fnosserver/app/main.py#L56-L98)

```python
def _extract_bvid(text: str) -> Optional[str]:
    """从URL或纯文本中提取BV号"""
    text = text.strip()
    # 直接是BV号
    m = re.match(r'^(BV[a-zA-Z0-9]+)$', text)
    if m: return m.group(1)
    # 从URL中提取
    m = re.search(r'BV([a-zA-Z0-9]+)', text)
    if m: return 'BV' + m.group(1)
    return None
```

解析流程：
1. 从输入文本提取 BV 号
2. 调用 B 站 API：`https://api.bilibili.com/x/web-interface/view?bvid={bvid}`
3. 返回 bvid、title、up_name、duration、cover

#### 3.1.7 Web 管理页面

访问地址：`http://<NAS_IP>:9530/admin`

功能模块（内嵌 HTML 单页应用）：
- **视频管理**：列表查看、搜索、按分类筛选、批量删除/移动分类
- **添加视频**：单个链接添加、批量链接添加（每行一个）
- **分类管理**：增删改分类、自定义排序

---

### 3.2 bv - Android TV 客户端

**位置：** `bv/`
**技术栈：** Kotlin + Jetpack Compose + Koin + Room + Ktor + Media3/VLC
**基于：** BV 项目（包名 `dev.aaa1115910.bv`）

#### 3.2.1 项目模块结构

文件：[settings.gradle.kts](file:///e:/UserData/Documents/VS/BiliTV/bv/settings.gradle.kts)

```
bv/
├── app/                    # 主应用模块
├── bili-api/              # B站 HTTP API 封装
├── bili-api-grpc/          # B站 gRPC protobuf 定义
├── bili-subtitle/          # 字幕处理模块
├── bv-player/             # 播放器模块
├── libs/
│   ├── av1Decoder/         # AV1 解码器
│   ├── ffmpegDecoder/      # FFmpeg 解码器
│   ├── libVLC/             # VLC 播放库
│   └── media3Container/    # Media3 容器
└── buildSrc/               # 构建配置（AppConfiguration）
```

#### 3.2.2 应用配置

文件：[AppConfiguration.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/buildSrc/src/main/kotlin/AppConfiguration.kt)

| 配置项 | 值 |
|--------|-----|
| appId | `dev.aaa1115910.bv` |
| applicationId | `dev.frost819.bv`（打包用，规避小米电视屏蔽） |
| compileSdk | 36 |
| minSdk | 21 |
| targetSdk | 36 |
| 版本 | 0.3.16 |

构建变体（Build Types）：
- `debug`：调试版，applicationId 后缀 `.debug`
- `release`：正式版，启用 R8 混淆
- `r8Test`：R8 测试版
- `alpha`：Alpha 版本

产品风味（Product Flavors）：
- `lite`：精简版（排除 VLC 库）
- `default`：完整版

#### 3.2.3 应用入口与启动流程

文件：[BVApp.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/BVApp.kt)

```kotlin
class BVApp : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        instance = this
        context = this.applicationContext

        initCoreLibraries()  // 初始化日志、DataStore、Koin DI
        Prefs.init()         // 初始化偏好设置（阻塞读取）
        initDeviceInfo()     // 初始化设备信息
        initRepository()     // 初始化 B站仓库（频道、认证）
        initProxy()         // 初始化代理

        BiliHttpApi.init(buvid3 = Prefs.buvid3)
    }
}
```

**启动顺序：**
1. 初始化核心库：日志捕获、DataStoreManager、Koin 依赖注入容器
2. `Prefs.init()`：阻塞读取 DataStore 到内存缓存，启动长连接监听变化
3. 初始化设备信息：OS 版本、设备型号、WebView 版本
4. 初始化仓库：频道仓库、认证仓库（sessData、biliJct、accessToken 等）
5. 初始化代理（可选）

**依赖注入模块：**
```kotlin
@Module(includes = [BiliApiModule::class])
@ComponentScan
class AppModule

val curatedVideoModule = org.koin.dsl.module {
    viewModel { CuratedVideoViewModel(nasApi = get()) }
}
```

#### 3.2.4 主 Activity 启动流程

文件：[MainActivity.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/activities/MainActivity.kt)

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()  // 启动屏
        setContent {
            BVTheme {
                // 检查用户锁定状态
                if (!userLockLocked) {
                    MainScreen()           // 主界面
                } else {
                    UnlockUserScreen(...)  // 解锁界面
                }
            }
        }
    }
}
```

#### 3.2.5 NAS 服务器 API 客户端

文件：[NasServerApi.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/network/NasServerApi.kt)

使用 Ktor HttpClient + OkHttp 引擎，通过 `X-API-Key` 头认证。

| 方法 | 功能 | 对应服务端 API |
|------|------|----------------|
| `getVideos(category)` | 获取视频列表 | `GET /api/videos` |
| `getCategories()` | 获取分类列表 | `GET /api/categories` |
| `searchVideos(keyword)` | 搜索视频 | `GET /api/videos/search` |
| `checkHealth()` | 健康检查 | `GET /api/health` |

**数据类：**
```kotlin
@Serializable
data class NasVideoItem(
    val id: String, val bvid: String, val title: String,
    val up_name: String, val duration: Int,
    val category: String = "", val cover: String = "",
    val added_at: String = "", val added_by: String = ""
)

@Serializable
data class NasCategory(
    val id: String, val name: String, val display_order: Int = 0
)
```

#### 3.2.6 精选视频 ViewModel

文件：[CuratedVideoViewModel.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/curated/CuratedVideoViewModel.kt)

**核心职责：**
- 从 NAS 加载视频列表和分类
- 实现家长控制逻辑（时间限制、数量限制、时长过滤）
- 跟踪每日观看统计

**关键状态：**
```kotlin
var videos: List<NasVideoItem>          // 全部视频
var filteredVideos: List<NasVideoItem>  // 过滤后的视频（受家长控制）
var categories: List<NasCategory>        // 分类列表
var selectedCategory: String?           // 当前选中分类
var isLoading: Boolean                   // 加载状态
var errorMessage: String?               // 错误信息
var remainingSeconds: Int               // 剩余观看秒数
var remainingCount: Int                  // 剩余观看数量
var isOutsideAllowedTime: Boolean       // 是否在允许时段外
var isLimitReached: Boolean              // 是否达到限制
```

**家长控制逻辑（`refreshParentalControl()`）：**
1. 加载家长控制配置
2. 检查是否在允许时段内
3. 计算剩余时间和数量
4. 过滤视频：时长不超过剩余时间、不超过单视频最大时长

#### 3.2.7 观看时长追踪

文件：[WatchTimeTracker.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/player/WatchTimeTracker.kt)

```kotlin
class WatchTimeTracker(
    private val player: AbstractVideoPlayer,
    private val onWatchTimeUpdate: (watchedSeconds: Int) -> Unit,
    private val onVideoCompleted: () -> Unit
)
```

**工作机制：**
- 每 10 秒（`updateIntervalMs = 10_000L`）检查一次播放状态
- 仅在 `player.isPlaying` 时累计观看时长
- 检查视频完成度：当播放进度 >= 阈值（默认 80%）时触发 `onVideoCompleted`
- 阈值可通过 `Prefs.watchCompletionThreshold` 配置

#### 3.2.8 本地数据库（Room）

文件：[AppDatabase.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/dao/AppDatabase.kt)

```kotlin
@Database(
    entities = [SearchHistoryDB::class, UserDB::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun userDao(): UserDao
}
```

**表结构：**
- `SearchHistoryDB`：搜索历史
- `UserDB`：用户信息

**TypeConverters：** `Date <-> Long`（时间戳转换）

#### 3.2.9 偏好设置管理

文件：[Prefs.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/util/Prefs.kt)

使用 DataStore + 委托属性模式，实现：
- **内存缓存**：通过 `MutableStateFlow` 维护，Get 同步读取
- **异步持久化**：Set 时立即更新内存，异步写入 DataStore

**配置分类：**

| 分类 | 关键配置 |
|------|----------|
| 账号 & 认证 | isLogin, uid, sessData, biliJct, accessToken, buvid |
| 网络 & API | apiType, enableProxy, proxyHttpServer, proxyGRPCServer |
| 播放器-视频 | defaultQuality, playerType, defaultVideoCodec |
| 播放器-音频 | defaultAudio, enableFfmpegAudioRenderer |
| 播放器-弹幕 | defaultDanmakuTypes, scale, opacity, speed, area, mask |
| 播放器-字幕 | fontSize, backgroundOpacity, bottomPadding |
| 播放器-界面 | defaultPlaySpeed, showFps, showVideoInfo |
| 应用界面 | density, homeLeftNaviItem, firstHomeTopNavItem |
| NAS 服务器 | nasServerUrl, nasApiKey |
| **家长控制** | parentalControlEnabled, parentalPinCode, dailyTimeLimitMinutes, dailyVideoCountLimit, maxSingleVideoDurationMinutes, allowedStartHour/Minute, allowedEndHour/Minute, resetHour, watchCompletionThreshold |

#### 3.2.10 嵌入式 HTTP 服务器

文件：[HttpServer.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/network/HttpServer.kt)

使用 Ktor CIO 引擎，提供：
- `/`：日志管理首页
- `/logs_ui/{path}`：静态资源服务
- `/api/logs/{filename}`：日志文件下载（白名单校验）

#### 3.2.11 播放器集成

支持多种播放器后端：

| 播放器 | 依赖库 | 说明 |
|--------|--------|------|
| Media3 (ExoPlayer) | androidx.media3 | 默认播放器 |
| VLC | libs/libVLC | 完整版包含 |
| FFmpeg | libs/ffmpegDecoder | 音频解码 |
| AV1 | libs/av1Decoder | AV1 解码 |

---

### 3.3 BiliPick - Android 手机端

**位置：** `BiliPick/`
**技术栈：** Kotlin + Jetpack Compose + Ktor + Material3
**包名：** `com.example.bilipick`

#### 3.3.1 目录结构

```
BiliPick/app/src/main/java/com/example/bilipick/
├── MainActivity.kt              # 主 Activity + 导航
├── data/model/
│   └── Video.kt                 # 数据模型
├── network/
│   ├── KtorClient.kt            # Ktor 客户端工厂
│   ├── NasApiService.kt         # NAS 服务端 API
│   └── BiliApiService.kt        # B站 API 服务
└── ui/
    ├── screens/
    │   ├── VideoListScreen.kt   # 视频列表页
    │   ├── SettingsScreen.kt     # 设置页
    │   └── ShareHandlerScreen.kt # 分享处理页
    └── theme/                   # 主题（Color/Theme/Type）
```

#### 3.3.2 主 Activity 与导航

文件：[MainActivity.kt](file:///e:/UserData/Documents/VS/BiliTV/BiliPick/app/src/main/java/com/example/bilipick/MainActivity.kt)

**功能：**
- 处理分享 Intent（`ACTION_SEND` / `ACTION_VIEW`）
- 底部导航栏：视频列表、推送记录、设置
- 分享链接处理弹窗

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        nasApiService = NasApiService(this)
        handleShareIntent(intent)  // 处理分享链接
        setContent {
            BiliPickTheme {
                BiliPickApp(nasApiService, biliApiService)
            }
        }
    }
}
```

#### 3.3.3 NAS API 服务

文件：[NasApiService.kt](file:///e:/UserData/Documents/VS/BiliTV/BiliPick/app/src/main/java/com/example/bilipick/network/NasApiService.kt)

使用 Ktor HttpClient，配置存储在 SharedPreferences。

| 方法 | 功能 | 对应服务端 API |
|------|------|----------------|
| `getBaseUrl()` | 获取 NAS 地址 | - |
| `saveBaseUrl(url)` | 保存 NAS 地址 | - |
| `isConfigured()` | 检查是否已配置 | - |
| `addVideo(request)` | 添加视频 | `POST /api/videos` |
| `getVideos()` | 获取视频列表 | `GET /api/videos` |
| `deleteVideo(videoId)` | 删除视频 | `DELETE /api/videos/{id}` |
| `healthCheck()` | 健康检查 | `GET /api/health` |
| `getCategories()` | 获取分类 | `GET /api/categories` |
| `createCategory(name, order)` | 创建分类 | `POST /api/categories` |
| `deleteCategory(categoryId)` | 删除分类 | `DELETE /api/categories/{id}` |
| `updateVideoCategory(videoId, category)` | 更新视频分类 | `PUT /api/videos/{id}/category` |

#### 3.3.4 B 站 API 服务

文件：[BiliApiService.kt](file:///e:/UserData/Documents/VS/BiliTV/BiliPick/app/src/main/java/com/example/bilipick/network/BiliApiService.kt)

| 方法 | 功能 |
|------|------|
| `getBvidFromShortUrl(shortUrl)` | 从短链接获取 BV 号（跟随重定向） |
| `getVideoInfo(bvid)` | 调用 B 站 API 获取视频详情 |

**B 站 API 端点：** `https://api.bilibili.com/x/web-interface/view?bvid={bvid}`

#### 3.3.5 数据模型

文件：[Video.kt](file:///e:/UserData/Documents/VS/BiliTV/BiliPick/app/src/main/java/com/example/bilipick/data/model/Video.kt)

```kotlin
@Serializable
data class Video(
    val id: String, val bvid: String, val title: String,
    @SerialName("up_name") val upName: String,
    val duration: Int, val category: String,
    val cover: String = "",
    @SerialName("added_at") val addedAt: String,
    @SerialName("added_by") val addedBy: String
)

@Serializable
data class BiliVideoInfo(
    val bvid: String, val title: String,
    val owner: Owner, val duration: Int,
    val tname: String? = null, val pic: String? = null
) {
    data class Owner(val name: String)
}
```

---

## 4. 关键类与函数说明

### 4.1 fnosserver 关键类

| 类/函数 | 文件位置 | 职责 |
|---------|----------|------|
| `app` | main.py:14 | FastAPI 应用实例 |
| `Database` | database.py:22 | 数据库操作类（单例 `db`） |
| `db.create_video()` | database.py:82 | 创建视频 |
| `db.get_video_by_bvid()` | database.py:114 | 按 BV 号查询 |
| `db.batch_delete_videos()` | database.py:153 | 批量删除 |
| `_extract_bvid()` | main.py:56 | BV 号提取 |
| `resolve_bilibili_url()` | main.py:70 | B 站链接解析 |
| `_video_to_response()` | main.py:38 | Video 转 VideoResponse |

### 4.2 bv 关键类

| 类/函数 | 文件位置 | 职责 |
|---------|----------|------|
| `BVApp` | BVApp.kt:36 | Application 入口 |
| `AppModule` | BVApp.kt:131 | Koin 依赖注入模块 |
| `MainActivity` | MainActivity.kt:20 | 主 Activity |
| `NasServerApi` | NasServerApi.kt:42 | NAS API 客户端（@Single） |
| `CuratedVideoViewModel` | CuratedVideoViewModel.kt:22 | 精选视频 ViewModel |
| `WatchTimeTracker` | WatchTimeTracker.kt:7 | 观看时长追踪 |
| `AppDatabase` | AppDatabase.kt:27 | Room 数据库 |
| `Prefs` | Prefs.kt:43 | 偏好设置管理（委托模式） |
| `PrefDelegate` | Prefs.kt:314 | 偏好委托类 |
| `HttpServer` | HttpServer.kt:23 | 嵌入式 HTTP 服务器 |

### 4.3 BiliPick 关键类

| 类/函数 | 文件位置 | 职责 |
|---------|----------|------|
| `MainActivity` | MainActivity.kt:32 | 主 Activity + 导航 |
| `BiliPickApp` | MainActivity.kt:93 | 主界面 Composable |
| `NasApiService` | NasApiService.kt:18 | NAS API 服务 |
| `BiliApiService` | BiliApiService.kt:17 | B 站 API 服务 |
| `ShareDataHolder` | MainActivity.kt:88 | 全局分享数据持有者 |

---

## 5. 依赖关系

### 5.1 fnosserver 依赖

文件：[requirements.txt](file:///e:/UserData/Documents/VS/BiliTV/fnosserver/requirements.txt)

| 依赖 | 版本 | 用途 |
|------|------|------|
| fastapi | 0.104.1 | Web 框架 |
| uvicorn[standard] | 0.24.0 | ASGI 服务器 |
| pydantic | 2.5.0 | 数据验证 |
| httpx | 0.27.0 | HTTP 客户端（调用 B 站 API） |

### 5.2 bv 主要依赖

文件：[app/build.gradle.kts](file:///e:/UserData/Documents/VS/BiliTV/bv/app/build.gradle.kts)

| 依赖 | 用途 |
|------|------|
| androidx.compose.* | Jetpack Compose UI 框架 |
| androidx.compose.tv.* | TV 专用 Compose 组件 |
| androidx.media3.* | Media3/ExoPlayer 播放器 |
| androidx.room.* | Room 数据库 |
| androidx.datastore.* | DataStore 偏好存储 |
| koin.* | 依赖注入框架 |
| ktor.* | HTTP 客户端 + 嵌入式服务器 |
| coil.* | 图片加载 |
| kotlinx.serialization | JSON 序列化 |
| akdanmaku | 弹幕引擎 |
| lottie | 动画 |
| slf4j-android | 日志门面 |

**内部模块依赖：**
- `:bili-api` - B 站 HTTP API
- `:bili-subtitle` - 字幕处理
- `:bv-player` - 播放器模块
- `:libs:av1Decoder` - AV1 解码
- `:libs:ffmpegDecoder` - FFmpeg 解码
- `:libs:libVLC` - VLC 库

### 5.3 BiliPick 主要依赖

| 依赖 | 用途 |
|------|------|
| androidx.compose.* | Jetpack Compose UI |
| androidx.material3 | Material Design 3 |
| ktor.* | HTTP 客户端 |
| kotlinx.serialization | JSON 序列化 |

### 5.4 模块间通信依赖

```
BiliPick ──HTTP──> fnosserver <──HTTP── bv
    │                   │
    │                   ├── httpx ──> B站 API
    │                   └── SQLite (本地存储)
    │
    ├── Ktor ──> B站 API (直接解析)
    └── Ktor ──> fnosserver (REST API)
```

---

## 6. 数据流与通信

### 6.1 视频推送流程

```
用户在手机端操作：
1. 分享 B 站链接到 BiliPick APP
   └─> ShareHandlerScreen 接收链接
   
2. BiliApiService.getVideoInfo(bvid)
   └─> 调用 https://api.bilibili.com/x/web-interface/view
   └─> 返回：title, up_name, duration, cover, tname

3. NasApiService.addVideo(VideoCreateRequest)
   └─> POST {NAS_URL}/api/videos
   └─> 服务端存储到 SQLite
```

### 6.2 视频播放流程

```
TV 端启动：
1. BVApp.onCreate() 初始化
   └─> Prefs.init() 读取配置
   └─> Koin 注入依赖

2. MainActivity 启动
   └─> 检查用户锁定
   └─> MainScreen()

3. CuratedVideoViewModel 加载
   └─> NasServerApi.getCategories()
   └─> NasServerApi.getVideos(category)
   └─> refreshParentalControl() 家长控制过滤

4. 用户选择视频
   └─> VideoInfoScreen 显示详情
   └─> 播放器播放

5. WatchTimeTracker 追踪
   └─> 每10秒累计观看时长
   └─> 达到80%进度触发 onVideoCompleted
   └─> 更新 DailyWatchStats
```

### 6.3 家长控制数据流

```
配置（Prefs）:
  parentalControlEnabled: Boolean
  dailyTimeLimitMinutes: Int
  dailyVideoCountLimit: Int
  maxSingleVideoDurationMinutes: Int
  allowedStartHour/Minute: Int
  allowedEndHour/Minute: Int
  watchCompletionThreshold: Int (默认80)

运行时:
  DailyWatchStats {
    date: String (YYYY-MM-DD)
    watchedTimeSeconds: Int
    watchedVideoCount: Int
  }

过滤逻辑:
  1. 检查时段 -> isOutsideAllowedTime
  2. 计算剩余 -> remainingSeconds, remainingCount
  3. 过滤视频 -> duration <= remainingSeconds
```

---

## 7. 项目运行方式

### 7.1 fnosserver 服务端部署

#### 方式一：Docker Compose（推荐）

```bash
# 1. 将 fnosserver/ 目录上传到 NAS
# 2. 进入目录
cd /docker/bilipick-server

# 3. 启动服务
docker-compose up -d

# 4. 访问管理页
# http://<NAS_IP>:9530/admin
```

**docker-compose.yml 配置：**
- 镜像：`python:3.11-slim`
- 端口：`9530:9530`
- 数据卷：`./app:/app`、`./data:/app/data`
- 时区：`Asia/Shanghai`

#### 方式二：Dockerfile 构建

```bash
docker build -t bilipick-server .
docker run -d -p 9525:9525 -v $(pwd)/data:/app/data bilipick-server
```

#### 方式三：本地运行

```bash
cd fnosserver
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 9530
```

**数据存储：** `./data/videos.db`（SQLite）

### 7.2 bv TV 客户端构建

#### 环境要求
- Android Studio
- JDK 17
- Android SDK 36
- Gradle 9.3.1

#### 构建命令

```bash
cd bv

# 调试版（完整）
./gradlew assembleDefaultDebug

# 调试版（精简，无 VLC）
./gradlew assembleLiteDebug

# 正式版
./gradlew assembleDefaultRelease

# Alpha 版
./gradlew assembleDefaultAlpha
```

**APK 输出：** `app/build/outputs/apk/`
命名格式：`BV_{versionCode}_{versionName}.{buildType}_{flavor}_{abi}.apk`

#### 配置 TV 端

在 TV 端设置中填入 NAS 服务器地址：`http://<NAS_IP>:9530`

### 7.3 BiliPick 手机端构建

#### 环境要求
- Android Studio
- JDK 17
- Android SDK

#### 构建命令

```bash
cd BiliPick
./gradlew assembleDebug
```

**APK 输出：** `app/build/outputs/apk/debug/app-debug.apk`

#### 配置手机端

首次打开 APP，在设置页填入 NAS 服务器地址：`http://<NAS_IP>:9530`

### 7.4 完整生态部署步骤

1. **部署 NAS 服务端**
   ```bash
   cd fnosserver
   docker-compose up -d
   ```

2. **安装手机端 APP**
   - 构建 BiliPick APK
   - 安装到家长手机
   - 配置 NAS 服务器地址

3. **安装 TV 端 APP**
   - 构建 bv APK（完整版）
   - 安装到 Android TV
   - 配置 NAS 服务器地址
   - 配置家长控制参数

4. **开始使用**
   - 家长在手机端分享 B 站视频链接到 BiliPick
   - 或访问 `http://<NAS_IP>:9530/admin` 管理视频
   - 儿童在 TV 端观看精选视频

---

## 附录

### A. 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| fnosserver (docker-compose) | 9530 | NAS 服务端 |
| fnosserver (Dockerfile) | 9525 | NAS 服务端（构建镜像用） |
| bv HttpServer | 随机端口 | TV 端嵌入式日志服务器 |

### B. 关键文件索引

| 文件 | 说明 |
|------|------|
| [readme.md](file:///e:/UserData/Documents/VS/BiliTV/readme.md) | 项目根 README |
| [PROJECT_RULES.md](file:///e:/UserData/Documents/VS/BiliTV/PROJECT_RULES.md) | 项目规则文档 |
| [CLAUDE.md](file:///e:/UserData/Documents/VS/BiliTV/CLAUDE.md) | 编码规范 |
| [fnosserver/app/main.py](file:///e:/UserData/Documents/VS/BiliTV/fnosserver/app/main.py) | 服务端入口 |
| [bv/app/src/main/kotlin/dev/aaa1115910/bv/BVApp.kt](file:///e:/UserData/Documents/VS/BiliTV/bv/app/src/main/kotlin/dev/aaa1115910/bv/BVApp.kt) | TV 端入口 |
| [BiliPick/app/src/main/java/com/example/bilipick/MainActivity.kt](file:///e:/UserData/Documents/VS/BiliTV/BiliPick/app/src/main/java/com/example/bilipick/MainActivity.kt) | 手机端入口 |

### C. Docs 目录文档

| 文档 | 说明 |
|------|------|
| [飞牛NAS部署指南.md](file:///e:/UserData/Documents/VS/BiliTV/Docs/飞牛NAS部署指南.md) | NAS 部署详细指南 |
| [故障排查指南.md](file:///e:/UserData/Documents/VS/BiliTV/Docs/故障排查指南.md) | 常见问题排查 |
| [Docker权限问题.md](file:///e:/UserData/Documents/VS/BiliTV/Docs/Docker权限问题.md) | Docker 权限问题 |
| [容器启动失败排查.md](file:///e:/UserData/Documents/VS/BiliTV/Docs/容器启动失败排查.md) | 容器启动问题 |
| [修改端口为9530.md](file:///e:/UserData/Documents/VS/BiliTV/Docs/修改端口为9530.md) | 端口配置说明 |
