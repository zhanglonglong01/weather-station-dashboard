# HTTP API

Base URL：`http://localhost:8080/api/v1`。响应为 JSON；错误为 `application/problem+json`，含 `status` 和中文 `detail`。

前端可使用演示场景别名；API 与 CSV 中的站点代码及来源不受影响，对应关系见 [场景说明](SCENARIO.md)。

## 读取接口

| 方法与路径 | 参数 | 行为 |
|---|---|---|
| GET `/stations` | 无 | 站点资料、最近观测时间、来源；历史数据不标为在线设备 |
| GET `/overview` | stationCode | 最新观测；当日记录数与气温统计；空站点 latest 为 null |
| GET `/observations` | stationCode, date, page=1, size=20 | 按日倒序分页，返回 items/total/page/size |
| GET `/trends` | stationCode, date, metric=temperature | 按15分钟桶聚合，返回桶均值与有效点数 |
| GET `/statistics/monthly` | stationCode, year, metric=temperature | 返回12个月的平均、最小、最大值和有效观测数 |
| GET `/observations/export` | stationCode, date | UTF-8 BOM CSV，带来源；空值保留为空；最多50,000条 |
| GET `/dataset` | 无 | 来源、许可、固定版本、处理方法、样本行数及 SHA-256 |

参数约束：

- `date` 为 YYYY-MM-DD，1970—2100年；日期查询采用 `[当天00:00, 次日00:00)`，不在时间列上套 DATE 函数。
- `date` 省略时使用服务器上海时间的今天；**前端会显式传入所选站点最近有记录的日期**，不会将历史数据伪装成实时数据。
- `year` 为1970—2100，`page >= 1`，`1 <= size <= 100`。
- `metric` 仅接受 `temperature`、`humidity`、`solarRadiation`、`outdoorTemperature`、`windSpeed`。动态 SQL 使用固定分支，不接受客户端传入列名。
- 数值缺失返回 JSON null；聚合 AVG/MIN/MAX 忽略 null，count 统计该指标有效值数。无数据月份返回 null 和 count=0。
- 观测 `observedAt` 保留数据源本地时刻，不携带推断的时区。`serverTime` 单独表示服务器 Asia/Shanghai 的本地时刻。

```sh
curl 'http://localhost:8080/api/v1/observations?stationCode=ES-ALAVA&date=2026-02-24&page=1&size=20'
curl 'http://localhost:8080/api/v1/statistics/monthly?stationCode=ES-ALAVA&year=2025&metric=temperature'
```

## 数据接入

`POST /observations` 需要请求头 `X-Ingest-Key`。后端设置 `INGEST_API_KEY`，至少32个字符；不配置时接入接口返回503。密钥不会下发给前端。

```json
{
  "stationCode": "GH-001",
  "source": "device",
  "readings": [
    {
      "observedAt": "2026-10-29T09:00:00",
      "temperature": 25.3,
      "humidity": 68.2,
      "solarRadiation": 320.5,
      "outdoorTemperature": 21.4,
      "windSpeed": null
    }
  ]
}
```

返回 `{"processed":1}`，表示该批已处理的记录数，包含相同观测的重试，不表示新增数。

- 一个批次1—500条，须指定已存在的站点。站点资料由 Flyway 初始化；新增站点请增加 SQL migration。
- 至少一个有效指标；值最多两位小数。气温/室外气温 -60～80℃、湿度0～100%、辐射0～2000 W/m²、风速0～300 km/h。
- 时间精确到秒，年份不早于1970，不超过服务器当前时间5分钟；调用方负责提供可解释的数据源时间。
- `stationCode` 限定大写字母开头，后续大写字母、数字、下划线或横杠；source 为小写字母开头的固定标签，最长32字符。不要在来源字段传个人信息或密钥。
- `(station_code, observed_at)` 唯一键。同时间、同数值、同来源的重复提交无副作用；不同值或来源返回409，整个批次回滚。
- 插入后使用锁定读验证内容，避免 MySQL REPEATABLE READ 下并发重试读取旧快照；并发幂等行为有集成测试。

硬件采集程序可通过此接口接入。系统**不实现 Modbus 采集、ESP32 驱动或设备控制**。

## 健康检查

`GET /actuator/health` 返回 `{"status":"UP"}`；liveness/readiness 端点由 Spring Boot 提供。详细数据库连接信息不向客户端公开。

常见状态：400 参数错误；401 接入密钥错误；404 未知站点；409 同时间观测冲突；422 导出超限；503 接入未配置。

读取接口面向公开历史数据，无登录要求。若接入非公开的实际生产数据，请通过认证网关和 HTTPS 限制访问。
