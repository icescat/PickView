# BV 项目调查报告

> 调查日期：2026-05-26
> 调查目标：为"精选列表播放 + 家长控制"改造提供项目现状分析

---

## 一、项目概况

| 项目 | 说明 |
|------|------|
| 项目名 | BV (Bilibili TV) |
| 类型 | Android TV 第三方B站播放器 |
| 包名 | `dev.aaa1115910.bv` |
| 最低SDK | API 21+ (TV Leanback) |
| 语言 | Kotlin 100% |
| UI框架 | Jetpack Compose + Compose for TV (Material3) |
| 架构 | MVVM (ViewModel + Repository + Koin DI) |
| 依赖注入 | Koin (注解扫描 + KSP) |
| 网络库 | Ktor (HTTP) + gRPC (Protobuf) |
| 播放器 | ExoPlayer/Media3 + VLC + FFmpeg |
| 持久化 | DataStore (Preferences) + Room |

## 二、模块结构

项目采用多模块架构，共 7 个模块：

| 模块 | 职责 |
|------|------|
| `:app` | 主应用，UI/Activity/Screen/ViewModel |
| `:bili-api` | B站 HTTP REST API 封装（Ktor） |
| `:bili-api-grpc` | B站 gRPC/Protobuf 接口定义 |
| `:bili-subtitle` | 字幕解析库 |
| `:bv-player` | 视频播放器封装（ExoPlayer/Media3/VLC） |
| `:libs:av1Decoder` | AV1 硬解库 |
| `:libs:ffmpegDecoder` | FFmpeg 软解库 |
| `:libs:libVLC` | VLC 播放器库 |

## 三、核心架构分析

### 3.1 导航结构

入口：`MainActivity` → `MainScreen` → `NavigationDrawer`（左侧导航栏 + 右侧内容区）

当前5个导航项（`LeftNaviContent.kt`）：

```
Search → Personal → Home → UGC → PGC
搜索      个人       主页    分区    影视
```

**改造要点**：需将5项精简为3项（精选视频 + 搜索 + 设置），删除 Personal/UGC/PGC，替换 Home 为精选列表。

### 3.2 首页内容

`HomeContent.kt` 包含3个Tab：
- **推荐** (`RecommendScreen`) — B站推荐算法
- **热门** (`PopularScreen`) — B站热门排行
- **动态** (`DynamicsScreen`) — 关注UP主动态

**改造要点**：整个 HomeContent 需替换为从 NAS 获取的精选视频列表。

### 3.3 视频播放流程

```
视频卡片点击 → VideoInfoActivity → VideoInfoScreen → playCurrentVideo()
  → launchPlayerActivity() → VideoPlayerV3Activity → VideoPlayerV3Screen
  → VideoPlayerV3ViewModel (加载视频流 + 弹幕 + 字幕)
```

关键文件：
- `VideoPlayerV3Activity.kt` — 播放器入口，通过 Intent 传递 avid/cid/title
- `VideoPlayerV3ViewModel.kt` — 播放器核心逻辑，管理播放状态/弹幕/字幕
- `VideoInfoScreen.kt` — 视频详情页，含点赞/投币/收藏/关注按钮

**改造要点**：
- 播放器核心可保留（视频流获取、播放控制、进度记忆）
- 需删除：弹幕、点赞/投币/收藏按钮、关注功能
- 需新增：观看时长追踪（WatchTimeTracker），拦截非精选视频播放

### 3.4 视频信息页（VideoInfoScreen）

当前包含：
- 封面 + 标题 + UP主 + 播放量统计
- 点赞/投币/收藏按钮（需删除）
- 关注UP主按钮（需删除）
- 视频分P选择
- 合集/相关视频推荐
- 标签

**改造要点**：精简为仅展示视频信息 + 播放按钮，删除互动功能和相关推荐。

### 3.5 搜索功能

当前：全站搜索（`SearchInputScreen` → `SearchResultScreen`）

**改造要点**：改为仅在精选视频列表内搜索，或直接调用 NAS 搜索接口。

### 3.6 设置页面

`SettingsScreen.kt` 当前分组：
- AudioVideo / PlayerType / UI / Network / Storage / Info / Other / About

**改造要点**：需新增"家长控制"设置分组（NAS地址、限制规则、PIN码验证）。

### 3.7 数据持久化

- **DataStore (Preferences)**：`Prefs.kt` — 全局配置，使用委托属性模式，内存缓存 + 异步持久化
- **Room Database**：`AppDatabase` — 搜索历史、用户信息
- **Koin DI**：依赖注入，`AppModule` 扫描注册

**改造要点**：Prefs 中需新增 NAS 服务器地址、家长控制开关等配置项。

### 3.8 用户认证

- B站登录：QR码登录 + 短信登录
- 用户切换：多账号支持
- 用户锁：PIN码锁定（已有 `UserLockSettingsActivity`）

**改造要点**：保留B站登录（播放需要），用户锁机制可复用为家长控制PIN码。

## 四、已有前期工作

### 4.1 已创建的实体类

`ParentalControlConfig.kt` — 已完整实现：
- `ParentalControlConfig`：家长控制配置（时长/数量/时段/白名单/黑名单）
- `DailyWatchStats`：每日观看统计（已观看时长/数量/剩余计算）
- 包含 `isInAllowedTimeRange()`、`hasAnyLimit()`、`getRemainingTimeSeconds()` 等工具方法

### 4.2 NAS 服务端（fnosserver）

已实现基础 CRUD API：

| 接口 | 方法 | 状态 |
|------|------|------|
| `/api/videos` | POST | ✅ 添加视频 |
| `/api/videos` | GET | ✅ 获取视频列表 |
| `/api/videos/{id}` | GET | ✅ 获取单个视频 |
| `/api/videos/{id}` | DELETE | ✅ 删除视频 |
| `/api/videos/bvid/{bvid}` | DELETE | ✅ 按BV号删除 |
| `/api/health` | GET | ✅ 健康检查 |

尚未实现：
- ❌ 观看统计接口（`/api/stats/watch`、`/api/stats/remaining`）
- ❌ 每日限制/重置逻辑
- ❌ API 认证（API Key）
- ❌ 搜索接口

### 4.3 设计文档

- `PLAN.md` — 三阶段实施计划（NAS服务端 → 家长管理APP → TV端改造）
- `PARENTAL_CONTROL_DESIGN.md` — 详细设计方案

## 五、改造关键路径分析

### 功能1：只能播放精选列表视频

| 改造点 | 当前状态 | 改造方案 | 复杂度 |
|--------|----------|----------|--------|
| 导航栏 | 5项（搜索/个人/主页/分区/影视） | 精简为3项（精选/搜索/设置） | ⭐⭐ |
| 首页内容 | B站推荐/热门/动态 | 替换为NAS精选视频列表 | ⭐⭐⭐ |
| 搜索 | 全站搜索 | 改为精选内搜索 | ⭐⭐ |
| 视频详情页 | 含互动按钮+相关推荐 | 精简，删除互动和推荐 | ⭐⭐ |
| 播放拦截 | 无限制 | 检查视频是否在精选列表中 | ⭐⭐ |
| NAS API客户端 | 不存在 | 新建 `NasServerApi.kt` | ⭐⭐ |

### 功能2：家长控制

| 改造点 | 当前状态 | 改造方案 | 复杂度 |
|--------|----------|----------|--------|
| 配置实体 | ✅ 已有 `ParentalControlConfig` | 接入 Prefs 持久化 | ⭐ |
| 观看统计 | ✅ 已有 `DailyWatchStats` | 实现追踪逻辑 | ⭐⭐ |
| 时长追踪 | 不存在 | 新建 `WatchTimeTracker`，监听播放器状态 | ⭐⭐⭐ |
| 限制执行 | 不存在 | 在播放入口/列表展示层拦截 | ⭐⭐ |
| 设置页面 | 无家长控制项 | 新增设置分组 + PIN码验证 | ⭐⭐ |
| NAS统计接口 | ❌ 未实现 | 服务端新增统计/限制API | ⭐⭐ |

## 六、需删除的模块/文件

| 模块/文件 | 说明 |
|-----------|------|
| `screen/main/home/` | 推荐/热门/动态（3个Screen + 3个ViewModel） |
| `screen/main/ugc/` | UGC分区 |
| `screen/main/pgc/` | PGC影视 |
| `screen/main/PersonalContent.kt` | 个人页 |
| `screen/user/` | 收藏/历史/稍后再看/追番等 |
| `component/DanmakuPlayerCompose.kt` | 弹幕组件 |
| `component/controllers/DanmakuMenu.kt` | 弹幕菜单 |
| `component/buttons/` | 点赞/投币/收藏按钮 |
| `viewmodel/home/` | 3个首页ViewModel |
| `viewmodel/pgc/` | PGC相关ViewModel |
| `viewmodel/ugc/` | UGC相关ViewModel |
| `viewmodel/user/` | 用户相关ViewModel（部分） |
| `activities/pgc/` | PGC相关Activity |
| `activities/user/FollowActivity.kt` | 关注 |
| `activities/user/FollowingSeasonActivity.kt` | 追番 |

## 七、风险点

1. **B站登录依赖**：播放视频仍需B站登录态（sessData/accessToken），精选视频只是过滤列表，实际播放仍走B站CDN
2. **弹幕深度耦合**：弹幕逻辑嵌入 `VideoPlayerV3ViewModel`，删除需谨慎，避免影响播放器核心
3. **Koin DI 注册**：删除模块后需同步清理 Koin 的 `AppModule` 扫描，否则编译报错
4. **gRPC 模块**：`bili-api-grpc` 体积庞大（大量 proto 文件），若不再需要相关功能可考虑排除
5. **视频播放拦截点**：需在 `launchPlayerActivity()` 和 `VideoInfoScreen.playCurrentVideo()` 两处加入精选列表校验
6. **相关视频推荐跳转**：`VideoInfoScreen` 中"相关视频"区域会跳转到非精选视频，必须删除

## 八、总结

- 项目架构清晰，MVVM + Compose + Koin，改造难度可控
- 已有 `ParentalControlConfig` 和 `DailyWatchStats` 实体类，家长控制的数据模型就绪
- NAS 服务端基础 CRUD 已完成，缺少统计/限制接口
- 核心改造量集中在：**导航精简** + **首页替换** + **播放拦截** + **时长追踪**
- 建议优先完成 TV 端改造（精选列表 + 播放拦截），再补全 NAS 统计接口和时长追踪
