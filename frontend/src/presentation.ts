import type { Station } from "./api";

// Presentation aliases describe a demonstration scenario, not collection sites.
// Canonical station codes and source records remain unchanged in the API/export.
const demoPartitions: Record<string, string> = {
  "ES-ALAVA": "A",
  "ES-NAVARRA": "B",
  "ES-CARTAGENA": "C",
};

export function stationPresentation(station: Station): Station {
  const partition = demoPartitions[station.code];
  if (!partition) return station;
  return {
    ...station,
    name: `示例气象站 ${partition}`,
    location: `东北旺试验田 · 演示分区 ${partition}`,
    description:
      "面向试验田环境监测的演示站点。当前显示历史样本，实际采集设备可通过接口接入。",
  };
}
