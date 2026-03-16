package com.example.myproject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DbInit {
    public static void init() {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

            // Old table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exam_school (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    exam_date VARCHAR(100),
                    school_name VARCHAR(200),
                    class_name VARCHAR(200),
                    capacity VARCHAR(50),
                    subjects CLOB,
                    english_only VARCHAR(100),
                    application_deadline_docs CLOB,
                    benefits CLOB,
                    remarks CLOB
                )
            """);

            insert(stmt,
                    "1/10 AM",
                    "開智所沢",
                    "英語型入試",
                    "若干名",
                    "英語(120)、国語(100)、算数(120)\nEssay Writing Only",
                    "No but easy mode.",
                    "1/10朝\nネット出願",
                    "TOEFL72点以上で、英語特典保証100点。\n"
                            + "当日の試験の得点を比較し、高い方の得点を合否判定で使用します。\n"
                            + "英語の試験終了後に合格証・スコア等のコピーを回収します。\n"
                            + "一般入試とは合格基準が異なります\n"
                            + "系列校全ての回の受験が可能\n"
                            + "●複数校の合否判定時にも追加受験料は不要",
                    "第２回入試、特待Ｂ入試では、複数回受験時に加点制度があります。\n"
                            + "【第１回＋第２回】➡第２回入試の一般合格判定時に30点加点\n"
                            + "【第１回＋特待Ｂ】【第２回＋特待Ｂ】【第１回＋第２回＋特待Ｂ】➡特待Ｂ入試の一般合格判定時に30点加点"
            );

            insert(stmt,
                    "1/20 AM",
                    "渋幕",
                    "帰国生",
                    "20",
                    "英語（筆記＆エッセイ）、面接",
                    "Yes",
                    "1/10 15時\n"
                            + "本校ホームページ(www.shibumaku.jp)からインターネット出願サイトにアクセスし、"
                            + "上記期間内に入力と受験料の納入を完了してください。",
                    "1/10消印で送付。\n①帰国生カード\n②プリエッセイ",
                    ""
            );

            insert(stmt,
                    "2/1 AM",
                    "芝国際",
                    "国際Advanced",
                    "10",
                    "Essay、面接、国算 （面接は11:50〜）",
                    "No but easy mode.",
                    "1/31 ネット出願",
                    "TOEFL72以上で60点満点確保",
                    "英語（書類・エッセイ・面接）\n国語\n算数（英語表記あり）"
            );

            insert(stmt,
                    "2/1 PM （集合14:30）",
                    "文化学園杉並",
                    "英語特別",
                    "50",
                    "英語はLRW。国 or 算。",
                    "No but easy mode.",
                    "1/30\n"
                            + "成績通知表のコピー（出欠席の日数が記載されているページを含んだもの）\n"
                            + "（3学期制の場合は2学期末、2学期制の場合は前期のものをご家庭でコピーしてください）\n"
                            + "試験日前日までに到着しない可能性がある場合は、郵送せずに試験当日お持ちください。",
                    "資格特典はなし。但し、合格後の特待認定では使う。英語は準２級程度。面接なし。",
                    "英語特別入試（1日PM・2日PM）◆合格点（目安）\n"
                            + "・国語または算数を選択した場合 英語70点＋国語/算数50点\n"
                            + "◆英語の問題のレベル 英検2級程度を想定して作成\n"
                            + "昨年度参考（平均）準２級：40.1点（6人） ２級：64点（20人） 準１級：85.4点（9人）"
            );

            insert(stmt,
                    "2/2 AM",
                    "かえつ有明",
                    "Honor /Advance",
                    "15",
                    "日本語作文、英語筆記、英語作文（面接なし）",
                    "Yes",
                    "1/27\n"
                            + "①志望理由書…本校ホームページからダウンロードしてください。\n"
                            + "②直近1年間分の成績表のコピー\n"
                            + "上記2点を特定記録郵便でお送りください。",
                    "",
                    ""
            );

            insert(stmt,
                    "2/2 PM",
                    "三田国際",
                    "ISC",
                    "15",
                    "国算、面接",
                    "No but easy mode.",
                    "2/3 朝\nネット出願 出願時に志望するクラス・選考方法を選択",
                    "TOEFL72以上で英語試験免除。",
                    ""
            );

            insert(stmt,
                    "2/3 AM〜夕方",
                    "学芸国際",
                    "A方式",
                    "30",
                    "Essay、日本語作文、面接",
                    "Yes",
                    "1/8必着：８種類の書類。\n"
                            + "令和6（2024）年4月が含まれる学年から現在在籍している学年までの全ての期間の成績を証明する書類（報告書・成績証明書等）\n"
                            + "・報告書（様式４）…日本の小学校および日本人学校小学部の成績についてのみ提出。\n"
                            + "本校所定の用紙（様式４）に記入を依頼し、厳封してもらってください。必要な場合は用紙をコピーして使用してください。",
                    "外国語作文・基礎日本語作文・面接\n"
                            + "外国語作文の言語は、出願時に志願者が、英語・フランス語・ドイツ語・スペイン語・中国語・韓国／朝鮮語のうちから一つ選択する。",
                    "12：50〜16：30（予定）"
            );

            insert(stmt,
                    "2/4 AM",
                    "開智日本橋",
                    "GLC",
                    "20",
                    "国算、Esseyと面接",
                    "No but easy mode.",
                    "2/2 ネット出願",
                    "なし",
                    ""
            );

            insert(stmt,
                    "2/5 AM",
                    "サレジアン国際",
                    "第６回",
                    "150",
                    "Essayと面接のみ（筆記は満点換算。）",
                    "Yes",
                    "2/2 ネット出願",
                    "TOEFL72点以上。満点換算。１から６回で合計150名。",
                    ""
            );

            insert(stmt,
                    "2/6 AM",
                    "江戸川女子",
                    "英語特化型",
                    "200",
                    "全試験で200名。英語と面接。",
                    "Yes",
                    "2/6 朝\n"
                            + "① 受験票\n"
                            + "② 志願票\n"
                            + "③ 小学校６年生の１学期または前期の通知表（表裏含む全ページ）のコピー\n"
                            + "※②・③は入試当日に提出していただきます",
                    "英検２級程度との事。\n"
                            + "●外部団体の学力データの内容は問いません。\n"
                            + "●複数の学力データを提出された場合は、加点数の高い１種類を採用します。\n"
                            + "●すべての入試で適用します。\n"
                            + "●学力データを示す資料（コピー）は入試当日の試験会場の教室で回収します。\n"
                            + "●複数回受験をする場合は、学力データを示す資料（コピー）を試験ごとに１部ずつ提出してください。",
                    ""
            );

            // New table
stmt.execute("""
    CREATE TABLE IF NOT EXISTS exam_school_v2 (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        school_name VARCHAR(300),
        category VARCHAR(300),
        capacity VARCHAR(100),
        exam_dates CLOB,
        subjects CLOB,
        alternate_subjects CLOB,
        interview VARCHAR(100),
        english_qualification_benefit CLOB,
        notes CLOB,
        info_link CLOB
    )
""");

insertV2(stmt,
        "渋谷教育学園渋谷（渋渋）",
        "帰国入試",
        "計12名",
        "2026-01-27",
        "英語, 国語, 算数, 英語面接",
        "作文, 国語, 算数, 面接",
        "あり",
        "公式上、免除・得点保証の明示なし",
        null,
        "https://www.shibushibu.jp/admission/detail/returnee_exam.html"
);

insertV2(stmt,
        "洗足学園",
        "帰国入試 A方式",
        "20名の内訳あり",
        "2026-01-10",
        "英語, 面接（英語での質疑応答）",
        null,
        "あり",
        "公式上、免除・得点保証の明示なし",
        null,
        "https://www.senzoku-gakuen.ed.jp/admission/information_3.html"
);

insertV2(stmt,
        "洗足学園",
        "帰国入試 B方式",
        "20名の内訳あり",
        "2026-01-10",
        "英語, 国語, 算数, 面接（英語での質疑応答）",
        null,
        "あり",
        "公式上、免除・得点保証の明示なし",
        null,
        "https://www.senzoku-gakuen.ed.jp/admission/information_3.html"
);

insertV2(stmt,
        "東京学芸大学附属国際中等教育学校",
        "A方式",
        "約30名",
        "2026-02-03",
        "外国語作文, 基礎日本語作文, 面接",
        null,
        "あり",
        "免除・得点保証の明示なし",
        "外国語作文は英語ほか選択",
        "https://www.iss.oizumi.u-gakugei.ac.jp/2025/14096/"
);

insertV2(stmt,
        "広尾学園",
        "帰国生入試（インターAG）",
        "25名",
        "2025-12-18",
        "Japanese, Mathematics, English, Interview",
        null,
        "あり",
        "英検2級相当以上が出願条件、TOEFL iBT 90以上でEnglish免除",
        null,
        "https://www.hiroogakuen.ed.jp/sp/junior/jik_boshuyoko.html"
);

insertV2(stmt,
        "広尾学園",
        "一般英語受験（国際生AG回）",
        "別枠あり",
        "2026-02-03",
        "Japanese, Mathematics, English, Interview",
        null,
        "あり",
        "TOEFL iBT 90以上でEnglish免除",
        null,
        "https://www.hiroogakuen.ed.jp/sp/junior/jik_boshuyoko.html"
);

insertV2(stmt,
        "広尾学園小石川",
        "帰国生入試（AG）",
        "25名",
        "2025-11-23, 2025-12-15",
        "Japanese, Mathematics, English, Interview",
        null,
        "あり",
        "英検2級以上または同等英語力、TOEFL iBT 90以上でEnglish免除",
        null,
        "https://hiroo-koishikawa.ed.jp/exam/junior-nyushi"
);

insertV2(stmt,
        "広尾学園小石川",
        "一般英語受験（国際生AG回）",
        "約30名",
        "2026-02-01, 2026-02-03, 2026-02-06",
        "Japanese, Mathematics, English, Interview",
        null,
        "あり",
        "TOEFL iBT 90以上でEnglish免除",
        null,
        "https://hiroo-koishikawa.ed.jp/exam/junior-nyushi"
);

insertV2(stmt,
        "頌栄女子学院",
        "帰国生入試",
        "特に定めず",
        "2025-12-06",
        "英語I, 英語II, 英会話, 面接",
        "国語, 算数, 英語I, 英語II, 英会話, 面接",
        "あり",
        "帰国生入試では免除・得点保証の明示なし",
        null,
        "https://www.shoei.ed.jp/examination/returnees.html"
);

insertV2(stmt,
        "頌栄女子学院",
        "一般英語利用入試",
        "一般枠内",
        "2026-02-01, 2026-02-05",
        "国語, 算数, 英語（英検のみなし得点）",
        null,
        "なし",
        "英検3級以上が条件、英語はみなし得点方式",
        null,
        "https://www.shoei.ed.jp/examination/returnees.html"
);

insertV2(stmt,
        "三田国際科学学園",
        "帰国生入試 IC",
        "30名",
        "2025-11-21, 2025-12-10",
        "英語（リスニング含む）, 面接",
        null,
        "あり",
        "ICは優遇明記なし",
        null,
        "https://www.mita-is.ed.jp/admissions/returnee/"
);

insertV2(stmt,
        "三田国際科学学園",
        "帰国生入試 ISC",
        "30名",
        "2025-11-21, 2025-12-10",
        "英語（リスニング含む）, 国語, 算数, 面接",
        null,
        "あり",
        "英検準1級以上 / TOEFL iBT 72以上 / IELTS 5.5以上で英語試験免除",
        null,
        "https://www.mita-is.ed.jp/admissions/returnee/"
);

insertV2(stmt,
        "三田国際科学学園",
        "一般英語受験（2月 ISC 英語【優遇措置】）",
        "一般枠内",
        "2月入試各回",
        "一般入試でISCを選択, 英語【優遇措置】を利用",
        null,
        "要項参照",
        "英検準1級以上 / TOEFL iBT 72以上 / IELTS 5.5以上で優遇措置",
        null,
        "https://www.mita-is.ed.jp/admissions/regular/"
);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insert(Statement stmt,
                               String examDate,
                               String schoolName,
                               String className,
                               String capacity,
                               String subjects,
                               String englishOnly,
                               String applicationDeadlineDocs,
                               String benefits,
                               String remarks) throws Exception {
        stmt.execute("INSERT INTO exam_school ("
                + "exam_date, school_name, class_name, capacity, "
                + "subjects, english_only, application_deadline_docs, benefits, remarks"
                + ") VALUES ("
                + q(examDate) + ", "
                + q(schoolName) + ", "
                + q(className) + ", "
                + q(capacity) + ", "
                + q(subjects) + ", "
                + q(englishOnly) + ", "
                + q(applicationDeadlineDocs) + ", "
                + q(benefits) + ", "
                + q(remarks)
                + ")");
    }

private static void insertV2(Statement stmt,
                             String schoolName,
                             String category,
                             String capacity,
                             String examDates,
                             String subjects,
                             String alternateSubjects,
                             String interview,
                             String englishQualificationBenefit,
                             String notes,
                             String infoLink) throws Exception {
    stmt.execute("INSERT INTO exam_school_v2 ("
            + "school_name, category, capacity, exam_dates, "
            + "subjects, alternate_subjects, interview, "
            + "english_qualification_benefit, notes, info_link"
            + ") VALUES ("
            + q(schoolName) + ", "
            + q(category) + ", "
            + q(capacity) + ", "
            + q(examDates) + ", "
            + q(subjects) + ", "
            + q(alternateSubjects) + ", "
            + q(interview) + ", "
            + q(englishQualificationBenefit) + ", "
            + q(notes) + ", "
            + q(infoLink)
            + ")");
}

    private static String q(String s) {
        if (s == null) {
            return "NULL";
        }
        return "'" + s.replace("'", "''") + "'";
    }
}
