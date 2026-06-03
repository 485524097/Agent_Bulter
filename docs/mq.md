# CentOS 7 虚拟机安装 RabbitMQ 踩坑记录：从 RPM 安装失败到 Docker 部署成功

## 一、背景

最近在开发自己的后端项目时，计划引入 RabbitMQ，用于后续实现异步通知、预算预警、消息解耦等功能。由于本地没有安装 RabbitMQ，所以我打算先在 CentOS 虚拟机中部署一个 RabbitMQ 环境，供 Spring Boot 项目连接测试。

一开始我的思路是直接下载 RabbitMQ 和 Erlang 的 RPM 包进行安装，后来发现 CentOS 7 环境比较老，直接安装新版 RabbitMQ 会遇到很多兼容问题。最终选择通过 Docker 方式部署 RabbitMQ。

本次环境大致如下：

```text
系统：CentOS 7.6.1810
虚拟机 IP：192.168.59.129
目标服务：RabbitMQ 3.13-management
部署方式：Docker
管理端口：15672
通信端口：5672
```

------

## 二、最初的问题：RPM 安装失败

一开始下载了两个 RPM 文件：

```text
erlang-28.4.3-1.el7.x86_64.rpm
rabbitmq-server-4.3.1-1.el8.noarch.rpm
```

执行安装 Erlang 时出现：

```bash
rpm -ivh erlang-28.4.3-1.el7.x86_64.rpm
```

报错：

```text
error: can't create transaction lock on /var/lib/rpm/.rpm.lock (Permission denied)
```

这个问题本身不是 Erlang 或 RabbitMQ 的问题，而是因为当前用户没有 root 权限。RPM 安装需要操作系统级软件包数据库，普通用户无法写入 `/var/lib/rpm/.rpm.lock`。

解决方式是切换到 root 用户：

```bash
su -
```

或者使用：

```bash
sudo rpm -ivh erlang-28.4.3-1.el7.x86_64.rpm
```

不过后面发现，更大的问题不是权限，而是版本不匹配。

------

## 三、版本不匹配问题

我下载的 Erlang 是：

```text
erlang-28.4.3-1.el7.x86_64.rpm
```

而 RabbitMQ 是：

```text
rabbitmq-server-4.3.1-1.el8.noarch.rpm
```

这里的 `el7` 和 `el8` 分别代表不同系统环境：

```text
el7：适用于 CentOS 7 / RHEL 7
el8：适用于 CentOS 8 / RHEL 8 / Rocky 8 / Alma 8
```

虽然 RabbitMQ 的 RPM 包是 `noarch`，但它依赖的系统环境仍然和发行版版本有关。CentOS 7 系统比较老，直接安装 `el8` 的 RabbitMQ 包并不合适。

同时，RabbitMQ 新版本对 Erlang 和 OpenSSL 等依赖也有要求。CentOS 7 的基础库较旧，安装新版 RabbitMQ 容易继续遇到依赖问题。

因此，最终放弃了直接 RPM 安装方式，改为使用 Docker 部署 RabbitMQ。

------

## 四、安装 Docker 时遇到的问题

### 1. Docker 命令不存在

执行：

```bash
docker run ...
```

提示命令不存在，说明系统中还没有安装 Docker。

于是开始安装 Docker。

------

### 2. yum 被 PackageKit 占用

执行：

```bash
yum install -y yum-utils device-mapper-persistent-data lvm2
```

出现：

```text
Existing lock /var/run/yum.pid: another copy is running as pid xxxx.
The other application is: PackageKit
```

这是因为系统后台的 PackageKit 服务正在占用 yum 锁，导致当前 yum 命令无法执行。

解决方式：

```bash
systemctl stop packagekit
rm -f /var/run/yum.pid
```

需要注意的是，不要随便杀错进程。当时我误杀了父进程，导致 root 终端退出，后续命令又变成普通用户执行，出现了：

```text
You need to be root to perform this command.
```

所以安装系统组件时，一定要确认当前是 root 用户。

------

### 3. CentOS 7.6 yum 源失效

继续执行 yum 时，又出现大量 404：

```text
http://mirror.lzu.edu.cn/centos/7.6.1810/os/x86_64/repodata/repomd.xml: HTTP Error 404 - Not Found
Cannot find a valid baseurl for repo: base/7/x86_64
```

原因是 CentOS 7.6.1810 已经过旧，普通镜像站不再提供这个版本的常规源，需要切换到 CentOS Vault 归档源。

解决方式是备份原来的 repo 文件，并重新配置 Vault 源：

```bash
cd /etc/yum.repos.d/

mkdir -p bak
mv *.repo bak/ 2>/dev/null

cat > CentOS-Vault-7.6.1810.repo <<'EOF'
[base]
name=CentOS-7.6.1810 - Base
baseurl=http://vault.centos.org/7.6.1810/os/$basearch/
enabled=1
gpgcheck=0

[updates]
name=CentOS-7.6.1810 - Updates
baseurl=http://vault.centos.org/7.6.1810/updates/$basearch/
enabled=1
gpgcheck=0

[extras]
name=CentOS-7.6.1810 - Extras
baseurl=http://vault.centos.org/7.6.1810/extras/$basearch/
enabled=1
gpgcheck=0
EOF
```

然后清理缓存：

```bash
yum clean all
yum makecache
```

------

## 五、Docker 安装和启动问题

一开始通过系统源安装了 CentOS 自带的 Docker：

```bash
yum install -y docker
```

安装完成后查看版本：

```bash
docker -v
```

显示：

```text
Docker version 1.13.1
```

说明 Docker 命令安装成功。

但是启动 Docker 时失败：

```bash
systemctl start docker
```

报错：

```text
Job for docker.service failed because the control process exited with error code.
```

查看日志：

```bash
journalctl -u docker.service -n 100 --no-pager
```

发现关键错误：

```text
Error starting daemon: SELinux is not supported with the overlay2 graph driver on this kernel.
Either boot into a newer kernel or disable selinux in docker (--selinux-enabled=false)
```

原因是 CentOS 7.6 的内核较旧，Docker 使用 overlay2 存储驱动时和 SELinux 存在兼容问题。

解决方式是修改 Docker 配置文件：

```bash
sed -i 's/--selinux-enabled//g' /etc/sysconfig/docker
```

然后重启 Docker：

```bash
systemctl daemon-reload
systemctl restart docker
```

此后 Docker 启动成功。

------

## 六、拉取 RabbitMQ 镜像失败

Docker 启动后，执行：

```bash
docker pull rabbitmq:3.13-management
```

报错：

```text
Get https://registry-1.docker.io/v2/: net/http: request canceled while waiting for connection
```

这是因为虚拟机访问 Docker Hub 超时。

于是改为使用镜像源：

```bash
docker pull docker.m.daocloud.io/library/rabbitmq:3.13-management
```

但在 Docker 1.13.1 下又遇到：

```text
missing signature key
```

这个问题和 CentOS 自带 Docker 版本太旧有关，拉取现代镜像时容易因为签名校验失败。

------

## 七、升级 Docker 过程中遇到的问题

为了避免 Docker 1.13.1 的兼容问题，尝试升级 Docker CE。

执行：

```bash
yum list docker-ce --showduplicates | sort -r
```

可以看到 Docker CE 源中有很多版本。

尝试安装：

```bash
yum install -y docker-ce-20.10.24-3.el7 docker-ce-cli-20.10.24-3.el7 containerd.io
```

但出现依赖问题：

```text
Requires: slirp4netns >= 0.4
Requires: fuse-overlayfs >= 0.7
```

原因是 yum 自动拉取了较新的 `docker-ce-rootless-extras`，而 CentOS 7.6 默认源中缺少对应依赖。

后来尝试安装更低版本：

```bash
yum install -y docker-ce-19.03.15-3.el7 docker-ce-cli-19.03.15-3.el7 containerd.io
```

过程中又遇到 `containerd.io` 下载失败：

```text
containerd.io-1.6.33-3.1.el7.x86_64.rpm: TCP connection reset by peer
No more mirrors to try.
```

这个问题不是依赖问题，而是 Docker 官方源下载不稳定。

解决方式是把 Docker 官方源切换为国内镜像源：

```bash
cd /etc/yum.repos.d

cp docker-ce.repo docker-ce.repo.bak

sed -i 's#https://download.docker.com/linux/centos#https://mirrors.aliyun.com/docker-ce/linux/centos#g' docker-ce.repo

sed -i 's#gpgcheck=1#gpgcheck=0#g' docker-ce.repo
```

然后重新生成缓存：

```bash
yum clean all
yum makecache fast
```

再次安装 Docker CE 后成功。

------

## 八、RabbitMQ 镜像拉取成功，但容器启动失败

Docker 升级和镜像源配置完成后，重新拉取 RabbitMQ：

```bash
docker pull docker.m.daocloud.io/library/rabbitmq:3.13-management
```

这次成功：

```text
Status: Downloaded newer image for docker.m.daocloud.io/library/rabbitmq:3.13-management
```

然后给镜像打标签：

```bash
docker tag docker.m.daocloud.io/library/rabbitmq:3.13-management rabbitmq:3.13-management
```

启动 RabbitMQ 容器：

```bash
docker run -d \
  --name rabbitmq \
  --hostname rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3.13-management
```

第一次启动时出现：

```text
error mounting "/var/lib/docker/containers/.../resolv.conf" to rootfs at "/etc/resolv.conf":
possibly malicious path detected
```

这和 Docker 的 overlay2 存储驱动、CentOS 7 的 XFS 文件系统兼容有关。之前日志中也提示过：

```text
xfs filesystem is formatted without d_type support
```

这说明当前文件系统对 overlay2 支持不完整。

解决方式是把 Docker 存储驱动改为 `devicemapper`，并清空旧的 Docker 数据目录：

```bash
docker rm -f rabbitmq
systemctl stop docker
rm -rf /var/lib/docker
```

配置 Docker：

```bash
mkdir -p /etc/docker

cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://docker.m.daocloud.io"],
  "storage-driver": "devicemapper"
}
EOF
```

重启 Docker：

```bash
systemctl daemon-reload
systemctl start docker
docker info | grep -E "Storage Driver|Docker Root Dir"
```

确认看到：

```text
Storage Driver: devicemapper
```

然后重新拉取 RabbitMQ 镜像并启动容器。

------

## 九、容器名冲突问题

重新运行 RabbitMQ 时，又遇到：

```text
Conflict. The container name "/rabbitmq" is already in use
```

这是因为之前失败的容器虽然没有正常运行，但容器名称已经被占用。

解决方式：

```bash
docker rm -f rabbitmq
```

然后重新执行 `docker run`。

------

## 十、最终启动成功

最终执行：

```bash
docker run -d \
  --name rabbitmq \
  --hostname rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3.13-management
```

返回容器 ID：

```text
f3b0f14b98a0db0d66ddba1babe411c912e282f696a8fd567af6d36e109e0e24
```

说明 RabbitMQ 容器启动成功。

查看容器：

```bash
docker ps
```

查看日志：

```bash
docker logs -f rabbitmq
```

如果日志中看到：

```text
Server startup complete
```

说明 RabbitMQ 已经正常启动。

------

## 十一、访问 RabbitMQ 管理页面

查看虚拟机 IP：

```bash
ip addr
```

我的虚拟机网卡地址是：

```text
192.168.59.129
```

所以 RabbitMQ 管理页面地址为：

```text
http://192.168.59.129:15672
```

默认账号密码：

```text
guest / guest
```

如果浏览器无法访问，可以开放防火墙端口：

```bash
firewall-cmd --add-port=5672/tcp --permanent
firewall-cmd --add-port=15672/tcp --permanent
firewall-cmd --reload
```

------

## 十二、Spring Boot 连接配置

RabbitMQ 启动成功后，Spring Boot 项目中可以这样配置：

```yaml
spring:
  rabbitmq:
    host: 192.168.59.129
    port: 5672
    username: guest
    password: guest
    virtual-host: /
```

其中：

```text
5672：RabbitMQ 消息通信端口，Spring Boot 连接使用
15672：RabbitMQ Web 管理控制台端口，浏览器访问使用
```

------

## 十三、总结

这次安装 RabbitMQ 遇到的问题主要集中在以下几个方面：

```text
1. 普通用户没有 rpm 安装权限
2. Erlang 和 RabbitMQ 的 el7 / el8 版本不匹配
3. CentOS 7.6 yum 源失效，需要切换 Vault 源
4. yum 被 PackageKit 占用
5. Docker 1.13.1 版本过旧，拉取新镜像容易出现签名问题
6. Docker 官方源下载不稳定，需要切换国内 Docker 源
7. CentOS 7 的 XFS 文件系统对 overlay2 支持不完整
8. 失败容器残留导致容器名冲突
```

最终解决思路是：

```text
直接放弃 RabbitMQ RPM 安装
改用 Docker 部署 RabbitMQ
修复 CentOS yum 源
安装可用版本 Docker
切换 Docker 镜像源
处理 Docker 存储驱动问题
重新拉取并启动 RabbitMQ 容器
```

这次经历也说明，在旧版本 CentOS 上部署新版中间件时，最大的坑往往不是中间件本身，而是系统版本、依赖库、yum 源、Docker 版本和文件系统兼容性。