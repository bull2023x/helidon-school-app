#!/usr/bin/env python3
"""Collect current-year admissions candidates for source and exam-round review.

Rule-generated candidates always require review and cannot overwrite verified facts.
"""
from __future__ import annotations

import argparse
import json
import os
import time
import re
import shutil
from dataclasses import dataclass
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from typing import Any
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup
from pypdf import PdfReader
from admission_rules import target_admission_year, source_admission_year

REQUIRED = ("schoolName", "category", "capacity", "examDates", "subjects", "alternateSubjects", "interview", "englishQualificationBenefit", "notes", "infoLink")
UPDATE_FIELDS = {"capacity", "examDates", "subjects", "interview", "englishQualificationBenefit"}
KEYWORDS = ("入試", "募集", "出願", "帰国", "国際", "英語", "要項")
HEADERS = {"User-Agent": "SchoolDataEditor/1.0 (+https://github.com/bull2023x/helidon-school-app)"}
PDF_LIMIT = 12 * 1024 * 1024


@dataclass(frozen=True)
class Config:
    input: Path; output: Path; start: int | None; end: int | None
    pretty: bool; apply: bool; minimum: float; max_pdfs: int; admission_year: int; refresh: bool


def args() -> Config:
    p = argparse.ArgumentParser(description="Extract admissions updates from official HTML/PDF sources.")
    p.add_argument("--input", required=True); p.add_argument("--output", required=True)
    p.add_argument("--start-no", type=int); p.add_argument("--end-no", type=int)
    p.add_argument("--pretty", action="store_true")
    p.add_argument("--refresh", action="store_true", help="Save retrieval timestamps without applying unreviewed candidates.")
    p.add_argument("--apply", action="store_true", help="Apply only explicitly reviewed candidates; rule-generated candidates are never applied.")
    p.add_argument("--min-confidence", type=float, default=.85)
    p.add_argument("--max-pdfs", type=int, default=3)
    p.add_argument("--admission-year", type=int, default=target_admission_year())
    a = p.parse_args()
    if (a.start_no and a.start_no < 1) or (a.end_no and a.end_no < 1) or (a.start_no and a.end_no and a.start_no > a.end_no): p.error("invalid record range")
    if not 0 <= a.min_confidence <= 1 or a.max_pdfs < 0: p.error("invalid confidence or PDF limit")
    return Config(Path(a.input), Path(a.output), a.start_no, a.end_no, a.pretty, a.apply, a.min_confidence, a.max_pdfs, a.admission_year, a.refresh)


def compact(s: str) -> str: return re.sub(r"\s+", " ", s).strip()

def get(url: str) -> requests.Response:
    time.sleep(max(0, float(os.environ.get("REQUEST_DELAY_SECONDS", "1"))))
    r = requests.get(url, headers=HEADERS, timeout=(10, 30), allow_redirects=True)
    r.raise_for_status()
    return r

def pdf_text(data: bytes) -> str:
    return compact("\n".join(page.extract_text() or "" for page in PdfReader(BytesIO(data)).pages[:30]))

def sources(url: str, max_pdfs: int) -> tuple[list[tuple[str, str, str]], dict[str, Any]]:
    report: dict[str, Any] = {"url": url, "ok": False, "sources": [], "error": None}
    try:
        r = get(url); ct = r.headers.get("content-type", "").lower(); report.update(ok=True, statusCode=r.status_code, url=r.url)
        if "pdf" in ct or urlparse(r.url).path.lower().endswith(".pdf"):
            if len(r.content) > PDF_LIMIT: raise ValueError("PDF exceeds size limit")
            text = pdf_text(r.content); report["sources"].append({"url":r.url,"kind":"pdf","characters":len(text)}); return [(r.url,"pdf",text)], report
        if "html" not in ct: report.update(ok=False, error=f"Unsupported content type: {ct}"); return [], report
        r.encoding = r.apparent_encoding or r.encoding; soup = BeautifulSoup(r.text, "html.parser")
        text = compact(soup.get_text(" ", strip=True)); found = [(r.url,"html",text)]; report["sources"].append({"url":r.url,"kind":"html","characters":len(text)})
        links: list[tuple[int,str]] = []
        for a in soup.find_all("a", href=True):
            href, label = urljoin(r.url, a["href"]), compact(a.get_text(" ", strip=True))
            score = sum(word in label + " " + href for word in KEYWORDS)
            if score and urlparse(href).path.lower().endswith(".pdf"): links.append((score, href))
        seen: set[str] = set()
        for _, href in sorted(links, reverse=True):
            if href in seen or len(found) - 1 >= max_pdfs: continue
            seen.add(href)
            try:
                p = get(href)
                if len(p.content) > PDF_LIMIT: raise ValueError("PDF exceeds size limit")
                ptext = pdf_text(p.content); found.append((p.url,"pdf",ptext)); report["sources"].append({"url":p.url,"kind":"pdf","characters":len(ptext)})
            except Exception as e: report["sources"].append({"url":href,"kind":"pdf","error":str(e)})
        return found, report
    except Exception as e:
        report.update(ok=False, error=f"{type(e).__name__}: {e}"); return [], report

def extract(source_url: str, kind: str, text: str, category: str, admission_year: int | None = None) -> list[dict[str, Any]]:
    admission_year = admission_year or target_admission_year()
    if source_admission_year(text) != admission_year:
        return []
    # Do not use a page if it has no sign of the record category; this avoids
    # replacing a 帰国 row with a nearby general-admissions value.
    hint = (category or "").replace("受験", "").replace("入試", "")
    scoped = text if not hint or hint in text else ""
    out: list[dict[str, Any]] = []
    patterns = {
      "examDates": r"(?:試験日|入試日|選考日|実施日)\s*[：:]?\s*([^。\n]{3,160})",
      "capacity": r"(?:募集人員|募集定員|定員)\s*[：:]?\s*([^。\n]{1,100})",
      "subjects": r"(?:試験科目|選考方法|試験内容)\s*[：:]?\s*([^。\n]{3,220})",
    }
    for field, pat in patterns.items():
        m = re.search(pat, scoped)
        if not m: continue
        value = compact(m.group(1).strip(" ：:"))
        if field == "examDates" and not re.search(r"20\d{2}|\d{1,2}月", value): continue
        out.append(candidate(field, value, .90 if kind == "pdf" else .86, source_url, kind, scoped, m))
    for field, pat in {
      "interview": r"(?:面接[^。\n]{0,100}(?:あり|実施|行う|含む)|(?:あり|実施)[^。\n]{0,60}面接)",
      "englishQualificationBenefit": r"(?:英検|TOEFL|IELTS|CEFR|英語資格)[^。\n]{0,180}(?:免除|加点|優遇|換算|保証)[^。\n]{0,100}",
    }.items():
        m = re.search(pat, scoped)
        if m: out.append(candidate(field, m.group(0), (.88 if kind == "pdf" else .82), source_url, kind, scoped, m))
    for item in out:
        item.update(admissionYear=admission_year, requiresReview=True)
    return out

def candidate(field: str, value: str, confidence: float, url: str, kind: str, text: str, m: re.Match[str]) -> dict[str, Any]:
    return {"field":field, "value":compact(value), "confidence":confidence, "sourceUrl":url, "sourceType":kind, "excerpt":compact(text[max(0,m.start()-120):m.end()+120])[:500]}

def write(path: Path, obj: Any, pretty: bool=True) -> bool:
    data = json.dumps(obj, ensure_ascii=False, indent=2 if pretty else None) + "\n"; old = path.read_text(encoding="utf-8") if path.exists() else ""
    if data == old: return False
    path.parent.mkdir(parents=True, exist_ok=True); path.write_text(data, encoding="utf-8"); return True

def backup(path: Path) -> None:
    if not path.exists(): return
    archive = path.parent.parent / "archive"; archive.mkdir(exist_ok=True)
    shutil.copy2(path, archive / f"{path.stem}-{datetime.now():%Y%m%d-%H%M%S}{path.suffix}")

def main() -> int:
    cfg = args(); records = json.loads(cfg.input.read_text(encoding="utf-8"))
    if not isinstance(records, list) or any(not isinstance(r,dict) or any(k not in r for k in REQUIRED) for r in records): raise SystemExit("Invalid school JSON schema")
    targets = records[(cfg.start-1 if cfg.start else 0):(cfg.end if cfg.end else len(records))]; now = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"); reports=[]; changes=0
    for record in targets:
        url = record.get("infoLink"); item={"id":record.get("id"),"schoolName":record["schoolName"],"category":record["category"],"checkedAt":now}
        found, fetch = sources(url, cfg.max_pdfs) if url else ([], {"ok":False,"error":"MISSING_URL"})
        candidates = [c for source in found for c in extract(*source, record["category"], cfg.admission_year)]
        selected: dict[str,dict[str,Any]] = {}
        for c in candidates:
            if c["field"] not in selected or c["confidence"] > selected[c["field"]]["confidence"]: selected[c["field"]]=c
        applied=[]
        if fetch["ok"]: record["lastRetrievedAt"] = now
        if cfg.apply:
            for field,c in selected.items():
                if not c.get("requiresReview", True) and field in UPDATE_FIELDS and c["confidence"] >= cfg.minimum and record.get(field) != c["value"]:
                    applied.append({"field":field,"from":record.get(field),"to":c["value"],"confidence":c["confidence"]}); record[field]=c["value"]; changes += 1
        item.update(fetch=fetch,candidates=candidates,selected=selected,applied=applied); reports.append(item)
        print(f"[{'OK' if fetch['ok'] else 'ERROR'}] {record['schoolName']}: {len(selected)} candidates, {len(applied)} applied")
    report_path=cfg.output.parent / "update-candidates.json"; write(report_path,{"generatedAt":now,"targetAdmissionYear":cfg.admission_year,"apply":cfg.apply,"minConfidence":cfg.minimum,"reviewRequired":True,"records":reports})
    write(cfg.output.parent / "check-report.json", {"finishedAt":now,"targetAdmissionYear":cfg.admission_year,
        "checked":len(reports),"retrieved":sum(bool(r["fetch"]["ok"]) for r in reports),
        "failed":sum(not bool(r["fetch"]["ok"]) for r in reports),"appliedFields":changes,
        "note":"取得成功は内容確認を意味しません。更新候補は年度・入試区分の照合が必要です。"})
    if cfg.apply or cfg.refresh:
        data = json.dumps(records,ensure_ascii=False,indent=2 if cfg.pretty else None) + "\n"
        if not cfg.output.exists() or cfg.output.read_text(encoding="utf-8") != data:
            backup(cfg.output); write(cfg.output,records,cfg.pretty)
            print(f"[WRITE] {cfg.output} ({changes} reviewed field changes)")
        else: print(f"[SKIP] {cfg.output}")
    else: print("[DRY RUN] No data changed. Review data/update-candidates.json against the current official admissions guide.")
    print(f"[REPORT] {report_path}"); return 0

if __name__ == "__main__": raise SystemExit(main())
