import { afterEach, describe, expect, it, vi } from "vitest";
import { dateOf, metrics, query, request, timeText, valueText } from "./api";
afterEach(() => vi.unstubAllGlobals());
describe("source timestamps and missing values", () => {
  it("preserves the original calendar date without browser timezone conversion", () => {
    expect(dateOf("2025-10-26T02:15:00")).toBe("2025-10-26");
    expect(timeText("2025-10-26T02:15:00")).toBe("2025-10-26 02:15:00");
  });
  it("distinguishes real zero from an absent measurement", () => {
    expect(valueText(null)).toBe("—");
    expect(valueText(undefined)).toBe("—");
    expect(valueText(0)).toBe("0.0");
  });
  it("uses dataset radiation and wind units without assuming lux or m/s", () => {
    expect(metrics.find((m) => m.key === "solarRadiation")?.unit).toBe("W/m²");
    expect(metrics.find((m) => m.key === "windSpeed")?.unit).toBe("km/h");
  });
});
describe("API transport", () => {
  it("encodes station filters without concatenating user input into URL syntax", () => {
    expect(
      new URLSearchParams(query({ stationCode: "x&size=999", size: 20 })).get(
        "stationCode",
      ),
    ).toBe("x&size=999");
  });
  it("shows a server problem detail instead of treating HTTP errors as empty data", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ detail: "站点不存在" }), {
            status: 404,
          }),
        ),
    );
    await expect(request("/overview")).rejects.toThrow("站点不存在");
  });
  it("handles non-JSON reverse-proxy failures", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("Bad gateway", { status: 502 })),
    );
    await expect(request("/stations")).rejects.toThrow("502");
  });
  it("forwards cancellation to fetch for rapidly changing filters", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response("[]"));
    vi.stubGlobal("fetch", fetcher);
    const signal = new AbortController().signal;
    await request("/stations", {}, signal);
    expect(fetcher).toHaveBeenCalledWith("/api/v1/stations", { signal });
  });
});
