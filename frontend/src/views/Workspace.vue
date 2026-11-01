<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import {
  RefreshCw,
  Download,
  ArrowUpRight,
  Thermometer,
  Droplets,
  Sun,
  Wind,
  CloudSun,
  MapPin,
  Database,
  Leaf,
  CalendarDays,
  Radio,
  ShieldCheck,
  Code,
  FileText,
} from "lucide-vue-next";
import ClimateChart from "../components/ClimateChart.vue";
import {
  request,
  query,
  metrics,
  valueText,
  timeText,
  dateOf,
  type StationStatus,
  type Overview,
  type Page,
  type Trends,
  type Statistics,
  type Metric,
} from "../api";
const props = defineProps<{ view: string }>();
const titles: Record<string, [string, string, string]> = {
  overview: [
    "环境总览",
    "把温室的每一次变化，看得更清楚。",
    "ENVIRONMENT OVERVIEW",
  ],
  history: [
    "历史数据",
    "按站点与日期追溯每一条观测记录。",
    "OBSERVATION HISTORY",
  ],
  statistics: [
    "年度统计",
    "从月度变化中，发现环境的季节规律。",
    "YEARLY ANALYTICS",
  ],
  stations: [
    "监测站点",
    "跨越不同气候区的温室环境记录。",
    "MONITORING STATIONS",
  ],
  sources: [
    "数据与接入",
    "透明的数据来源，清晰的系统边界。",
    "DATA & INTEGRATION",
  ],
};
const title = computed(() => titles[props.view] || titles.overview!);
const stations = ref<StationStatus[]>([]);
const stationCode = ref("ES-ALAVA");
const date = ref("");
const year = ref(2025);
const metric = ref<Metric>("temperature");
const page = ref(1);
const size = ref(20);
const overview = ref<Overview>();
const history = ref<Page>();
const trend = ref<Trends>();
const stats = ref<Statistics>();
const loading = ref(false);
const error = ref("");
const downloadBusy = ref(false);
let controller: AbortController | undefined;
let version = 0;
const selected = computed(() =>
  stations.value.find((s) => s.station.code === stationCode.value),
);
const selectedMetric = computed(() =>
  metrics.find((m) => m.key === metric.value)!,
);
const latest = computed(() =>
  overview.value?.station.code === stationCode.value
    ? overview.value.latest
    : null,
);
const sourceLabel = computed(() =>
  latest.value?.source === "nutricontrol"
    ? "NUTRICONTROL · 历史数据"
    : latest.value?.source === "demo"
      ? "演示数据"
      : latest.value?.source === "device"
        ? "设备观测"
        : "暂无观测数据",
);
const icons = [Thermometer, Droplets, Sun, CloudSun, Wind];
const points = computed(() => {
  // Explicit missing 15-minute slots preserve outages as gaps instead of connecting across them.
  const values = new Map(
    (trend.value?.points || []).map((p) => [p.time.slice(11, 16), p.value]),
  );
  const perHour = (trend.value?.points || []).some(
    (p) => p.time.slice(14, 16) !== "00",
  )
    ? 4
    : 1;
  return Array.from({ length: 24 * perHour }, (_, i) => {
    const time = `${String(Math.floor(i / perHour)).padStart(2, "0")}:${String((i % perHour) * (60 / perHour)).padStart(2, "0")}`;
    return { time, value: values.get(time) ?? null };
  });
});
const yearCount = computed(
  () => stats.value?.months.reduce((sum, m) => sum + m.count, 0) || 0,
);
async function load(resetDate = false) {
  const current = ++version;
  controller?.abort();
  controller = new AbortController();
  const signal = controller.signal;
  loading.value = true;
  error.value = "";
  try {
    const nextStations = await request<StationStatus[]>(
      "/stations",
      {},
      signal,
    );
    const nextOverview = await request<Overview>(
      "/overview",
      { stationCode: stationCode.value },
      signal,
    );
    if (current !== version) return;
    stations.value = nextStations;
    overview.value = nextOverview;
    if (resetDate || !date.value) {
      date.value = nextOverview.latest
        ? dateOf(nextOverview.latest.observedAt)
        : dateOf(nextOverview.serverTime);
      year.value = Number(date.value.slice(0, 4));
    }
    const params = { stationCode: stationCode.value, date: date.value };
    if (props.view === "overview")
      trend.value = await request<Trends>(
        "/trends",
        { ...params, metric: metric.value },
        signal,
      );
    if (props.view === "history")
      history.value = await request<Page>(
        "/observations",
        { ...params, page: page.value, size: size.value },
        signal,
      );
    if (props.view === "statistics")
      stats.value = await request<Statistics>(
        "/statistics/monthly",
        {
          stationCode: stationCode.value,
          year: year.value,
          metric: metric.value,
        },
        signal,
      );
  } catch (e) {
    if (
      current === version &&
      !(e instanceof DOMException && e.name === "AbortError")
    )
      error.value = e instanceof Error ? e.message : "加载失败，请检查后端服务";
  } finally {
    if (current === version) loading.value = false;
  }
}
function stationChanged() {
  page.value = 1;
  date.value = "";
  void load(true);
}
function filterChanged() {
  page.value = 1;
  void load();
}
function yearChanged(event: Event) {
  const value = Number((event.target as HTMLInputElement).value);
  if (!Number.isInteger(value) || value < 1970 || value > 2100) return;
  year.value = value;
  filterChanged();
}
async function download() {
  downloadBusy.value = true;
  try {
    const response = await fetch(
      `/api/v1/observations/export?${query({ stationCode: stationCode.value, date: date.value })}`,
    );
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.detail || "导出失败");
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = `${stationCode.value}-${date.value}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "导出失败";
  } finally {
    downloadBusy.value = false;
  }
}
watch(
  () => props.view,
  () => {
    page.value = 1;
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  version++;
  controller?.abort();
});
</script>
<template>
  <main class="workspace" :aria-busy="loading">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ title[2] }}</div>
        <h1>{{ title[0] }}<span class="title-dot">.</span></h1>
        <p>{{ title[1] }}</p>
      </div>
      <button class="refresh-button" :disabled="loading" @click="load()">
        <RefreshCw :size="15" :class="{ spinning: loading }" /> 刷新数据
      </button>
    </div>
    <div v-if="error" class="error-box" role="alert">
      {{ error }}<button @click="load()">重新加载</button>
    </div>
    <div v-if="view !== 'sources' && view !== 'stations'" class="filter-row">
      <div class="filter-group">
        <MapPin :size="16" /><label for="station">监测站点</label
        ><el-select
          id="station"
          v-model="stationCode"
          aria-label="选择监测站点"
          style="width: 190px"
          @change="stationChanged"
          ><el-option
            v-for="s in stations"
            :key="s.station.code"
            :label="s.station.name"
            :value="s.station.code"
        /></el-select>
      </div>
      <div v-if="view !== 'statistics'" class="filter-group">
        <CalendarDays :size="16" /><label>观测日期</label
        ><el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          :clearable="false"
          aria-label="选择观测日期"
          style="width: 155px"
          @change="filterChanged"
        />
      </div>
      <div v-else class="filter-group">
        <CalendarDays :size="16" /><label>统计年份</label
        ><input
          class="year-input"
          type="number"
          :value="year"
          min="1970"
          max="2100"
          step="1"
          aria-label="统计年份"
          @input="yearChanged"
        />
      </div>
      <span class="data-badge"><span></span>{{ sourceLabel }}</span>
    </div>
    <template v-if="view === 'overview'">
      <section class="intro-strip">
        <div>
          <span class="intro-icon"><Leaf :size="21" /></span
          ><span
            ><strong>{{ selected?.station.location || "正在读取站点" }}</strong
            ><small>{{
              latest?.source === "nutricontrol"
                ? "保留原始观测时间 · 缺失值不填充 · 每小时抽样"
                : "按数据源时间展示 · 缺失值不填充"
            }}</small></span
          >
        </div>
        <span class="last-record"
          >最近记录 <strong>{{ timeText(latest?.observedAt) }}</strong></span
        >
      </section>
      <section class="metric-grid" aria-label="最近观测指标">
        <article
          v-for="(m, i) in metrics"
          :key="m.key"
          class="metric-card"
          :class="{ selected: metric === m.key }"
          tabindex="0"
          role="button"
          :aria-label="`查看${m.name}趋势`"
          @click="
            metric = m.key;
            load();
          "
          @keydown.enter="
            metric = m.key;
            load();
          "
          @keydown.space.prevent="
            metric = m.key;
            load();
          "
        >
          <div class="metric-label">
            {{ m.name
            }}<component
              :is="icons[i]"
              :size="20"
              :style="{ color: m.color }"
            />
          </div>
          <div class="metric-value">
            {{ valueText(latest?.[m.key]) }}<small>{{ m.unit }}</small>
          </div>
          <div class="metric-note">{{ m.note }}</div>
        </article>
      </section>
      <section class="panel trend-panel">
        <div class="panel-heading">
          <div>
            <h2>环境变化趋势</h2>
            <p>{{ date }} · 15分钟分桶 · 原始记录时间（时区未注明）</p>
          </div>
          <el-select
            v-model="metric"
            aria-label="趋势指标"
            style="width: 150px"
            @change="load()"
            ><el-option
              v-for="m in metrics"
              :key="m.key"
              :label="m.name"
              :value="m.key"
          /></el-select>
        </div>
        <div
          v-if="!loading && !trend?.points.some((p) => p.value !== null)"
          class="empty-state"
        >
          <Database :size="32" /><strong>该日期暂无有效观测值</strong
          ><span>请选择其他日期或指标。</span>
        </div>
        <ClimateChart
          v-else
          :labels="points.map((p) => p.time)"
          :values="points.map((p) => p.value)"
          :name="selectedMetric.name"
          :unit="selectedMetric.unit"
          :color="selectedMetric.color"
        />
        <div class="chart-footnote">
          <span
            class="legend-line"
            :style="{ background: selectedMetric.color }"
          ></span
          >{{ selectedMetric.name
          }}<span>间断表示缺失或未抽样，不等于零。</span>
        </div>
      </section>
      <section class="bottom-grid">
        <div class="panel explain-card">
          <div class="section-kicker">DATA PROVENANCE</div>
          <h2>从原始数据，到清晰的洞察</h2>
          <p>
            已接入西班牙商业温室公开数据集。本站展示真实历史样本，保留缺失记录与数据来源，可按日追溯、按月分析。
          </p>
          <RouterLink to="/sources"
            >查看数据来源与处理说明 <ArrowUpRight :size="16"
          /></RouterLink>
        </div>
        <div class="panel station-summary">
          <div class="panel-heading">
            <h2>站点记录概况</h2>
            <RouterLink to="/stations">全部站点 ↗</RouterLink>
          </div>
          <div
            v-for="s in stations.filter((s) => s.lastObservedAt).slice(0, 3)"
            :key="s.station.code"
            class="station-row"
          >
            <span class="station-pin"><MapPin :size="17" /></span>
            <div>
              <strong>{{ s.station.name }}</strong
              ><small>{{ s.station.location }}</small>
            </div>
            <span class="muted-pill">历史归档</span>
          </div>
        </div>
      </section>
    </template>
    <section v-else-if="view === 'history'" class="panel">
      <div class="panel-heading">
        <div>
          <h2>观测记录</h2>
          <p>
            共 {{ history?.total.toLocaleString() || 0 }} 条 · 按观测时间倒序
          </p>
        </div>
        <button
          class="primary-button"
          :disabled="loading || downloadBusy"
          @click="download"
        >
          <Download :size="15" />{{ downloadBusy ? "正在导出…" : "导出 CSV" }}
        </button>
      </div>
      <el-table
        v-loading="loading"
        :data="history?.items || []"
        :empty-text="error ? '数据加载失败' : '该站点在此日期没有观测记录'"
        stripe
        style="width: 100%"
        ><el-table-column
          prop="observedAt"
          label="观测时间（数据源时间）"
          min-width="185"
          ><template #default="scope">{{
            timeText(scope.row.observedAt)
          }}</template></el-table-column
        ><el-table-column
          v-for="m in metrics"
          :key="m.key"
          :label="`${m.name} (${m.unit})`"
          min-width="135"
          ><template #default="scope">{{
            valueText(scope.row[m.key], 2)
          }}</template></el-table-column
        ><el-table-column prop="source" label="来源" min-width="125"
      /></el-table>
      <div class="table-footer">
        <span>空值以 — 显示；CSV 保留为空。</span
        ><el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="history?.total || 0"
          layout="sizes, prev, pager, next"
          @current-change="load()"
          @size-change="filterChanged"
        />
      </div>
    </section>
    <template v-else-if="view === 'statistics'"
      ><div class="statistics-bar">
        <div>
          <span class="section-kicker">ANNUAL OBSERVATIONS</span>
          <h2>{{ year }}<small> 年度数据概览</small></h2>
        </div>
        <div class="statistics-total">
          <strong>{{ yearCount.toLocaleString() }}</strong
          ><span>{{ selectedMetric.name }}有效观测记录</span>
        </div>
        <el-select
          v-model="metric"
          aria-label="统计指标"
          style="width: 170px"
          @change="load()"
          ><el-option
            v-for="m in metrics"
            :key="m.key"
            :label="m.name"
            :value="m.key"
        /></el-select>
      </div>
      <section class="panel">
        <div class="panel-heading">
          <div>
            <h2>{{ selectedMetric.name }}月度分布</h2>
            <p>基于已导入的历史样本计算；无数据月份留空。</p>
          </div>
          <span class="muted-pill">{{ selectedMetric.unit }}</span>
        </div>
        <ClimateChart
          :labels="(stats?.months || []).map((m) => `${m.month}月`)"
          :values="(stats?.months || []).map((m) => m.average)"
          :minimums="(stats?.months || []).map((m) => m.minimum)"
          :maximums="(stats?.months || []).map((m) => m.maximum)"
          :name="'月均' + selectedMetric.name"
          :unit="selectedMetric.unit"
          :color="selectedMetric.color"
          bar
        />
      </section>
      <section class="panel monthly-table">
        <h2>月度明细</h2>
        <el-table :data="stats?.months || []" empty-text="暂无统计数据"
          ><el-table-column label="月份" min-width="100"
            ><template #default="s"
              >{{ s.row.month }}月</template
            ></el-table-column
          ><el-table-column
            v-for="col in [
              { key: 'average', label: '平均值' },
              { key: 'minimum', label: '最低值' },
              { key: 'maximum', label: '最高值' },
            ]"
            :key="col.key"
            :label="`${col.label} (${selectedMetric.unit})`"
            min-width="150"
            ><template #default="s">{{
              valueText(s.row[col.key], 2)
            }}</template></el-table-column
          ><el-table-column prop="count" label="有效观测数" min-width="130"
        /></el-table></section
    ></template>
    <template v-else-if="view === 'stations'"
      ><div class="source-banner">
        <MapPin :size="25" />
        <div>
          <h2>三个地区，多种温室环境</h2>
          <p>Álava · Navarra · Cartagena / Spain</p>
        </div>
        <span class="data-badge">NUTRICONTROL</span>
      </div>
      <section class="station-grid">
        <article
          v-for="s in stations.filter((s) => s.station.code.startsWith('ES-'))"
          :key="s.station.code"
          class="panel site-card"
        >
          <span class="site-code">{{ s.station.code }}</span
          ><span class="site-illustration"
            ><Leaf :size="38" stroke-width="1"
          /></span>
          <h2>{{ s.station.name }}</h2>
          <p>{{ s.station.location }}</p>
          <div class="site-description">{{ s.station.description }}</div>
          <div class="site-last">
            <span>最近观测记录</span
            ><strong>{{ timeText(s.lastObservedAt) }}</strong>
          </div>
          <button
            class="outline-button"
            @click="
              stationCode = s.station.code;
              stationChanged();
              $router.push('/overview');
            "
          >
            查看环境数据 <ArrowUpRight :size="16" />
          </button>
        </article></section
    ></template>
    <template v-else-if="view === 'sources'"
      ><div class="source-banner">
        <Database :size="26" />
        <div>
          <h2>NUTRICONTROL 开放数据集</h2>
          <p>Multi-Site Greenhouse Climate Dataset (Spain)</p>
        </div>
        <a
          href="https://github.com/SmartGreenHouses/NUTRICONTROL_datasets"
          target="_blank"
          rel="noopener noreferrer"
          class="outline-button"
          >访问原始仓库 <ArrowUpRight :size="15"
        /></a>
      </div>
      <section class="source-grid">
        <article class="panel prose">
          <div class="section-kicker">01 / SOURCE & LICENSE</div>
          <h2>来源与使用许可</h2>
          <p>
            数据由 Jaime Carlavilla-Lázaro、Virginia C. Sánchez、Carlos T.
            Calafate、Carlos García Sáez、Juan Morales-García、José M. Cecilia
            发布，许可为 CC BY 4.0。
          </p>
          <p>
            使用三个站点的 clean_data 数据，Navarra 与 Cartagena 选择
            C0，并按相同时间戳关联室外气象数据。DOI：<a
              href="https://doi.org/10.5281/zenodo.21037153"
              target="_blank"
              rel="noopener noreferrer"
              >10.5281/zenodo.21037153</a
            >。
          </p>
        </article>
        <article class="panel prose">
          <div class="section-kicker">02 / PROCESSING</div>
          <h2>透明的处理规则</h2>
          <p>
            默认样本仅选取每小时整点的原始15分钟记录，保留日期与缺失值，数值四舍五入至两位小数。辐射单位为
            W/m²，风速为 km/h。
          </p>
          <p>
            不插值、不把空值当成零、不把历史记录改成当天。原始数据未注明时区，因此保留源时间，不进行时区换算。月度统计基于已导入样本，不代表完整原始数据集。
          </p>
        </article>
        <article class="panel prose">
          <div class="section-kicker">03 / ARCHITECTURE</div>
          <h2>数据服务架构</h2>
          <div class="architecture-flow">
            <span><Database :size="18" /> MySQL</span><b>→</b
            ><span><Code :size="18" /> Spring Boot</span><b>→</b
            ><span><Leaf :size="18" /> Vue + ECharts</span>
          </div>
          <p>
            数据库存储、按日分页查询、15分钟趋势聚合、年度统计与 CSV
            导出。系统提供数据接入接口，不包含硬件采集程序和 Modbus 驱动。
          </p>
        </article>
        <article class="panel prose">
          <div class="section-kicker">04 / INGESTION API</div>
          <h2>接入自己的观测数据</h2>
          <p>
            <ShieldCheck :size="15" /> 配置至少32位的 INGEST_API_KEY
            后，采集端可使用 X-Ingest-Key 请求头发送数据。密钥仅在后端配置。
          </p>
          <pre>
POST /api/v1/observations
{
  "stationCode": "GH-001",
  "source": "device",
  "readings": [{
    "observedAt": "2026-10-29T09:00:00",
    "temperature": 25.3,
    "humidity": 68.2,
    "solarRadiation": 320.5
  }]
}</pre>
          <p>
            同站点同时间的相同数据可重复发送；数据不一致时返回409并回滚整个批次。完整说明见仓库
            docs/API.md。
          </p>
        </article>
      </section></template
    >
    <footer class="page-footer">
      <span>青禾 · 气象站数据平台</span
      ><span>Spring Boot / MyBatis / MySQL / Vue 3 / ECharts</span
      ><span>数据可追溯，变化看得见。</span>
    </footer>
  </main>
</template>
