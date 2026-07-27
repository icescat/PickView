# 飞牛NAS部署 B站精选服务端 指南

## 概述

本文档指导如何在飞牛OS（fnOS）上使用Docker部署B站精选服务端（v1.4.0）。

v1.4.0 新增功能：
- Web管理页面（`/admin`），支持可视化操作
- B站链接自动解析，粘贴链接即可添加视频
- 批量视频管理（删除、移动分类）
- 分类增删改

## 前提条件

- 飞牛NAS已安装Docker套件
- 已开启SSH或可通过Web界面操作
- 知道NAS的IP地址

## 部署步骤

### 方法一：通过飞牛Web界面部署（推荐）

#### 1. 准备文件

在电脑上创建以下文件结构：

```
bilipick-server/
├── docker-compose.yml
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── models.py
│   └── database.py
```

**文件内容详见项目目录 `fnosserver/`**

#### 2. 上传文件到NAS

1. 打开飞牛Web界面
2. 进入 **文件管理**
3. 创建文件夹：`/docker/bilipick-server`
4. 将所有文件上传到此目录，保持目录结构

#### 3. 创建Docker容器

1. 打开飞牛Web界面
2. 进入 **Docker** 应用
3. 点击 **容器** → **创建容器**
4. 选择 **使用Docker Compose**

#### 4. 配置Docker Compose

在Docker Compose配置区域粘贴以下内容：

```yaml
services:
  bilipick-server:
    image: python:3.11-slim
    container_name: bilipick-server
    working_dir: /app
    command: >
      sh -c "pip install fastapi uvicorn pydantic httpx -q &&
             uvicorn main:app --host 0.0.0.0 --port 9530"
    ports:
      - "9530:9530"
    volumes:
      - ./app:/app
      - ./data:/app/data
    restart: unless-stopped
    environment:
      - TZ=Asia/Shanghai
```

#### 5. 启动容器

1. 点击 **启动**
2. 等待启动完成（首次需要下载Python镜像和安装依赖，约2-3分钟）
3. 查看容器状态，显示 **运行中** 即表示成功

#### 6. 验证部署

在浏览器访问：
```
http://你的NAS_IP:9530
```

应该看到返回JSON：
```json
{
  "status": "running",
  "service": "B站精选NAS服务端",
  "version": "1.4.0"
}
```

访问管理页面：
```
http://你的NAS_IP:9530/admin
```

---

### 方法二：通过SSH命令行部署

#### 1. SSH连接到NAS

```bash
ssh root@你的NAS_IP
```

#### 2. 创建项目目录

```bash
mkdir -p /docker/bilipick-server/app
mkdir -p /docker/bilipick-server/data
```

#### 3. 上传文件

将 `fnosserver/` 目录下的文件上传到NAS：

- `docker-compose.yml` → `/docker/bilipick-server/`
- `app/main.py` → `/docker/bilipick-server/app/`
- `app/models.py` → `/docker/bilipick-server/app/`
- `app/database.py` → `/docker/bilipick-server/app/`
- `app/__init__.py` → `/docker/bilipick-server/app/`

可以使用 scp 命令：
```bash
# 在本地电脑执行
scp docker-compose.yml root@NAS_IP:/docker/bilipick-server/
scp app/*.py root@NAS_IP:/docker/bilipick-server/app/
```

#### 4. 启动容器

```bash
cd /docker/bilipick-server
docker-compose up -d
```

#### 5. 查看日志

```bash
docker-compose logs -f
```

#### 6. 验证部署

```bash
curl http://localhost:9530
```

---

## Web管理页使用

部署成功后，浏览器访问 `http://NAS_IP:9530/admin`

### 添加视频

1. 切换到 **添加视频** 标签页
2. 在输入框粘贴B站视频链接或BV号（如 `BV1xx411c7mD` 或完整URL）
3. 选择分类（可选）
4. 点击 **解析并添加**，系统自动获取视频信息并添加

批量添加：在文本框中每行粘贴一个链接，点击 **批量解析并添加**

### 管理视频

1. 在 **视频管理** 标签页查看所有视频
2. 使用分类下拉框筛选，搜索框按标题/BV号搜索
3. 单个操作：点击每行的 **移动** / **删除** 按钮
4. 批量操作：勾选多个视频 → 使用顶部批量操作栏（移动分类/批量删除）

### 管理分类

1. 切换到 **分类管理** 标签页
2. 点击 **+ 新建分类** 添加分类
3. 点击分类旁的 **编辑** 修改名称或排序
4. 点击 **删除** 移除分类（视频不会被删除，变为未分类）

---

## 常用管理命令

### 查看容器状态

```bash
docker ps | grep bilipick
```

### 停止服务

```bash
cd /docker/bilipick-server
docker-compose down
```

### 重启服务

```bash
cd /docker/bilipick-server
docker-compose restart
```

### 查看日志

```bash
cd /docker/bilipick-server
docker-compose logs -f
```

### 更新代码后重新启动

```bash
cd /docker/bilipick-server
docker-compose restart
```

> 使用 `docker-compose.yml` 挂载 `./app:/app`，代码更新后只需重启即可生效，无需重新构建镜像。

---

## 飞牛OS特有注意事项

### 1. 端口

服务使用 **9530** 端口。飞牛OS默认占用 8000（Web界面）、8080（Docker管理）、443（HTTPS），9530通常不会冲突。

如需修改端口，编辑 `docker-compose.yml` 中的 `ports` 行，如改为 `9540:9530`。

### 2. 防火墙设置

如果无法访问，检查飞牛防火墙：

1. 打开飞牛Web界面
2. 进入 **设置** → **安全**
3. 添加防火墙规则，允许9530端口

或通过SSH：

```bash
iptables -A INPUT -p tcp --dport 9530 -j ACCEPT
```

### 3. 数据持久化

数据存储在 `./data` 目录：
- 宿主机：`/docker/bilipick-server/data/videos.db`
- 容器内：`/app/data/videos.db`

删除容器不会丢失数据。

### 4. 自动启动

配置中已设置 `restart: unless-stopped`，NAS重启后自动启动。

---

## 从旧版（v1.0-v1.3）升级

旧版使用端口 9525，新版使用 9530。升级步骤：

### 1. 停止旧容器

```bash
cd /docker/bilipick-server
docker-compose down
```

### 2. 更新文件

将新版 `app/` 目录下的文件替换旧文件，并更新 `docker-compose.yml`。

### 3. 启动新容器

```bash
docker-compose up -d
```

### 4. 数据迁移

旧版数据库位于 `./data/videos.db`，新版兼容旧版数据库结构，无需额外迁移。

如果旧版端口是 9525，需要更新客户端配置为新端口 9530。

---

## 故障排查

### 问题1：容器启动失败

```bash
# 查看日志
docker-compose logs

# 常见原因：端口冲突
netstat -tlnp | grep 9530
```

### 问题2：无法访问API

```bash
# 1. 检查容器是否运行
docker ps | grep bilipick

# 2. 检查容器日志
docker-compose logs

# 3. 检查防火墙
iptables -L -n | grep 9530
```

### 问题3：链接解析失败

B站链接解析需要容器能访问 `api.bilibili.com`。如果NAS网络受限：
- 检查DNS配置
- 确认容器可以访问外网：`docker exec bilipick-server curl -s https://api.bilibili.com`

### 问题4：数据丢失

```bash
ls -la /docker/bilipick-server/data/
```

确保目录有写入权限。如权限异常：
```bash
chmod 777 /docker/bilipick-server/data/
```

---

## 手机/TV APP配置

在客户端中填入服务器地址：`http://你的NAS_IP:9530`

---

## 备份与恢复

### 备份

```bash
cp /docker/bilipick-server/data/videos.db /docker/backup/videos_$(date +%Y%m%d).db
```

### 恢复

```bash
docker-compose down
cp /docker/backup/videos_20240101.db /docker/bilipick-server/data/videos.db
docker-compose up -d
```

---

**文档版本：** 2.0
**服务端版本：** 1.4.0
**适用系统：** 飞牛OS (fnOS)
