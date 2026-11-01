# 运行与部署

## 配置模式

| Profile | 数据库 | 初始数据 | 用途 |
|---|---|---|---|
| dataset（默认） | 内存 H2，MySQL 模式 | 77,169条 NUTRICONTROL 样本 | 快速运行、开发演示；重启重建 |
| mysql | MySQL 8.0.16+ / 8.4 | 设置 DATASET_SEED_ENABLED=true 才导入 | 持久化部署 |
| demo | 内存 H2 | 标记为 source=demo 的生成数据 | 接入端尚无数据时的独立开发样例 |

不要同时启用 dataset 和 demo 的种子开关。种子数据不会覆盖已有的同来源数据，正式项目请根据数据库迁移计划维护数据版本。

## Docker Compose

```sh
python scripts/init_env.py
docker compose config --quiet
docker compose up -d --build
docker compose logs -f api
```

默认地址 http://localhost:8088 。首次启动需要下载镜像、构建依赖并导入样本；看到 `Loaded 77169 attributed NUTRICONTROL historical sample records` 后可开始使用。

```sh
docker compose ps
curl http://localhost:8088/actuator/health
python scripts/verify_live.py --base-url http://localhost:8088
docker compose stop
docker compose start
```

Compose 的 MySQL 不向宿主机暴露3306端口；API 由 Nginx 同源代理；网页端口默认绑定127.0.0.1。数据位于命名卷中，普通 stop/start/down 不清空数据。不要在需要保留数据时使用 `down -v`。

## 阿里云或其他 Linux 服务器

1. 安装 Docker Engine 和 Compose 插件，把仓库部署到服务器。
2. 运行 `python3 scripts/init_env.py` 生成服务器自己的 `.env`；不要上传本机 `.env`。
3. 使用 `docker compose up -d --build` 启动；通过服务器本机8088端口验证健康与页面。
4. 用已有 HTTPS 入口反向代理到 `127.0.0.1:8088`，同时配置域名和证书。若计划直接提供服务，请明确配置公开入口及所需访问控制。
5. 备份 MySQL 数据卷；升级前先备份，再使用新增 Flyway migration 管理结构变更，不修改已执行的 migration。

云部署所需的服务器、域名、证书和外部入口由部署者配置。

## 连接已有 MySQL

创建独立数据库 `weather_station` 和仅有该数据库所需权限的应用用户。配置：

```text
SPRING_PROFILES_ACTIVE=mysql
DB_URL=jdbc:mysql://127.0.0.1:3306/weather_station?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true
DB_USER=weather
DB_PASSWORD=<数据库密码>
INGEST_API_KEY=<至少32位随机接入密钥>
DATASET_SEED_ENABLED=true
```

然后运行 `./mvnw spring-boot:run`，或 `./mvnw package` 后运行 `java -jar target/weather-station-1.0.0.jar`。Windows 使用 `mvnw.cmd`。

Flyway 自动建表；不要对已有的业务数据库启用 baseline-on-migrate 来强行跳过结构核对。数据接入端不应直接连接数据库，使用 [API](API.md) 即可。

## 故障检查

- 页面连接失败：确认5173（开发）或8088（Compose）已监听；开发前端默认代理到8080。
- 页面无数据：选择 ES-ALAVA 等数据站点，使用其最近记录日期；历史数据的最后时间早于今天是正常现象。
- 数据接入返回503：尚未配置 INGEST_API_KEY；返回401说明请求头不匹配。
- 数据接入返回409：同一站点、同一时刻已有不同记录；应检查来源和数据映射，不直接覆盖。
- 数据库连接失败：检查容器健康、DB_URL、用户名和密码；勿把测试数据库配置用于业务库。
- JVM 编译失败：检查 JAVA_HOME 指向 JDK 17+；首次运行需要可访问 Maven Central。
