#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any
import requests
from bs4 import BeautifulSoup

REQUIRED_FIELDS = [
    "schoolName",
    "category",
    "capacity",
    "examDates",
    "subjects",
    "alternateSubjects",
    "interview",
    "englishQualificationBenefit",
    "notes",
    "infoLink",
]


@dataclass(frozen=True)
class Config:
    input_path: Path
    output_path: Path
    start_no: int | None
    end_no: int | None
    pretty: bool


def parse_args() -> Config:
    parser = argparse.ArgumentParser(
        description="Update school JSON (scaffold for weekly automation)."
    )
    parser.add_argument(
        "--input",
        required=True,
        help="Path to the source JSON file.",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Path to write the updated JSON file.",
    )
    parser.add_argument(
        "--start-no",
        type=int,
        default=None,
        help="1-based inclusive start index for targeted records.",
    )
    parser.add_argument(
        "--end-no",
        type=int,
        default=None,
        help="1-based inclusive end index for targeted records.",
    )
    parser.add_argument(
        "--pretty",
        action="store_true",
        help="Write formatted JSON with indentation.",
    )
    args = parser.parse_args()

    if args.start_no is not None and args.start_no < 1:
        raise SystemExit("--start-no must be >= 1")
    if args.end_no is not None and args.end_no < 1:
        raise SystemExit("--end-no must be >= 1")
    if args.start_no is not None and args.end_no is not None and args.start_no > args.end_no:
        raise SystemExit("--start-no must be <= --end-no")

    return Config(
        input_path=Path(args.input),
        output_path=Path(args.output),
        start_no=args.start_no,
        end_no=args.end_no,
        pretty=args.pretty,
    )


def load_json(path: Path) -> list[dict[str, Any]]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(f"Input file not found: {path}")
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON: {path} ({exc})")

    if not isinstance(data, list):
        raise SystemExit("Top-level JSON must be a list of school objects.")

    normalized: list[dict[str, Any]] = []
    for i, item in enumerate(data, start=1):
        if not isinstance(item, dict):
            raise SystemExit(f"Record #{i} is not an object.")
        normalized.append(item)
    return normalized


def validate_schema(records: list[dict[str, Any]]) -> None:
    for i, record in enumerate(records, start=1):
        missing = [key for key in REQUIRED_FIELDS if key not in record]
        if missing:
            raise SystemExit(f"Record #{i} is missing fields: {', '.join(missing)}")


def select_target_range(records: list[dict[str, Any]], start_no: int | None, end_no: int | None) -> list[dict[str, Any]]:
    if start_no is None and end_no is None:
        return records

    start_idx = 0 if start_no is None else start_no - 1
    end_idx = len(records) if end_no is None else end_no
    return records[start_idx:end_idx]


def normalize_record(record: dict[str, Any]) -> dict[str, Any]:
    # Keep structure stable; fill missing optional keys with null.
    out = dict(record)
    for key in REQUIRED_FIELDS:
        out.setdefault(key, None)
    return out

def check_school_url(record: dict[str, Any]) -> dict[str, Any]:
    school_name = record.get("schoolName", "(unknown)")
    url = record.get("infoLink")

    result: dict[str, Any] = {
        "schoolName": school_name,
        "url": url,
        "ok": False,
        "statusCode": None,
        "title": None,
        "errorType": None,
        "errorMessage": None,
    }

    if not url:
        result["errorType"] = "MISSING_URL"
        result["errorMessage"] = "infoLink is null or empty"
        print(f"[SKIP] {school_name}: no URL")
        return result

    try:
        response = requests.get(
            url,
            timeout=15,
            headers={"User-Agent": "Mozilla/5.0"},
        )

        result["statusCode"] = response.status_code
        result["ok"] = response.status_code == 200

        print(f"[OK] {school_name}: {response.status_code}")

        content_type = response.headers.get("Content-Type", "")
        if "text/html" in content_type:
            soup = BeautifulSoup(response.text, "html.parser")
            title = soup.title.string.strip() if soup.title and soup.title.string else "No title"
            result["title"] = title
            print(f"      Title: {title}")

    except requests.exceptions.SSLError as e:
        result["errorType"] = "SSL_ERROR"
        result["errorMessage"] = str(e)
        print(f"[SSL ERROR] {school_name}: {e}")

    except requests.exceptions.RequestException as e:
        result["errorType"] = "REQUEST_ERROR"
        result["errorMessage"] = str(e)
        print(f"[ERROR] {school_name}: {e}")

    return result

def main() -> int:
    cfg = parse_args()
    records = load_json(cfg.input_path)
    validate_schema(records)

    target_records = select_target_range(records, cfg.start_no, cfg.end_no)

    report: list[dict[str, Any]] = []
    for record in target_records:
        report.append(check_school_url(record))

    failed = [r for r in report if not r["ok"]]
    print(f"\nSummary: {len(report) - len(failed)} ok, {len(failed)} failed")

    report_path = cfg.output_path.parent / "check-report.json"
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {report_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
