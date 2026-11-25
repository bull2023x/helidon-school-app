"""Admissions year is the April entry year, not the year a website was fetched."""
from datetime import datetime
import re
import unicodedata
from zoneinfo import ZoneInfo


def target_admission_year(now=None):
    now = now or datetime.now(ZoneInfo("Asia/Tokyo"))
    return now.year + (now.month >= 4)


def source_admission_year(text):
    # Only a leading admissions heading establishes a document year. A footer,
    # copyright year, publication date or year in a URL is insufficient.
    head = re.sub(r"\s+", "", unicodedata.normalize("NFKC", text[:3000]))
    match = re.search(r"(20\d{2})(?:[（(]令和\d+[)）])?年度|令和(\d{1,2})年度", head[:1500])
    if not match: return None
    return int(match[1]) if match[1] else 2018 + int(match[2])
