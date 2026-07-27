# BV 改造方案 — 最终确认版

> 确认日期：2026-05-26
> 核心原则：只能查看和播放NAS精选列表中的视频，屏蔽一切跳转到列表外视频的入口

---

## 一、导航栏改造

### 变化

```
原：🔍搜索  👤个人  🏠主页  📺分区  🎬影视  ⚙️设置
新：🔍搜索  👤个人  🏠主页              ⚙️设置
```

### 各导航项改造明细

| 导航项 | 改造 |
|--------|------|
| 🔍 搜索 | 改为仅搜索NAS精选列表 |
| 👤 个人 | 精简：只保留登录/头像/用户名/账号切换 |
| 🏠 主页 | 替换为NAS精选视频列表 |
| 📺 分区 | **删除** |
| 🎬 影视 | **删除** |
| ⚙️ 设置 | 保留原有 + 新增家长控制分组 |

---

## 二、各模块详细改造

### 2.1 主页 → NAS精选视频列表

**替换内容**：删除推荐/热门/动态三个Tab，替换为NAS精选视频网格列表

**列表功能**：
- 网格布局显示视频卡片（封面 + 标题 + 时长 + UP主）
- 右上角显示"今日剩余 X 分钟可看"
- 根据剩余观看时长过滤：只显示 `duration ≤ remainingTime` 的视频
- 剩余时间不足时灰化/隐藏列表项
- 剩余时长接近0时弹出友好提示"今天时间快用完啦，明天再看吧！"
- 空列表时显示"请家长添加视频"

**新增文件**：
- `network/NasServerApi.kt` — NAS API客户端
- `repository/CuratedVideoRepository.kt` — 精选视频数据仓库
- `viewmodel/curated/CuratedVideoViewModel.kt` — 精选视频ViewModel
- `screen/main/home/CuratedVideoScreen.kt` — 精选视频列表页（替代原HomeContent）

**删除文件**：
- `screen/main/home/RecommendScreen.kt`
- `screen/main/home/PopularScreen.kt`
- `screen/main/home/DynamicsScreen.kt`
- `viewmodel/home/RecommendViewModel.kt`
- `viewmodel/home/PopularViewModel.kt`
- `viewmodel/home/DynamicViewModel.kt`
- `screen/main/HomeContent.kt`（替换为新版）

### 2.2 搜索 → 精选列表内搜索

**改造**：搜索范围从B站全站改为NAS精选列表

**实现方式**：
- 在已加载的精选视频列表中本地过滤（标题/UP主名匹配）
- 或调用NAS搜索接口 `GET /api/videos/search?keyword=xxx`

**修改文件**：
- `screen/search/SearchInputScreen.kt` — 移除B站热搜词，改为精选内搜索
- `screen/search/SearchResultScreen.kt` — 搜索结果只显示精选列表中的视频
- `viewmodel/search/SearchInputViewModel.kt` — 移除B站热搜API调用
- `viewmodel/search/SearchResultViewModel.kt` — 搜索逻辑改为精选内搜索

### 2.3 个人页 → 精简版

**保留**：
- B站登录/登出功能
- 用户头像和用户名显示
- 账号切换功能

**删除**：
- 收藏列表（可跳转B站视频）
- 历史记录（可跳转B站视频）
- 稍后再看（可跳转B站视频）
- 追番/追剧（可跳转B站内容）
- 关注的UP主列表（可跳转UP主主页→非精选视频）

**删除文件**：
- `screen/user/FavoriteScreen.kt`
- `screen/user/HistoryScreen.kt`
- `screen/user/ToViewScreen.kt`
- `screen/user/FollowingSeasonScreen.kt`
- `screen/user/FollowScreen.kt`
- `screen/user/UpInfoScreen.kt`
- `viewmodel/user/FavoriteViewModel.kt`
- `viewmodel/user/HistoryViewModel.kt`
- `viewmodel/user/ToViewViewModel.kt`
- `viewmodel/user/FollowingSeasonViewModel.kt`
- `viewmodel/user/FollowViewModel.kt`
- `viewmodel/user/UpInfoViewModel.kt`
- `activities/user/FollowActivity.kt`
- `activities/user/FollowingSeasonActivity.kt`

**修改文件**：
- `screen/main/PersonalContent.kt` — 精简为仅登录/头像/用户名/账号切换

### 2.4 分区 → 删除

**删除所有UGC相关代码**：

**删除文件**：
- `screen/main/UgcContent.kt`
- `viewmodel/ugc/UgcViewModel.kt`
- `activities/pgc/PgcIndexActivity.kt`
- `viewmodel/index/PgcIndexViewModel.kt`
- `viewmodel/pgc/` 整个目录（PgcAnimeViewModel等6个）
- `component/pgc/IndexFilter.kt`

### 2.5 影视 → 删除

**删除所有PGC相关代码**：

**删除文件**：
- `screen/main/PgcContent.kt`
- `screen/SeasonInfoScreen.kt`
- `activities/video/SeasonInfoActivity.kt`
- `component/videocard/SeasonCard.kt`
- `entity/carddata/SeasonCardData.kt`

### 2.6 视频详情页 → 去除跳转入口

**核心原则**：屏蔽一切可能跳转到列表外视频的功能

**删除**：
- 相关视频推荐区域（`VideosRow` — 可跳转非精选视频）
- UP主头像/名称的点击跳转（`UpInfoActivity` — 可浏览UP主其他视频）
- 标签点击跳转（`TagActivity` — 可浏览标签下其他视频）

**保留**：
- 封面 + 标题 + 播放量等信息展示
- 视频分P选择
- 合集内视频选择（仅限当前视频合集内的分集）
- 播放按钮

**修改文件**：
- `screen/VideoInfoScreen.kt` — 删除相关视频推荐、UP主跳转、标签跳转
- `component/buttons/LikeButton.kt` — 删除（点赞）
- `component/buttons/CoinButton.kt` — 删除（投币）
- `component/buttons/FavoriteButton.kt` — 删除（收藏）
- `component/buttons/SeasonInfoButtons.kt` — 删除

**删除文件**：
- `activities/video/UpInfoActivity.kt`
- `activities/video/TagActivity.kt`
- `screen/UpInfoScreen.kt`
- `screen/TagScreen.kt`
- `viewmodel/TagViewModel.kt`

### 2.7 设置页 → 新增家长控制

**保留原有设置项**：
- 音视频设置、播放器类型、界面设置、网络设置、存储设置、关于等

**新增家长控制分组**（进入需PIN码验证）：
- NAS服务器地址
- 家长控制开关
- 每日时长限制（分钟）
- 每日视频数量限制
- 单视频最大时长（分钟）
- 允许观看时段（开始时间-结束时间）
- 重置时间（几点重置每日限制）
- 观看完成阈值（默认80%）
- 允许当前视频播完（默认开启）

**PIN码验证**：
- 遥控器数字键直接输入
- `*` 遮盖显示
- 开发期可跳过

**新增文件**：
- `screen/settings/content/ParentalControlSetting.kt` — 家长控制设置页
- `component/PinCodeDialog.kt` — PIN码输入对话框

**修改文件**：
- `screen/settings/SettingsScreen.kt` — 新增家长控制菜单项
- `util/Prefs.kt` — 新增家长控制相关配置项

---

## 三、播放拦截机制

### 3.1 播放入口拦截

所有跳转到播放器的入口必须经过精选列表校验：

| 入口 | 拦截方式 |
|------|----------|
| 精选列表点击 | ✅ 天然在列表内，无需额外拦截 |
| 搜索结果点击 | ✅ 搜索范围已限定精选列表 |
| 相关视频推荐 | ❌ 已删除此功能 |
| UP主主页 | ❌ 已删除此功能 |
| 标签页 | ❌ 已删除此功能 |
| 合集内跳转 | ⚠️ 需检查：合集内其他视频是否在精选列表中 |

### 3.2 VideoPlayerV3Activity 拦截

在 `VideoPlayerV3Activity.actionStart()` 中增加校验：
- 检查 avid 是否在精选列表中
- 不在列表中则拒绝播放

---

## 四、家长控制 — 观看时长追踪

### 4.1 核心逻辑

```
App启动
  → 检查是否在允许时段 → 不在 → 显示"现在不是观看时间"
  → 加载精选视频列表（从NAS）
  → 读取 DailyWatchStats（本地DataStore）
  → 检查是否跨天 → 是 → 重置统计
  → 计算 remainingTime = dailyLimit - watchedTime
  → 计算 remainingCount = dailyLimit - watchedCount
  → 过滤列表：只显示 duration ≤ remainingTime 的视频
  → 列表标注"剩余 X 分钟可看"

点击播放
  → 检查 remainingTime > 0 && remainingCount > 0
  → 不满足 → 显示"今天观看额度已用完"
  → 满足 → 进入播放
  → 播放中：每5-10秒扣减 remainingTime
  → 播放进度 > 80% → watchedCount++
  → 播放结束 → 刷新列表（可能更多视频被过滤）
  → remainingTime 接近0 → 提示"今天时间快用完啦"
```

### 4.2 时长追踪器

**新增文件**：`player/WatchTimeTracker.kt`

**监听播放器状态**：
- `onPlay` → 启动计时器
- `onPause` → 暂停计时器
- `onResume` → 恢复计时器
- `onStop/onRelease` → 停止计时器，保存最终时长

**更新频率**：每5-10秒更新一次本地已观看秒数

**人性化处理**：
- 允许当前视频播完（即使时间用完）
- 播完后不再显示新视频，或只显示更短的

### 4.3 综合限制规则

可同时开启多个规则：
- 每日总时长上限（如10分钟）
- 每日视频数量上限（如2个）
- 单视频时长上限（如强制 ≤ 8分钟）
- 特定时段可用（如晚上7-8点才能看）

所有规则在列表加载和播放前检查。

### 4.4 数据持久化

**本地存储（DataStore）**：
- `DailyWatchStats` — 每日观看统计
- `ParentalControlConfig` — 家长控制配置
- 每日自动重置（检查日期变化）

---

## 五、Prefs 新增配置项

```kotlin
// NAS服务器
var nasServerUrl by pref(PrefKeys.prefNasServerUrl, "")
var nasApiKey by pref(PrefKeys.prefNasApiKey, "")

// 家长控制
var parentalControlEnabled by pref(PrefKeys.prefParentalControlEnabled, false)
var parentalPinCode by pref(PrefKeys.prefParentalPinCode, "")
var dailyTimeLimitMinutes by pref(PrefKeys.prefDailyTimeLimitMinutes, 0)
var dailyVideoCountLimit by pref(PrefKeys.prefDailyVideoCountLimit, 0)
var maxSingleVideoDurationMinutes by pref(PrefKeys.prefMaxSingleVideoDurationMinutes, 0)
var allowedStartHour by pref(PrefKeys.prefAllowedStartHour, -1)  // -1=不限制
var allowedStartMinute by pref(PrefKeys.prefAllowedStartMinute, 0)
var allowedEndHour by pref(PrefKeys.prefAllowedEndHour, -1)
var allowedEndMinute by pref(PrefKeys.prefAllowedEndMinute, 0)
var resetHour by pref(PrefKeys.prefResetHour, 0)
var watchCompletionThreshold by pref(PrefKeys.prefWatchCompletionThreshold, 80)
var allowCurrentVideoFinish by pref(PrefKeys.prefAllowCurrentVideoFinish, true)
```

---

## 六、NAS服务端需补充的接口

| 接口 | 方法 | 说明 | 优先级 |
|------|------|------|--------|
| `/api/videos/search` | GET | 精选视频搜索 `?keyword=xxx` | 高 |
| `/api/stats/watch` | POST | 上报观看进度（可选，本地计算为主） | 低 |
| `/api/stats/remaining` | GET | 获取剩余时长（可选，本地计算为主） | 低 |

当前NAS服务端已有基础CRUD，搜索接口为最高优先级补充项。

---

## 七、改造文件清单汇总

### 新增文件

| 文件 | 说明 |
|------|------|
| `network/NasServerApi.kt` | NAS API客户端 |
| `repository/CuratedVideoRepository.kt` | 精选视频数据仓库 |
| `viewmodel/curated/CuratedVideoViewModel.kt` | 精选视频ViewModel |
| `screen/main/home/CuratedVideoScreen.kt` | 精选视频列表页 |
| `player/WatchTimeTracker.kt` | 观看时长追踪器 |
| `screen/settings/content/ParentalControlSetting.kt` | 家长控制设置页 |
| `component/PinCodeDialog.kt` | PIN码输入对话框 |

### 删除文件

| 文件 | 说明 |
|------|------|
| `screen/main/home/RecommendScreen.kt` | 推荐 |
| `screen/main/home/PopularScreen.kt` | 热门 |
| `screen/main/home/DynamicsScreen.kt` | 动态 |
| `viewmodel/home/RecommendViewModel.kt` | 推荐VM |
| `viewmodel/home/PopularViewModel.kt` | 热门VM |
| `viewmodel/home/DynamicViewModel.kt` | 动态VM |
| `screen/main/UgcContent.kt` | UGC分区 |
| `viewmodel/ugc/UgcViewModel.kt` | UGC VM |
| `screen/main/PgcContent.kt` | PGC影视 |
| `viewmodel/pgc/` 整个目录 | PGC相关VM |
| `screen/SeasonInfoScreen.kt` | 番剧详情 |
| `activities/video/SeasonInfoActivity.kt` | 番剧Activity |
| `activities/pgc/PgcIndexActivity.kt` | PGC索引 |
| `viewmodel/index/PgcIndexViewModel.kt` | PGC索引VM |
| `component/pgc/IndexFilter.kt` | PGC筛选 |
| `component/videocard/SeasonCard.kt` | 番剧卡片 |
| `entity/carddata/SeasonCardData.kt` | 番剧卡片数据 |
| `screen/user/FavoriteScreen.kt` | 收藏 |
| `screen/user/HistoryScreen.kt` | 历史 |
| `screen/user/ToViewScreen.kt` | 稍后再看 |
| `screen/user/FollowingSeasonScreen.kt` | 追番 |
| `screen/user/FollowScreen.kt` | 关注 |
| `screen/user/UpInfoScreen.kt` | UP主信息 |
| `viewmodel/user/FavoriteViewModel.kt` | 收藏VM |
| `viewmodel/user/HistoryViewModel.kt` | 历史VM |
| `viewmodel/user/ToViewViewModel.kt` | 稍后再看VM |
| `viewmodel/user/FollowingSeasonViewModel.kt` | 追番VM |
| `viewmodel/user/FollowViewModel.kt` | 关注VM |
| `viewmodel/user/UpInfoViewModel.kt` | UP主VM |
| `activities/user/FollowActivity.kt` | 关注Activity |
| `activities/user/FollowingSeasonActivity.kt` | 追番Activity |
| `activities/video/UpInfoActivity.kt` | UP主Activity |
| `activities/video/TagActivity.kt` | 标签Activity |
| `screen/UpInfoScreen.kt` | UP主页 |
| `screen/TagScreen.kt` | 标签页 |
| `viewmodel/TagViewModel.kt` | 标签VM |
| `component/buttons/LikeButton.kt` | 点赞按钮 |
| `component/buttons/CoinButton.kt` | 投币按钮 |
| `component/buttons/FavoriteButton.kt` | 收藏按钮 |
| `component/buttons/SeasonInfoButtons.kt` | 番剧按钮 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `screen/main/LeftNaviContent.kt` | 删除UGC/PGC导航项 |
| `screen/MainScreen.kt` | 删除UGC/PGC路由，Home改为精选列表 |
| `screen/main/HomeContent.kt` | 替换为精选视频列表 |
| `screen/main/PersonalContent.kt` | 精简为登录/头像/用户名/账号切换 |
| `screen/VideoInfoScreen.kt` | 删除相关推荐、UP主跳转、标签跳转、互动按钮 |
| `screen/search/SearchInputScreen.kt` | 改为精选内搜索 |
| `screen/search/SearchResultScreen.kt` | 搜索结果只显示精选视频 |
| `screen/settings/SettingsScreen.kt` | 新增家长控制菜单项 |
| `viewmodel/search/SearchInputViewModel.kt` | 移除B站热搜API |
| `viewmodel/search/SearchResultViewModel.kt` | 改为精选内搜索 |
| `activities/video/VideoPlayerV3Activity.kt` | 集成WatchTimeTracker + 播放拦截 |
| `viewmodel/player/VideoPlayerV3ViewModel.kt` | 集成时长追踪 |
| `util/Prefs.kt` | 新增家长控制配置项 |
| `activities/MainActivity.kt` | 启动时检查家长控制 |
| `BVApp.kt` | 可能需调整Koin模块注册 |
| `AndroidManifest.xml` | 删除已移除Activity的声明 |
