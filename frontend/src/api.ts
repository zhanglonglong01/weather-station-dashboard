export interface Station {
  code: string;
  name: string;
  location: string;
  description: string;
}
export interface StationStatus {
  station: Station;
  lastObservedAt: string | null;
  online: boolean;
  source: string | null;
}
export type Metric =
  | "temperature"
  | "humidity"
  | "solarRadiation"
  | "outdoorTemperature"
  | "windSpeed";
export interface Reading {
  id: number;
  stationCode: string;
  observedAt: string;
  temperature: number | null;
  humidity: number | null;
  solarRadiation: number | null;
  outdoorTemperature: number | null;
  windSpeed: number | null;
  source: string;
}
export interface Overview {
  station: Station;
  latest: Reading | null;
  todayCount: number;
  todayAverage: number | null;
  todayMinimum: number | null;
  todayMaximum: number | null;
  serverTime: string;
  demoMode: boolean;
}
export interface Page {
  items: Reading[];
  total: number;
  page: number;
  size: number;
}
export interface Trends {
  metric: Metric;
  date: string;
  points: { time: string; value: number | null; count: number }[];
}
export interface Monthly {
  month: number;
  average: number | null;
  minimum: number | null;
  maximum: number | null;
  count: number;
}
export interface Statistics {
  metric: Metric;
  year: number;
  months: Monthly[];
}
export const metrics: {
  key: Metric;
  name: string;
  unit: string;
  color: string;
  note: string;
}[] = [
  {
    key: "temperature",
    name: "室内气温",
    unit: "℃",
    color: "#d38a43",
    note: "温室内部干球温度",
  },
  {
    key: "humidity",
    name: "相对湿度",
    unit: "%",
    color: "#4995bb",
    note: "温室内部空气相对湿度",
  },
  {
    key: "solarRadiation",
    name: "太阳辐射",
    unit: "W/m²",
    color: "#bd9b38",
    note: "室外气象站平均太阳辐射",
  },
  {
    key: "outdoorTemperature",
    name: "室外气温",
    unit: "℃",
    color: "#7d88b6",
    note: "室外干球温度",
  },
  {
    key: "windSpeed",
    name: "室外风速",
    unit: "km/h",
    color: "#479783",
    note: "室外平均风速，保留原始单位",
  },
];
export function query(params: Record<string, string | number>): string {
  return new URLSearchParams(
    Object.entries(params).map(([k, v]) => [k, String(v)]),
  ).toString();
}
export async function request<T>(
  path: string,
  params: Record<string, string | number> = {},
  signal?: AbortSignal,
): Promise<T> {
  const suffix = query(params);
  const response = await fetch(`/api/v1${path}${suffix ? `?${suffix}` : ""}`, {
    signal,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(
      typeof body.detail === "string"
        ? body.detail
        : `请求失败（${response.status}）`,
    );
  }
  return response.json() as Promise<T>;
}
export const valueText = (v: number | null | undefined, digits = 1) =>
  v == null
    ? "—"
    : v.toLocaleString("zh-CN", {
        maximumFractionDigits: digits,
        minimumFractionDigits: digits,
      });
export const timeText = (time: string | null | undefined) =>
  time ? time.replace("T", " ").slice(0, 19) : "暂无记录";
export const dateOf = (time: string) => time.slice(0, 10);
