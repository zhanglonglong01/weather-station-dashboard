"""Download pinned CC-BY-4.0 data and generate a reproducible, attributed sample.

No third-party Python packages. Keeps original timestamps; no interpolation.
"""
from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import io
import json
import math
import urllib.request
from datetime import datetime
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REVISION = "6dece0d2c7483982610e4d2de225db3c4649b935"
SOURCE = "https://github.com/SmartGreenHouses/NUTRICONTROL_datasets"
FILES = {
    "Alava_15min.csv": "Alava_data/clean_data/Alava_15min.csv",
    "Navarra_comp0_15min.csv": "Navarra_data/clean_data/Navarra_comp0_15min.csv",
    "Navarra_meteo_15min.csv": "Navarra_data/clean_data/Navarra_meteo_15min.csv",
    "Cartagena_comp0_15min.csv": "Cartagena_data/clean_data/Cartagena_comp0_15min.csv",
    "Cartagena_meteo_15min.csv": "Cartagena_data/clean_data/Cartagena_meteo_15min.csv",
}
FIELDS = ["station_code", "observed_at", "temperature", "humidity", "solar_radiation", "outdoor_temperature", "wind_speed", "source"]
BOUNDS = [(-60, 80), (0, 100), (0, 2000), (-60, 80), (0, 300)]


def number(value: str | None, bounds: tuple[int, int]) -> str:
    if value is None or not value.strip():
        return ""
    try:
        n = Decimal(value)
        if not n.is_finite() or not bounds[0] <= n <= bounds[1]:
            return ""
        return str(n.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
    except InvalidOperation:
        return ""


def read_rows(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        yield from csv.DictReader(stream)


def normalize(input_dir: Path, sample_minutes: int) -> tuple[list[list[str]], list[dict]]:
    normalized: list[list[str]] = []
    reports = []
    for region, code in [("Alava", "ES-ALAVA"), ("Navarra", "ES-NAVARRA"), ("Cartagena", "ES-CARTAGENA")]:
        suffix = region if region == "Alava" else f"{region}_comp0"
        path = input_dir / (f"{region}_15min.csv" if region == "Alava" else f"{region}_comp0_15min.csv")
        meteo = {} if region == "Alava" else {
            r["dateTime"]: r for r in read_rows(input_dir / f"{region}_meteo_15min.csv")
        }
        meteo_suffix = region if region == "Alava" else f"{region}_meteo"
        seen: set[str] = set()
        counts = {"inputRows": 0, "sampledRows": 0, "allMissingRowsSkipped": 0, "missingOrRejectedCells": 0}
        station_rows: list[list[str]] = []
        for row in read_rows(path):
            counts["inputRows"] += 1
            timestamp = datetime.fromisoformat(row["dateTime"])
            if timestamp.tzinfo is not None:
                raise ValueError("Unexpected timezone-aware source timestamp; update mapping explicitly")
            if (timestamp.hour * 60 + timestamp.minute) % sample_minutes:
                continue
            if timestamp.second != 0 or timestamp.microsecond != 0:
                raise ValueError("Unexpected source timestamp precision")
            time_text = timestamp.isoformat(timespec="seconds")
            if time_text in seen:
                raise ValueError(f"Duplicate source timestamp in {path.name}: {time_text}")
            seen.add(time_text)
            weather = row if region == "Alava" else meteo.get(row["dateTime"], {})
            values = [row.get(f"DB_TEMP_INT_{suffix}"), row.get(f"REL_HUM_INT_{suffix}"),
                      weather.get(f"RADIATION_MEAN_{meteo_suffix}"), weather.get(f"DB_TEMP_EXT_{meteo_suffix}"),
                      weather.get(f"WIND_VELOCITY_MEAN_{meteo_suffix}")]
            cleaned = [number(v, b) for v, b in zip(values, BOUNDS)]
            counts["missingOrRejectedCells"] += cleaned.count("")
            if not any(cleaned):
                counts["allMissingRowsSkipped"] += 1
                continue
            station_rows.append([code, time_text, *cleaned, "nutricontrol"])
        station_rows.sort(key=lambda r: r[1])
        normalized.extend(station_rows)
        counts["sampledRows"] = len(station_rows)
        reports.append({"stationCode": code, "region": region, **counts,
                        "firstObservedAt": station_rows[0][1] if station_rows else None,
                        "lastObservedAt": station_rows[-1][1] if station_rows else None})
    return normalized, reports


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path, default=ROOT / "data" / "imports")
    parser.add_argument("--download", action="store_true", help="Download only the five pinned source CSV files")
    parser.add_argument("--sample-minutes", type=int, choices=[15, 60], default=60)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "backend/src/main/resources/dataset")
    args = parser.parse_args()
    args.input_dir.mkdir(parents=True, exist_ok=True)
    if args.download:
        for name, relative in FILES.items():
            path = args.input_dir / name
            print(f"Downloading {name}")
            urllib.request.urlretrieve(f"https://raw.githubusercontent.com/SmartGreenHouses/NUTRICONTROL_datasets/{REVISION}/{relative}", path)
    for name in FILES:
        if not (args.input_dir / name).is_file() or (args.input_dir / name).stat().st_size == 0:
            raise SystemExit(f"Missing or empty source file: {name}. Run with --download.")
    rows, reports = normalize(args.input_dir, args.sample_minutes)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / "observations.csv.gz"
    with output.open("wb") as raw, gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0) as compressed:
        with io.TextIOWrapper(compressed, encoding="utf-8", newline="") as text:
            writer = csv.writer(text, lineterminator="\n")
            writer.writerow(FIELDS)
            writer.writerows(rows)
    manifest = {
        "title": "Multi-Site Greenhouse Climate Dataset (Spain)",
        "sourceUrl": SOURCE, "revision": REVISION, "license": "CC-BY-4.0",
        "licenseUrl": "https://creativecommons.org/licenses/by/4.0/",
        "doi": "10.5281/zenodo.21037153",
        "authors": ["Jaime Carlavilla-Lázaro", "Virginia C. Sánchez", "Carlos T. Calafate", "Carlos García Sáez", "Juan Morales-García", "José M. Cecilia"],
        "samplingMinutes": args.sample_minutes, "timezone": "unspecified-in-source; original wall-clock timestamps preserved",
        "processing": "Exact timestamp left join to site meteorology; select original rows on interval boundary; round to 2 decimal places; missing/non-finite/out-of-range cells remain empty; skip rows with all five metrics empty; no interpolation or time shifting.",
        "units": {"temperature": "degC", "humidity": "%", "solarRadiation": "W/m2", "outdoorTemperature": "degC", "windSpeed": "km/h"},
        "totalRows": len(rows), "stations": reports,
        "files": [{"path": relative, "sha256": hashlib.sha256((args.input_dir / name).read_bytes()).hexdigest()} for name, relative in FILES.items()],
        "sampleSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
    }
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"totalRows": len(rows), "stations": reports, "compressedBytes": output.stat().st_size}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
