# 青禾 · 气象站数据可视化系统

**Spring Boot + MyBatis + MySQL + Vue 3 + ECharts**。围绕温室环境数据的集中存储、历史追溯与趋势分析，提供可运行的前后端工程、真实开放数据样本、测试与部署配置。

![环境总览](docs/images/dashboard.jpg)

## 功能

- **环境总览**：三个西班牙温室站点切换，展示气温、湿度、太阳辐射、室外温度和风速，支持按日查看趋势。
- **历史查询**：按站点、日期分页检索；CSV 导出；保留数据来源与缺失值。
- **年度统计**：按指标生成12个月的均值、极值和有效观测数；无记录月份留空。
- **数据接入**：带密钥的批量 HTTP API，参数校验、唯一键、幂等重试与冲突批次回滚。
- **数据可追溯**：固定原始仓库版本、保留时间戳、记录处理步骤和 SHA-256；页面展示来源与许可。

本工程实现数据库及前后端业务，硬件采集端通过 HTTP 接口接入，不包含 Modbus 驱动。当前展示的是已归档的 NUTRICONTROL 数据，不是在线连接的温室设备。

## 快速运行：无需先安装 MySQL

环境：Java 17+、Node.js 22.12+、npm。Maven Wrapper 已包含在仓库，首次构建需联网下载依赖。

终端一：

```sh
cd backend
./mvnw spring-boot:run
# Windows PowerShell 使用 .\mvnw.cmd spring-boot:run
```

终端二：

```sh
cd frontend
npm ci
npm run dev
```

打开 **http://localhost:5173**。默认 `dataset` profile 使用内存 H2（MySQL 模式），自动加载随仓库提供的77,169条小时样本；重启后重新加载，适合快速体验。前端将 `/api` 代理到8080端口。

## Docker Compose：MySQL 持久化部署

需要 Docker Engine / Docker Desktop 及 Compose v2。

```sh
python scripts/init_env.py
docker compose up -d --build
```

打开 **http://localhost:8088**。`init_env.py` 生成随机数据库密码及数据接入密钥，不覆盖已有 `.env`；该文件已排除在 Git 之外。

Compose 启动 MySQL 8.4、Spring Boot API 和 Nginx。数据库数据保存在命名卷 `mysql_data` 中，重启不丢失；首次启动会导入公开历史样本。默认仅将网页端口绑定到本机回环地址。部署到阿里云等服务器时，建议使用域名、HTTPS 反向代理及受控入口，配置见 [部署说明](docs/DEPLOYMENT.md)。

## 数据集

使用 [SmartGreenHouses/NUTRICONTROL_datasets](https://github.com/SmartGreenHouses/NUTRICONTROL_datasets) 的商业温室实测数据，许可 **CC BY 4.0**。

| 站点 | 选取分区 | 默认样本行数 |
|---|---|---:|
| Álava | 单温室 | 19,628 |
| Navarra | C0 | 40,511 |
| Cartagena | C0 | 17,030 |

默认样本选取每小时整点的原始记录，关联对应时刻的室外气象；保留原始日期和缺失值，不插值、不平移到今天。**太阳辐射单位是 W/m²，风速单位是 km/h**。数据源未注明时区，系统不推断其时区。

完整作者、引用、字段映射、过滤规则和全量15分钟数据导入方法见 [数据来源说明](docs/DATA_SOURCES.md)。所有月度统计基于已导入样本，不代表完整原始数据集。

## 架构与目录

```mermaid
flowchart LR
  A[NUTRICONTROL CSV] --> B[Python 规范化与校验]
  B --> C[MySQL / H2]
  D[外部采集程序] -->|接入密钥 + HTTP| E[Spring Boot / MyBatis]
  E <--> C
  E -->|查询 / 统计 / CSV| F[Vue 3 / Element Plus / ECharts]
```

```text
backend/         Java 17 / Spring Boot 3.5 / MyBatis / Flyway / 接口测试
frontend/        Vue 3 / TypeScript / Element Plus / ECharts / Vite
scripts/         数据下载、规范化、批量导入与运行验收（Python 标准库）
docs/            API、数据来源、部署、验证说明与实际页面截图
compose.yml      MySQL + API + Nginx
.github/         GitHub Actions：H2、MySQL、前端和数据校验
```

关键设计：

- 观测表使用 `(station_code, observed_at)` 唯一索引；日期查询采用半开区间，便于走联合索引。
- 统计指标来自固定枚举与 SQL 分支，禁止客户端注入列名。
- 数值缺失保留为 null；聚合分别统计每个指标有效值，前端区分零与缺失。
- 批次入库使用事务；相同观测可重试，冲突回滚；并发重复请求由唯一键和锁定读保证一致。
- 前端取消过期查询并处理异常与空数据；图表通过 ResizeObserver 适配容器并在卸载时释放实例。

## 开发与验证

```sh
cd backend
./mvnw verify

cd ../frontend
npm test
npm run build

cd ..
python -m unittest discover -s scripts -p 'test_*.py' -v
python scripts/verify_live.py
```

`verify_live.py` 在应用运行时将 API 的三个站点查询、月度均值、极值及有效点数与随仓库提供的原始规范化样本逐项比较。MySQL 集成测试可设置 `TEST_DATABASE_URL`、`TEST_DATABASE_USER`、`TEST_DATABASE_PASSWORD` 后运行后端测试，必须指向单独的测试库；测试会清空该库的 observation 表。

具体验证结果见 [验证记录](docs/VERIFICATION.md)。接口参数、响应与接入样例见 [API 文档](docs/API.md)。

## 许可

自编软件采用 [MIT](LICENSE)；NUTRICONTROL 原始数据与本项目衍生样本采用 **CC BY 4.0**。Maven Wrapper 脚本保留其 Apache-2.0 文件头与原始许可声明。
