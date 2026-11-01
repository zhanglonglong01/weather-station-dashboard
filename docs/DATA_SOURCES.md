# 数据来源与处理说明

## NUTRICONTROL

数据集：**Multi-Site Greenhouse Climate Dataset (Spain)**。

- 原仓库：https://github.com/SmartGreenHouses/NUTRICONTROL_datasets
- 固定版本：`6dece0d2c7483982610e4d2de225db3c4649b935`
- DOI：https://doi.org/10.5281/zenodo.21037153
- 作者：Jaime Carlavilla-Lázaro、Virginia C. Sánchez、Carlos T. Calafate、Carlos García Sáez、Juan Morales-García、José M. Cecilia。
- 许可：原仓库 README 和 `.zenodo.json` 均声明 **CC BY 4.0**，许可条款：https://creativecommons.org/licenses/by/4.0/ 。数据及其衍生样本保留此许可；本项目软件的 MIT 许可不覆盖这些数据。

推荐引用：Carlavilla-Lázaro, J., Sánchez, V. C., Calafate, C. T., García Sáez, C., Morales-García, J., & Cecilia, J. M. (2026). *Multi-Site Greenhouse Climate Dataset (Spain)*. https://doi.org/10.5281/zenodo.21037153 。引用原始研究成果时，请同时遵循原仓库对关联论文的引用要求。

## 本项目使用的样本

默认随仓库分发约 1 MB 的 gzip 样本，共 **77,169 条**。不是完整原始数据集，也不是本项目自行采集的数据。

| 站点 | 分区 | 样本记录数 | 实际保留的时间范围 |
|---|---|---:|---|
| Álava | 单温室 | 19,628 | 2023-11-07 12:00 至 2026-02-25 08:00 |
| Navarra | C0 | 40,511 | 2021-05-11 16:00 至 2026-03-03 14:00 |
| Cartagena | C0 | 17,030 | 2024-03-06 18:00 至 2026-03-03 17:00 |

以上范围由 CSV 实际记录计算，而不是直接复制原仓库 README 的汇总表。各文件 SHA-256、筛选数量、缺失数量及样本校验值见 `backend/src/main/resources/dataset/manifest.json`，也可通过 `GET /api/v1/dataset` 查看。

处理步骤：

1. 使用 `clean_data` 中三个室内环境文件及 Navarra、Cartagena 两个室外气象文件。
2. 对 Navarra、Cartagena 按**完全相同的 dateTime** 左关联室外气象；不使用相邻时刻的数据填补。
3. 默认仅保留每小时整点的原始观测行，**不是小时平均**。源文件本身为15分钟分辨率。
4. 选取室内干球温度、室内相对湿度、室外平均太阳辐射、室外干球温度、室外平均风速。数值按 HALF_UP 四舍五入到两位小数。
5. 空白、非有限数值、超出系统物理范围的单元格保留为空；五个指标均为空的行跳过。`missingOrRejectedCells` 合并统计缺失及被拒绝的单元格。
6. 保留原始日期和时刻。原数据未注明时区，不推断为 UTC、上海或马德里时间，不进行夏令时转换。
7. 不插值、不随机补数、不将历史日期平移到今天。数据库 `NULL`、图表间断、表格 `—` 和 CSV 空单元格均表示缺失，而真实的零保留为零。

| 系统字段 | 原始字段模式 | 单位 |
|---|---|---|
| temperature | DB_TEMP_INT_* | ℃ |
| humidity | REL_HUM_INT_* | % |
| solarRadiation | RADIATION_MEAN_* | W/m²（不是 lx） |
| outdoorTemperature | DB_TEMP_EXT_* | ℃ |
| windSpeed | WIND_VELOCITY_MEAN_* | km/h（不是 m/s） |

月度统计只针对**已导入的有效样本**。不以缺失值补零；每个指标独立计算有效观测数。默认小时抽样统计不能被描述为完整15分钟原始数据的统计。

## 复现或使用完整15分钟数据

```sh
# Python 3.10+，无需额外安装 Python 依赖
python scripts/prepare_dataset.py --download
python -m unittest discover -s scripts -p 'test_*.py' -v

# 在单独目录生成完整15分钟规范化文件；不覆盖随仓库发布的小时样本
python scripts/prepare_dataset.py --sample-minutes 15 --output-dir data/imports/full-normalized
python scripts/import_observations.py data/imports/full-normalized/observations.csv.gz
```

导入已有同来源、同站点、同时间、同数值的行是幂等操作；同时间不同数值返回409。完整导入按500条一批提交，某批失败不会回滚之前成功的批次。原始大文件保存在忽略的 `data/imports/`，不提交到 GitHub。

之前考察的 `ghaith-jbali/Smart-Greenhouse-IoT-System` 中4个 CSV 均为空，**未从该仓库使用或复制观测数据、代码**。
