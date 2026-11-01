// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import Workspace from "./Workspace.vue";
import { request } from "../api";
vi.mock("../components/ClimateChart.vue", () => ({
  default: {
    props: ["labels", "values", "unit", "name"],
    template: '<div class="chart-stub" />',
  },
}));
vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return {
    ...actual,
    request: vi.fn(
      async (path: string, params: Record<string, unknown> = {}) => {
        const station = {
          code: "ES-ALAVA",
          name: "Álava 温室",
          location: "Spain",
          description: "fixture",
        };
        if (path === "/stations")
          return [
            {
              station,
              lastObservedAt: "2026-02-25T08:00:00",
              online: false,
              source: "nutricontrol",
            },
          ];
        if (path === "/overview")
          return {
            station,
            latest: {
              stationCode: station.code,
              observedAt: "2026-02-25T08:00:00",
              temperature: 20,
              humidity: null,
              solarRadiation: 0,
              outdoorTemperature: null,
              windSpeed: null,
              source: "nutricontrol",
            },
            serverTime: "2026-11-01T09:00:00",
          };
        if (path === "/statistics/monthly")
          return {
            year: params.year,
            metric: "temperature",
            months: Array.from({ length: 12 }, (_, i) => ({
              month: i + 1,
              average: params.year === 2025 ? 10 + i : null,
              minimum: null,
              maximum: null,
              count: params.year === 2025 ? 20 : 0,
            })),
          };
        return { points: [] };
      },
    ),
  };
});
describe("annual filter regression", () => {
  it("requests the edited year and updates the displayed report together", async () => {
    const wrapper = mount(Workspace, {
      props: { view: "statistics" },
      global: {
        stubs: {
          RouterLink: true,
          ElSelect: true,
          ElOption: true,
          ElTable: true,
          ElTableColumn: true,
          ElDatePicker: true,
        },
        directives: { loading: () => {} },
      },
    });
    await flushPromises();
    expect(wrapper.text()).toContain("2026 年度数据概览");
    await wrapper.get('input[aria-label="统计年份"]').setValue("2025");
    await flushPromises();
    expect(request).toHaveBeenLastCalledWith(
      "/statistics/monthly",
      expect.objectContaining({ year: 2025 }),
      expect.any(AbortSignal),
    );
    expect(wrapper.text()).toContain("2025 年度数据概览");
    expect(wrapper.text()).toContain("240");
    wrapper.unmount();
  });
});
