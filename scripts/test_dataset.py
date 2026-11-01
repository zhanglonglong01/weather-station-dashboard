import csv
import gzip
import hashlib
import json
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

from prepare_dataset import ROOT, number, normalize


class DatasetTests(unittest.TestCase):
    def test_missing_zero_nonfinite_and_bounds_are_distinct(self):
        self.assertEqual(number("0", (0, 100)), "0.00")
        for missing in (None, "", "NaN", "inf", "-1", "101"):
            self.assertEqual(number(missing, (0, 100)), "")
        self.assertEqual(number("25.345", (-60, 80)), "25.35")

    def test_bundled_data_matches_manifest_and_has_no_duplicate_keys(self):
        folder = ROOT / "backend/src/main/resources/dataset"
        manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(hashlib.sha256((folder / "observations.csv.gz").read_bytes()).hexdigest(), manifest["sampleSha256"])
        with gzip.open(folder / "observations.csv.gz", "rt", encoding="utf-8", newline="") as stream:
            rows = list(csv.DictReader(stream))
        self.assertEqual(len(rows), manifest["totalRows"])
        self.assertEqual(len({(r["station_code"], r["observed_at"]) for r in rows}), len(rows))
        self.assertTrue(all(r["source"] == "nutricontrol" for r in rows))
        self.assertTrue(any(r["wind_speed"] == "" for r in rows))
        self.assertTrue(any(r["solar_radiation"] == "0.00" for r in rows))
        for site in manifest["stations"]:
            subset = [r for r in rows if r["station_code"] == site["stationCode"]]
            self.assertEqual(len(subset), site["sampledRows"])
            self.assertEqual(subset[0]["observed_at"], site["firstObservedAt"])
            self.assertEqual(subset[-1]["observed_at"], site["lastObservedAt"])

    def test_exact_timestamp_join_and_hourly_sampling_do_not_impute(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            for region in ("Alava", "Navarra", "Cartagena"):
                suffix = region if region == "Alava" else region + "_comp0"
                filename = f"{region}_15min.csv" if region == "Alava" else f"{region}_comp0_15min.csv"
                fields = ["dateTime", f"DB_TEMP_INT_{suffix}", f"REL_HUM_INT_{suffix}"]
                with (folder / filename).open("w", newline="", encoding="utf-8") as stream:
                    writer = csv.DictWriter(stream, fieldnames=fields); writer.writeheader()
                    writer.writerow(dict(zip(fields, ["2025-10-26 02:00:00", "25.345", ""])))
                    writer.writerow(dict(zip(fields, ["2025-10-26 02:15:00", "26", "60"])))
                if region != "Alava":
                    with (folder / f"{region}_meteo_15min.csv").open("w", newline="", encoding="utf-8") as stream:
                        writer = csv.writer(stream); writer.writerow(["dateTime", f"RADIATION_MEAN_{region}_meteo"])
                        writer.writerow(["2025-10-26 02:15:00", "100"])
            rows, report = normalize(folder, 60)
            self.assertEqual(len(rows), 3)
            for row in rows:
                self.assertEqual(row[1], "2025-10-26T02:00:00")
                self.assertEqual(row[2], "25.35")
                self.assertEqual(row[3:7], ["", "", "", ""])
            self.assertEqual(report[0]["inputRows"], 2)


if __name__ == "__main__":
    unittest.main()
