"""Read-only live acceptance: compare MySQL/API results with the bundled source sample."""
import argparse
import csv
import gzip
import json
import urllib.parse
import urllib.request
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    def get(path, **params):
        url = args.base_url.rstrip("/") + path + ("?" + urllib.parse.urlencode(params) if params else "")
        with urllib.request.urlopen(url, timeout=30) as response:
            return json.loads(response.read(), parse_float=Decimal)
    manifest = get("/api/v1/dataset")
    with gzip.open(ROOT / "backend/src/main/resources/dataset/observations.csv.gz", "rt", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream))
    checks = 0
    for station in manifest["stations"]:
        code = station["stationCode"]
        subset = [r for r in rows if r["station_code"] == code]
        overview = get("/api/v1/overview", stationCode=code)
        assert overview["latest"]["observedAt"] == subset[-1]["observed_at"]
        assert overview["latest"]["source"] == "nutricontrol"
        checks += 2
        date = subset[-1]["observed_at"][:10]
        expected = sorted([r for r in subset if r["observed_at"].startswith(date)], key=lambda r: r["observed_at"], reverse=True)
        page = get("/api/v1/observations", stationCode=code, date=date, size=100)
        assert page["total"] == len(expected)
        assert [r["observedAt"] for r in page["items"]] == [r["observed_at"] for r in expected]
        checks += 2
        for metric, field in [("temperature", "temperature"), ("solarRadiation", "solar_radiation"), ("windSpeed", "wind_speed")]:
            stats = get("/api/v1/statistics/monthly", stationCode=code, year=2025, metric=metric)
            for month in stats["months"]:
                prefix = f"2025-{month['month']:02d}"
                numbers = [Decimal(r[field]) for r in subset if r["observed_at"].startswith(prefix) and r[field]]
                assert month["count"] == len(numbers), (code, metric, month)
                average = (sum(numbers) / len(numbers)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP) if numbers else None
                assert month["average"] == average, (code, metric, month, average)
                assert month["minimum"] == (min(numbers) if numbers else None)
                assert month["maximum"] == (max(numbers) if numbers else None)
                checks += 4
        trend = get("/api/v1/trends", stationCode=code, date=date, metric="temperature")
        assert len(trend["points"]) == len(expected)
        checks += 1
    print(f"PASS: {checks} live assertions across all three sites; API statistics match attributed source samples.")


if __name__ == "__main__":
    main()
