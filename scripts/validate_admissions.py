"""Keep the deployable JSON and classpath fallback in sync with the entry-year policy."""
import argparse
from copy import deepcopy
from datetime import datetime
import json
from pathlib import Path
from urllib.parse import urlparse
from admission_rules import target_admission_year

ROOT = Path(__file__).resolve().parent.parent
FIELDS = ('capacity','examDates','subjects','alternateSubjects','interview','englishQualificationBenefit','notes')

def verified(row, field, year):
    proof = (row.get('fieldVerification') or {}).get(field) or {}
    if proof.get('admissionYear') != year: return False
    try:
        uri = urlparse(proof.get('sourceUrl',''))
        when = datetime.fromisoformat(proof.get('verifiedAt',''))
        return uri.scheme in ('http','https') and bool(uri.hostname) and when.utcoffset() is not None
    except (ValueError, TypeError): return False

def normalize(records, year):
    result = deepcopy(records)
    ids = [r['id'] for r in result]
    if len(ids) != len(set(ids)): raise ValueError('Duplicate school IDs')
    for row in result:
        if not row.get('schoolName'): raise ValueError('School name is required')
        for field in FIELDS:
            if not verified(row,field,year): row[field] = f'{year}年度の公式情報を未確認'
        row['targetAdmissionYear'] = year
        key_facts = verified(row,'examDates',year) and verified(row,'capacity',year)
        row['verificationStatusLabel'] = '日程・人数を確認済み' if key_facts else '一部確認済み' if any(verified(row,f,year) for f in FIELDS) else '未確認'
        if row.get('admissionYear') != year:
            row['verifiedAt'] = ''
            row['verificationNote'] = f'{year}年度の該当入試区分を公式資料で確定できていません。未確認は未公表を意味しません。'
        row.pop('lastCheckedAt',None)
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sync',action='store_true')
    args = parser.parse_args()
    canonical = ROOT/'data/schools-v2.json'
    fallback = ROOT/'src/main/resources/data/schools-v2.json'
    original = json.loads(canonical.read_text(encoding='utf-8'))
    normalized = normalize(original,target_admission_year())
    if args.sync:
        text = json.dumps(normalized,ensure_ascii=False,indent=2)+'\n'
        canonical.write_text(text,encoding='utf-8');fallback.write_text(text,encoding='utf-8')
    else:
        if normalized != original: raise SystemExit('Admissions fields need normalization: run with --sync')
        if json.loads(fallback.read_text(encoding='utf-8')) != original: raise SystemExit('Classpath data differs from canonical data')
    print(f'PASS: {len(normalized)} records, {target_admission_year()} April entry, matching deployable JSON copies')

if __name__ == '__main__': main()
