import sys, unittest
from pathlib import Path
from datetime import datetime
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from admission_rules import target_admission_year, source_admission_year
from update_schools import extract
from validate_admissions import normalize
import json, tempfile, contextlib, io
from unittest.mock import patch
import update_schools

class AdmissionYearTest(unittest.TestCase):
    def test_cohort_rolls_over_in_april(self):
        self.assertEqual(target_admission_year(datetime(2026,11,25)),2027)
        self.assertEqual(target_admission_year(datetime(2027,1,1)),2027)
        self.assertEqual(target_admission_year(datetime(2027,4,1)),2028)

    def test_old_guide_cannot_be_validated_by_a_new_footer_or_url(self):
        text='2026年度 帰国生入試 試験日 2025年11月20日 募集人員35名 '+('説明 '*800)+'2027年度ニュース'
        self.assertEqual(source_admission_year(text),2026)
        self.assertEqual(extract('https://school.example/2027.pdf','pdf',text,'帰国入試',2027),[])

    def test_japanese_era_and_fullwidth_year(self):
        self.assertEqual(source_admission_year('令和９年度 生徒募集要項'),2027)
        self.assertEqual(source_admission_year('２０２７年度 生徒募集要項'),2027)

    def test_current_candidates_still_require_exam_round_review(self):
        candidates=extract('https://school.example/guide.pdf','pdf','2027年度 帰国生入試 試験日 2026年11月20日 募集人員30名','帰国入試',2027)
        self.assertTrue(candidates)
        self.assertTrue(all(c['admissionYear']==2027 and c['requiresReview'] for c in candidates))

    def test_fetch_timestamp_cannot_validate_last_years_admissions(self):
        row={'id':1,'schoolName':'学校A','examDates':'2025-11-20','capacity':'35名','lastCheckedAt':'2026-11-25'}
        updated=normalize([row],2027)[0]
        self.assertIn('未確認',updated['examDates']);self.assertIn('未確認',updated['capacity'])
        self.assertNotIn('lastCheckedAt',updated)
        self.assertEqual(row['examDates'],'2025-11-20')

    def test_source_proof_expires_with_the_entry_cohort(self):
        row={'id':1,'schoolName':'学校A','capacity':'30名','admissionYear':2027,'fieldVerification':{'capacity':{'admissionYear':2027,'sourceUrl':'https://school.example/guide.pdf','verifiedAt':'2026-11-25T11:07:10+09:00'}}}
        self.assertEqual(normalize([row],2027)[0]['capacity'],'30名')
        self.assertIn('未確認',normalize([row],2028)[0]['capacity'])

    def test_refresh_saves_retrieval_without_overwriting_reviewed_facts(self):
        fixture=json.loads((Path(__file__).resolve().parents[1]/'data/schools-v2.json').read_text(encoding='utf-8'))[0]
        with tempfile.TemporaryDirectory() as temp:
            target=Path(temp)/'data/schools-v2.json';target.parent.mkdir()
            target.write_text(json.dumps([fixture],ensure_ascii=False),encoding='utf-8')
            command=['update_schools','--input',str(target),'--output',str(target),'--refresh','--apply','--pretty','--admission-year','2027']
            page=[('https://school.example/guide.pdf','pdf','2027年度 帰国生入試 試験日 2026年12月31日 募集人員999名')]
            with patch.object(sys,'argv',command),patch.object(update_schools,'sources',return_value=(page,{'ok':True})),contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(update_schools.main(),0)
            actual=json.loads(target.read_text(encoding='utf-8'))[0]
            self.assertIn('lastRetrievedAt',actual)
            for field in ['capacity','examDates','verifiedAt','fieldVerification']:
                self.assertEqual(actual[field],fixture[field])
            report=json.loads((target.parent/'update-candidates.json').read_text(encoding='utf-8'))
            self.assertEqual(report['targetAdmissionYear'],2027)
            self.assertTrue(report['records'][0]['candidates'])
            self.assertEqual(report['records'][0]['applied'],[])

if __name__ == '__main__': unittest.main()
