"""Import normalized CSV/CSV.GZ into the authenticated observation API.

Usage: python scripts/import_observations.py path/to/observations.csv.gz
INGEST_API_KEY is read from the environment, then from the local .env file.
"""
import argparse
import csv
import gzip
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    target = urllib.parse.urlparse(args.base_url)
    if target.scheme != "https" and not (target.scheme == "http" and target.hostname in {"localhost", "127.0.0.1", "::1"}):
        raise SystemExit("Use HTTPS for remote ingestion; HTTP is allowed only on loopback.")
    key = os.getenv("INGEST_API_KEY", "")
    if not key and (ROOT / ".env").is_file():
        key = next((line.partition("=")[2].strip() for line in (ROOT / ".env").read_text().splitlines() if line.startswith("INGEST_API_KEY=")), "")
    if len(key) < 32:
        raise SystemExit("Configure INGEST_API_KEY with at least 32 characters first.")
    processed = 0
    def send(station, source, readings):
        nonlocal processed
        payload = json.dumps({"stationCode": station, "source": source, "readings": readings}).encode()
        request = urllib.request.Request(args.base_url.rstrip("/") + "/api/v1/observations", data=payload,
                                         headers={"Content-Type": "application/json", "X-Ingest-Key": key}, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                processed += json.load(response)["processed"]
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")
            raise SystemExit(f"Batch rejected ({error.code}): {detail}. Earlier successful batches remain committed.")
    opener = gzip.open if args.file.suffix == ".gz" else open
    current = None
    batch = []
    mapping = {"temperature": "temperature", "humidity": "humidity", "solar_radiation": "solarRadiation", "outdoor_temperature": "outdoorTemperature", "wind_speed": "windSpeed"}
    with opener(args.file, "rt", encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            identity = (row["station_code"], row["source"])
            if current is not None and (identity != current or len(batch) >= 500):
                send(*current, batch); batch = []
            current = identity
            batch.append({"observedAt": row["observed_at"], **{output: float(row[source]) if row[source] else None for source, output in mapping.items()}})
        if batch:
            send(*current, batch)
    print(f"Processed {processed} records. Identical existing observations are idempotent.")


if __name__ == "__main__":
    main()
