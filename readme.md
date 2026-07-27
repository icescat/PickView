# BiliTV - B站精选视频管理

B站精选视频的NAS服务端 + 多端客户端（手机 / 平板 / TV），为儿童提供安全的B站视频观看体验，由家长通过手机端挑选并推送视频。

## 项目结构

```
BiliTV/
├── fnosserver/          # 飞牛NAS服务端（FastAPI）
│   ├── app/
│   │   ├── main.py      # 主程序（API + Web管理页）
│   │   ├── models.py    # 数据模型
│   │   ├── database.py  # 数据库操作
│   │   └── __init__.py
│   ├── docker-compose.yml
│   ├── Dockerfile
│   └── requirements.txt
├── BiliPick/            # Android 手机端（家长管理端，接收分享推送视频）
├── bv/                  # Android TV 端（视频精选，儿童观看）
├── bvpad/               # Android Pad 端（视频精选，触屏设备观看）
└── Docs/                # 部署与排查文档
```

## 端能力对照

| 维度 | 手机端 BiliPick | TV 端 视频精选（bv） | Pad 端 视频精选（bvpad） |
|------|----------------|----------------------|--------------------------|
| 使用场景 | 家长挑选/推送/管理视频 | 儿童在电视上观看 | 儿童在平板上观看 |
| 应用形态 | 手机 App | TV App（leanback 必需） | 触屏 App（触摸屏必需） |
| 视频播放 | 跳转 B 站官方 App 播放 | 内置播放器 | 内置播放器 |
| 家长控制 | 仅只读展示 | 不显示，由手机端配置 | 不显示，由手机端配置 |
| 添加视频 | 分享接收或粘贴链接 | 不支持 | 不支持 |
| minSdk | 24（Android 7.0+） | 21（Android 5.0+） | 24（Android 7.0+） |

## 服务端功能

- REST API：视频、分类、系列、观看状态、家长控制、观看统计的增删改查与批量操作
- B站链接解析：粘贴链接自动获取视频信息（标题、UP主、时长、封面）
- API Key 鉴权：除 `/` 和 `/api/health` 外，所有 `/api/*` 接口需在请求头携带 `X-API-Key`（通过环境变量 `NAS_API_KEY` 配置，部署时自行设置）
- Web管理页：`http://<NAS_IP>:9530/admin`
  - 视频管理：列表查看、搜索、按分类/系列筛选、批量删除/移动分类/加入系列、点击"播放"跳转B站 web 端播放
  - 添加视频：单个链接添加、批量链接添加
  - 分类管理：增删改分类、自定义排序
  - 系列管理：增删改系列、查看系列详情、管理系列内视频顺序

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 服务状态 |
| GET | `/api/health` | 健康检查 |
| GET | `/api/resolve?url=` | 解析B站链接，获取视频信息 |
| GET | `/api/videos` | 获取视频列表（可按分类/系列过滤） |
| POST | `/api/videos` | 添加视频 |
| GET | `/api/videos/{id}` | 获取单个视频 |
| DELETE | `/api/videos/{id}` | 删除视频 |
| DELETE | `/api/videos/bvid/{bvid}` | 按BV号删除视频 |
| PUT | `/api/videos/{id}/category` | 修改视频分类 |
| POST | `/api/videos/batch-delete` | 批量删除 |
| POST | `/api/videos/batch-move` | 批量移动分类 |
| GET | `/api/videos/{id}/watch-status` | 获取观看状态 |
| POST | `/api/videos/{id}/watch-status` | 更新观看状态 |
| GET | `/api/videos/watch-status/batch` | 批量获取观看状态 |
| GET | `/api/categories` | 获取分类列表 |
| POST | `/api/categories` | 创建分类 |
| PUT | `/api/categories/{id}` | 修改分类 |
| DELETE | `/api/categories/{id}` | 删除分类 |
| GET | `/api/series` | 获取系列列表 |
| POST | `/api/series` | 创建系列 |
| GET | `/api/series/{id}` | 获取系列详情 |
| PUT | `/api/series/{id}` | 修改系列 |
| DELETE | `/api/series/{id}` | 删除系列 |
| GET | `/api/series/{id}/videos` | 获取系列内视频 |
| POST | `/api/series/{id}/videos` | 添加视频到系列 |
| DELETE | `/api/series/{id}/videos/{video_id}` | 从系列移除视频 |
| PUT | `/api/series/{id}/videos/order` | 更新系列视频顺序 |
| GET | `/api/parental-control` | 获取家长控制配置 |
| POST | `/api/parental-control` | 保存家长控制配置 |
| GET | `/api/watch-stats` | 获取某日观看统计 |
| POST | `/api/watch-stats` | 增量更新某日观看统计 |
| GET | `/admin` | Web管理页面 |

## 快速部署

详见 [飞牛NAS部署指南](Docs/飞牛NAS部署指南.md)

### Docker Compose 一键部署

1. 将 `fnosserver/` 目录上传到NAS
2. 进入目录执行：

```bash
cd /docker/bilipick-server
docker-compose up -d
```

3. 访问 `http://<NAS_IP>:9530/admin` 打开管理页

### 从旧版升级

```bash
cd /docker/bilipick-server
docker-compose down
# 更新 app/ 目录中的文件
docker-compose up -d
```

数据存储在 `./data/videos.db`，升级不会丢失数据。

---

## 安卓手机端（BiliPick）

位于 `BiliPick/` 目录，使用 Jetpack Compose 开发，作为家长端管理工具，配合 NAS 服务端和 TV/Pad 端使用。

### 核心功能

- **视频管理**
  - 列表/网格两种布局切换
  - 长按进入多选模式，支持批量删除、批量移动分类、批量加入系列
  - 单击视频卡片在 B 站官方 App 中打开播放（未安装时回退浏览器）
- **添加视频**
  - 通过系统分享面板接收 B 站链接（`b23.tv`、`bilibili.com` 或纯 BV 号）
  - 自动解析视频信息（标题、UP主、时长、封面）
  - 推送前可选择归档分类（默认不分类）和加入系列（默认不加入）
- **设置**
  - 服务器地址与 API Key 配置
  - 连接测试（自动校验 `/api/categories` 鉴权）
  - 分类管理、系列管理、使用说明等子页面

### 接收分享的链接类型

- `https://b23.tv/xxxxxxx`
- `https://www.bilibili.com/video/BVxxxxxxxx`
- 纯 `BVxxxxxxxx` 文本

### 构建

在 `BiliPick/` 目录执行：

```bash
./gradlew :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

---

## 视频精选 - TV 端（bv）

位于 `bv/` 目录，基于 Jetpack Compose for TV 开发，为儿童提供纯观看体验，配合 NAS 服务端使用。应用名「视频精选」，原项目代号 BV。

### 系统要求

- Android 5.0（API 21）及以上
- armeabi-v7a / arm64-v8a / x86 / x86_64 架构
- leanback（电视）设备

### 核心功能

- **视频浏览**
  - 左侧导航栏：首页、系列、搜索、个人页（稍后再看/历史/收藏/追番）、账号、设置
  - 按分类和系列浏览 NAS 服务端精选视频
  - 搜索视频（支持热搜词推荐和历史记录）
- **视频播放**
  - 内置播放器（基于 ExoPlayer / Media3），支持进度拖拽、倍速播放、循环播放
  - 多分 P 视频支持，合集播放支持
  - 字幕支持
- **家长控制**（数据源为 NAS，TV 端只读）
  - 观看时长限制（每日总时长）
  - 视频数量限制（按短/中/长分类，次日清零）
  - 单视频最大时长限制（超限视频隐藏不显示）
  - 允许观看时段限制
  - 观看完成阈值（按播放百分比计为已观看）
  - PIN 码锁定，配置变更次日生效
- **设置**
  - 网络设置：NAS 服务器地址（内置外网域名 / 内网 IP 双选）、API Key、代理、CDN
  - 界面设置：启动页、密度、视频信息显示、持续进度条
  - 音视频设置：播放器类型、解码优先级、音频输出方式
  - 存储设置：缓存清理
  - 其他设置：无痕模式等
  - 关于页面

### 服务器配置

首次使用需在 TV 端配置 NAS 服务器地址：

1. 进入 **设置 → 网络设置**
2. 点击 **NAS 服务器地址**，选择：
   - **外网（域名）** → `http://<your-nas-domain>:9530`
   - **内网（IP）** → `http://<your-nas-ip>:9530`
3. API Key 需与服务端 `NAS_API_KEY` 环境变量保持一致

### 构建

在 `bv/` 目录执行：

```bash
./gradlew :app:assembleDefaultDebug
```

生成的 APK 位于 `app/build/outputs/apk/default/debug/`。

> **注意：** 项目使用 Java 17 编译并启用了 Core Library Desugaring，以兼容 Android 5.0+。如遇内存不足导致构建失败，可适当调整 `gradle.properties` 中的 JVM 内存参数。

---

## 视频精选 - Pad 端（bvpad）

位于 `bvpad/` 目录，与 TV 端同源（共享 `dev.aaa1115910.bv` namespace 与 BVApp 入口），针对触屏设备适配。应用名同为「视频精选」。

### 系统要求

- Android 7.0（API 24）及以上
- arm64-v8a / x86_64 架构
- 触摸屏设备（`android.hardware.touchscreen` 必需，`android.software.leanback` 非必需）

### 与 TV 端的差异

| 维度 | TV 端（bv） | Pad 端（bvpad） |
|------|-------------|------------------|
| applicationId | `dev.frost819.bv`（小米电视屏蔽原包名后的备用包名） | `dev.aaa1115910.bvpad` |
| minSdk | 21 | 24 |
| leanback 特性 | 必需 | 非必需 |
| 触摸屏 | 非必需 | 必需 |
| ABI（release） | arm64-v8a | arm64-v8a |
| ABI（debug） | arm64-v8a / x86_64 | arm64-v8a / x86_64 |

### 功能特性

Pad 端在功能上与 TV 端基本一致（视频浏览、播放、家长控制、设置等），主要差异在于：

- 交互方式：以触屏操作为主，不依赖遥控器焦点
- 设备适配：针对平板分辨率与触控交互优化

### 构建

在 `bvpad/` 目录执行：

```bash
./gradlew :app:assembleDefaultDebug
```

生成的 APK 位于 `app/build/outputs/apk/default/debug/`。

> **注意：** Pad 端同样使用 Java 17 编译并启用 Core Library Desugaring。`productFlavors` 含 `lite` 与 `default` 两个 channel，构建 lite 版时使用 `assembleLiteDebug`。
