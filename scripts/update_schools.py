#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


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


def main() -> int:
    cfg = parse_args()
    records = load_json(cfg.input_path)
    validate_schema(records)

    # Update only the requested slice; for now we simply normalize and preserve data.
    target_records = select_target_range(records, cfg.start_no, cfg.end_no)
    normalized_target = [normalize_record(r) for r in target_records]

    if cfg.start_no is None and cfg.end_no is None:
        updated = [normalize_record(r) for r in records]
    else:
        updated = list(records)
        start_idx = 0 if cfg.start_no is None else cfg.start_no - 1
        end_idx = len(records) if cfg.end_no is None else cfg.end_no
        updated[start_idx:end_idx] = normalized_target

    output_text = json.dumps(
        updated,
        ensure_ascii=False,
        indent=2 if cfg.pretty else None,
    )

    cfg.output_path.parent.mkdir(parents=True, exist_ok=True)
    existing = cfg.output_path.read_text(encoding="utf-8") if cfg.output_path.exists() else None

    if existing == output_text:
        print("No changes.")
        return 0

    cfg.output_path.write_text(output_text + "\n", encoding="utf-8")
    print(f"Wrote {cfg.output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
