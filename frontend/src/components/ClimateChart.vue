<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import { init, use, type EChartsType } from "echarts/core";
import { LineChart, BarChart } from "echarts/charts";
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
} from "echarts/components";
import { SVGRenderer } from "echarts/renderers";
use([
  LineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  SVGRenderer,
]);
const props = defineProps<{
  labels: string[];
  values: (number | null)[];
  unit: string;
  name: string;
  color?: string;
  bar?: boolean;
  minimums?: (number | null)[];
  maximums?: (number | null)[];
}>();
const root = ref<HTMLDivElement>();
let chart: EChartsType | undefined;
let observer: ResizeObserver | undefined;
function render() {
  if (!chart) return;
  const color = props.color || "#27745d";
  const series: object[] = [
    {
      name: props.name,
      type: props.bar ? "bar" : "line",
      data: props.values,
      smooth: false,
      connectNulls: false,
      symbol: "circle",
      symbolSize: 5,
      showSymbol: props.values.length < 30,
      barMaxWidth: 28,
      itemStyle: { color, borderRadius: [5, 5, 0, 0] },
      lineStyle: { color, width: 2.5 },
      areaStyle: props.bar ? undefined : { color, opacity: 0.08 },
    },
  ];
  if (props.minimums)
    series.push({
      name: "最低值",
      type: "line",
      data: props.minimums,
      connectNulls: false,
      symbol: "none",
      lineStyle: { width: 1.5, type: "dashed", color: "#8bb0cc" },
      itemStyle: { color: "#8bb0cc" },
    });
  if (props.maximums)
    series.push({
      name: "最高值",
      type: "line",
      data: props.maximums,
      connectNulls: false,
      symbol: "none",
      lineStyle: { width: 1.5, type: "dashed", color: "#d3a783" },
      itemStyle: { color: "#d3a783" },
    });
  chart.setOption(
    {
      animationDuration: 250,
      tooltip: {
        trigger: "axis",
        valueFormatter: (v: unknown) =>
          v == null ? "缺失" : `${v} ${props.unit}`,
      },
      legend: {
        show: !!props.minimums,
        bottom: 0,
        textStyle: { color: "#7a8780", fontSize: 11 },
      },
      grid: { left: 58, right: 24, top: 34, bottom: props.minimums ? 52 : 34 },
      xAxis: {
        type: "category",
        boundaryGap: !!props.bar,
        data: props.labels,
        axisLine: { lineStyle: { color: "#e0e8e2" } },
        axisTick: { show: false },
        axisLabel: { color: "#8b978f", fontSize: 11, hideOverlap: true },
      },
      yAxis: {
        type: "value",
        name: props.unit,
        scale: true,
        nameTextStyle: { color: "#8b978f" },
        splitLine: { lineStyle: { color: "#edf1ec", type: "dashed" } },
        axisLabel: { color: "#8b978f", fontSize: 11 },
      },
      series,
    },
    true,
  );
}
watch(
  () => [props.labels, props.values, props.minimums, props.maximums, props.bar],
  render,
  { deep: true },
);
onMounted(() => {
  if (root.value) {
    chart = init(root.value, undefined, { renderer: "svg" });
    observer = new ResizeObserver(() => chart?.resize());
    observer.observe(root.value);
    render();
  }
});
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <div
    ref="root"
    class="climate-chart"
    role="img"
    :aria-label="`${name}图表，共${values.filter((v) => v != null).length}个有效点，单位${unit}`"
  ></div>
</template>
