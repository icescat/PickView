# 家长控制系统设计方案

## 项目背景

基于现有 Bilibili TV 播放器（BV）进行减法改造，打造一个专为孩子设计的受控播放器：
- 只能观看家长从 NAS 服务器精选的视频
- 支持观看时长/数量限制
- 去除 B 站推荐、分区、弹幕等干扰内容

## 架构设计

采用**服务端（飞牛NAS）+ TV播放器**的架构：

```
┌─────────────────┐     分享链接      ┌─────────────────┐
│  家长手机(B站APP) │ ───────────────→ │  家长管理APP     │
│                 │                  │  (Android)      │
└─────────────────┘                  └────────┬────────┘
                                              │
                                              ↓ 保存视频信息
                                       ┌─────────────────┐
                                       │   飞牛NAS服务器  │
                                       │  (本地API服务)   │
                                       └────────┬────────┘
                                                │
                                                ↓ 获取列表
                                       ┌─────────────────┐
                                       │   TV播放器(BV)   │
                                       │  (精简改造版)    │
                                       └─────────────────┘
```

## 核心功能

### 1. 内容筛选（白名单机制）

- 家长通过B站APP浏览视频，看到合适的视频后使用"分享"功能
- 家长管理APP接收分享，解析视频信息，上传到NAS服务器
- TV播放器只显示NAS服务器上的视频列表
- 孩子无法看到B站的其他内容

### 2. 观看限制

#### 方案一：按剩余时长自动屏蔽长视频（推荐）
- 家长设置每日总观看时长（如10分钟）
- 系统只显示时长 ≤ 剩余时长的视频
- 孩子只能选能完整看完的视频，不会中途被打断
- 播放时实时扣减观看时长
- 剩余时长接近0时，提前提示"今天时间快用完啦"

#### 方案二：按视频数量限制
- 家长设置每日可观看视频数量（如2个）
- 观看完成判定：播放进度 > 80%
- 达到上限后，列表显示"今日观看额度已用完"

#### 综合方案（最灵活）
可同时开启多个规则：
- 每日总时长上限（10分钟）
- 每日视频数量上限（2个）
- 单视频时长上限（如强制 ≤ 8分钟）
- 特定时段可用（晚上7-8点才能看）

### 3. 人性化设计

- **允许当前视频播完**：即使时间/数量用完，也允许当前视频播完，不会中途打断
- **每日自动重置**：每天固定时间（默认凌晨0点）自动重置限制
- **友好提示**：剩余时间不多时，提前告知孩子

## TV播放器改造方案

### 功能删减清单

| 功能模块 | 原功能 | 改造方案 | 说明 |
|---------|--------|---------|------|
| 左侧导航 | 搜索/个人/主页/分区/影视 | **保留：精选视频 + 设置** | 大幅简化导航 |
| 首页内容 | 推荐/热门/动态 | **替换为精选视频列表** | 从NAS获取 |
| 搜索功能 | 全站搜索 | **改为精选视频内搜索** | 只在家长添加的视频中搜索 |
| 个人页 | 收藏/历史/稍后再看 | **删除** | 不需要 |
| 分区页 | UGC分区 | **删除** | 不需要 |
| 影视页 | PGC影视 | **删除** | 不需要 |
| 弹幕功能 | 弹幕显示/发送 | **删除** | 简化界面 |
| 评论功能 | 评论查看/发送 | **删除** | 不需要 |
| 点赞投币 | 一键三连 | **删除** | 不需要 |
| 关注功能 | 关注UP主 | **删除** | 不需要 |

### 保留功能清单

| 功能 | 说明 |
|------|------|
| 视频播放 | 核心功能，保留播放控制 |
| 播放进度记忆 | 保留断点续播 |
| 播放速度调节 | 保留0.5x-2x速度 |
| 清晰度选择 | 保留画质选择 |
| 字幕功能 | 保留CC字幕显示 |
| B站登录 | 需要登录才能播放视频 |
| 设置页面 | 重新设计，保留必要设置 |

### 新增功能清单

| 功能 | 说明 |
|------|------|
| 精选视频列表 | 从NAS服务器获取视频列表 |
| 观看时长追踪 | 播放时计时，暂停时停止 |
| 视频过滤 | 根据剩余时长过滤视频 |
| 家长控制设置 | NAS地址、限制规则配置 |
| 剩余时长提示 | 界面上显示今日剩余时间 |

## 技术实现

### 服务端（飞牛NAS）

**技术选型**：Python FastAPI + SQLite

**API设计**：
```
POST   /api/videos          # 添加视频（家长APP调用）
GET    /api/videos          # 获取视频列表（TV调用）
GET    /api/videos/search   # 在精选视频中搜索
DELETE /api/videos/{id}     # 删除视频
GET    /api/videos/today    # 获取今日可观看列表（带限制过滤）
POST   /api/stats/watch     # 上报观看进度
GET    /api/stats/remaining # 获取剩余时长/数量
```

**视频信息字段**：
- BV号/AV号
- 标题、封面、时长
- UP主信息
- 添加时间
- 标签/分类（可选）

### 家长管理APP（Android）

**核心功能**：
1. 接收B站分享（标准Android分享功能）
2. 解析BV号/链接
3. 调用B站API获取视频详情
4. 发送到NAS服务器
5. 简单的管理界面（查看/删除已添加的视频）

**技术选型**：Kotlin + Jetpack Compose

### TV播放器修改

#### 导航栏改造

原导航项：`搜索` / `个人` / `主页` / `分区` / `影视`

新导航项：
- **精选视频**（原主页位置，显示NAS视频列表）
- **搜索**（改为精选视频内搜索）
- **设置**

#### 设置页面重新设计

**保留设置项**：
- 播放器设置（画质、解码器、速度等）
- 界面设置（密度、主题等）
- 网络设置（代理等）

**新增设置项**：
- NAS服务器地址
- 家长控制开关
- 每日时长限制
- 每日视频数量限制
- 单视频最大时长
- 允许观看时段
- 重置时间

#### 观看计时逻辑

- **核心原则**：只统计实际观看时间，暂停时不计时
- **状态监听**：
  - `onPlay`（开始播放）→ 启动计时器
  - `onPause`（暂停）→ 暂停计时器，立即上报已观看时长
  - `onResume`（继续播放）→ 恢复计时器
  - `onStop/onRelease`（停止/退出）→ 停止计时器，强制上报最终时长
- **上报频率**：
  - 每 30 秒定期上报一次
  - 暂停时立即上报
  - 视频退出时强制上报
- **防挂机机制**：暂停状态下不计时，避免孩子"挂机"消耗观看时长

## 数据模型

### 精选视频（CuratedVideo）
```kotlin
data class CuratedVideo(
    val id: String,           // 唯一ID
    val bvid: String,         // B站BV号
    val aid: Long,            // B站AV号
    val title: String,        // 标题
    val cover: String,        // 封面URL
    val duration: Int,        // 时长（秒）
    val upId: Long,           // UP主ID
    val upName: String,       // UP主名称
    val addedAt: Long,        // 添加时间
    val addedBy: String       // 添加者
)
```

### 观看统计（DailyWatchStats）
```kotlin
data class DailyWatchStats(
    val date: String,                    // 日期
    val watchedTimeSeconds: Int,         // 已观看时长（累计实际播放时间）
    val watchedVideoCount: Int,          // 已观看视频数
    val currentVideoAid: Long?,          // 当前观看的视频
    val currentVideoWatchedSeconds: Int, // 当前视频已观看时长
    val isTimerRunning: Boolean = false  // 计时器是否正在运行
)
```

**计时器状态说明**：
- `isTimerRunning` 用于标识当前是否处于播放状态
- 暂停时 `isTimerRunning = false`，已观看时长已计入 `currentVideoWatchedSeconds`
- 继续播放时 `isTimerRunning = true`，从当前时间点继续累计

### 家长控制配置（ParentalControlConfig）
```kotlin
data class ParentalControlConfig(
    val enabled: Boolean,                    // 是否启用
    val nasServerUrl: String,                // NAS服务器地址
    val nasApiKey: String,                   // NAS API密钥
    val dailyTimeLimitSeconds: Int,          // 每日时长限制
    val dailyVideoCountLimit: Int,           // 每日数量限制
    val maxSingleVideoDurationSeconds: Int,  // 单视频最大时长
    val allowedStartTime: LocalTime?,        // 允许开始时间
    val allowedEndTime: LocalTime?,          // 允许结束时间
    val resetHour: Int,                      // 重置时间（小时）
    val watchCompletionThreshold: Int,       // 观看完成阈值（%）
    val allowCurrentVideoFinish: Boolean     // 允许当前视频播完
)
```

## 实现阶段

### 第一阶段：MVP（最小可行产品）

**目标**：基础功能跑通

**任务**：
1. 搭建NAS服务端（Python FastAPI）
   - 视频列表CRUD接口
   - SQLite数据库存储
2. 改造TV播放器
   - 删除无关功能（推荐/分区/影视/个人页/弹幕）
   - 简化导航栏为：精选视频 + 搜索 + 设置
   - 实现精选视频列表页面
   - 添加NAS服务器地址配置
3. 最简单的视频添加方式
   - 先用curl/Postman手动添加视频
   - 或简单的网页表单

### 第二阶段：完善体验

**任务**：
1. 开发家长管理APP（Android）
   - 接收B站分享
   - 视频管理界面
2. TV播放器添加限制功能
   - 观看时长统计（播放计时、暂停停止）
   - 视频过滤（根据剩余时长）
   - 友好提示（剩余时间显示）
3. 服务端添加限制逻辑
   - 剩余时长计算
   - 观看记录存储

### 第三阶段：高级功能（可选）

- 观看记录报表（家长查看孩子看了什么）
- 多孩子账号支持
- 临时解锁功能（紧急情况）
- 批量导入/导出视频列表

## 技术栈总结

| 组件 | 技术栈 |
|------|--------|
| NAS服务端 | Python FastAPI + SQLite |
| 家长管理APP | Kotlin + Jetpack Compose |
| TV播放器 | 现有BV项目改造（Kotlin + Jetpack Compose） |
| 通信协议 | HTTP REST API + JSON |

## 注意事项

1. **网络环境**：TV和NAS需要在同一局域网，或NAS有公网访问能力
2. **B站登录**：TV播放器需要登录B站账号才能播放视频
3. **API限制**：频繁调用B站API可能触发风控，需要合理设计缓存
4. **数据安全**：NAS服务建议添加简单的认证机制（如API Key）

## 文件结构

### 改造后的TV播放器结构

```
bv/
├── app/src/main/kotlin/dev/aaa1115910/bv/
│   ├── entity/
│   │   ├── ParentalControlConfig.kt      # 家长控制配置
│   │   ├── DailyWatchStats.kt            # 观看统计
│   │   └── CuratedVideo.kt               # 精选视频
│   ├── network/
│   │   └── NasServerApi.kt               # NAS服务端API
│   ├── repository/
│   │   └── CuratedVideoRepository.kt     # 精选视频数据仓库
│   ├── player/
│   │   └── WatchTimeTracker.kt           # 观看时长追踪器（播放状态监听）
│   ├── screen/
│   │   ├── curated/
│   │   │   └── CuratedVideoScreen.kt     # 精选视频列表页（替代原首页）
│   │   ├── search/
│   │   │   └── CuratedSearchScreen.kt    # 精选视频内搜索（替代原搜索）
│   │   ├── settings/
│   │   │   └── ParentalControlSetting.kt # 家长控制设置
│   │   └── MainScreen.kt                 # 修改后的主界面
│   └── viewmodel/
│       └── curated/
│           └── CuratedVideoViewModel.kt  # 精选视频ViewModel
├── server/                                # NAS服务端（Python）
│   ├── main.py
│   ├── models.py
│   └── database.db
└── PARENTAL_CONTROL_DESIGN.md             # 本文档
```

### 删除的文件/目录（原BV功能）

```
app/src/main/kotlin/dev/aaa1115910/bv/
├── screen/main/home/          # 推荐/热门/动态
├── screen/main/ugc/           # 分区
├── screen/main/pgc/           # 影视
├── screen/user/               # 个人页（收藏/历史/追番等）
├── screen/search/             # 原搜索（改为精选内搜索）
├── component/DanmakuPlayerCompose.kt    # 弹幕
├── component/controllers/DanmakuMenu.kt # 弹幕菜单
└── viewmodel/home/            # 首页ViewModel
    ├── DynamicViewModel.kt
    ├── PopularViewModel.kt
    └── RecommendViewModel.kt
```

## 界面设计要点

### 精选视频列表页

- 简洁的网格布局显示视频封面
- 每个卡片显示：封面、标题、时长、UP主
- 右上角显示今日剩余时长
- 空状态时显示"请家长添加视频"

### 播放器界面

- 去除弹幕相关按钮
- 去除点赞/投币/收藏按钮
- 保留：播放控制、进度条、清晰度、速度、字幕
- 顶部显示剩余可观看时长

### 设置页面

- 分组显示：播放器设置、家长控制设置
- 家长控制需要简单验证（如PIN码）

## 后续优化方向

- 支持多个B站账号（家长和孩子分开登录）
- 支持其他视频平台（YouTube、抖音等）
- AI辅助内容审核（自动识别不良内容）
- 观看习惯分析报告
- 远程控制（家长手机端控制TV端）
