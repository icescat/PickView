# Docker权限问题解决

## 问题现象
```
permission denied while trying to connect to the Docker daemon socket
```

## 原因
当前用户（icescat）没有Docker权限，需要使用root用户或docker组用户。

## 解决方案

### 方案1：使用sudo（推荐，立即生效）

```bash
# 所有docker命令前加sudo
sudo docker logs bilipick-server
sudo docker ps
sudo docker-compose logs
```

### 方案2：将用户加入docker组（永久解决，需重新登录）

```bash
# 1. 将当前用户加入docker组
sudo usermod -aG docker icescat

# 2. 退出并重新登录SSH
exit
# 重新连接SSH

# 3. 验证
docker ps
```

### 方案3：切换到root用户

```bash
# 切换到root
su -
# 或
sudo -i

# 然后执行docker命令
docker logs bilipick-server
```

---

## 推荐操作

**现在立即执行（用方案1）：**

```bash
# 查看容器日志
sudo docker logs bilipick-server

# 查看容器状态
sudo docker ps | grep bilipick

# 查看完整日志
sudo docker logs --tail 50 bilipick-server
```

**如果容器确实退出了，用sudo重启：**

```bash
cd /vol1/1000/docker/bilipick

# 停止并重启
sudo docker-compose down
sudo docker-compose up -d

# 查看日志
sudo docker logs -f bilipick-server
```

---

## 验证修复

执行以下命令，确认服务正常运行：

```bash
# 1. 检查容器状态
sudo docker ps | grep bilipick

# 应该显示：
# bilipick-server   Up X minutes   0.0.0.0:9525->9525/tcp

# 2. 测试本地访问
curl http://localhost:9525

# 应该返回JSON：
# {"status":"running","service":"B站精选NAS服务端","version":"1.0.0"}
```

---

## 长期建议

建议将用户加入docker组，避免每次都输入sudo：

```bash
sudo usermod -aG docker icescat
```

然后**退出当前SSH会话，重新连接**，即可直接使用docker命令。
