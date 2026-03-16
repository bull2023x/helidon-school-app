package com.example.myproject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class DbInit {
    public static void init() {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "");
             Statement stmt = conn.createStatement()) {

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

            loadSchoolV2FromJson(stmt);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadSchoolV2FromJson(Statement stmt) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream is = DbInit.class.getClassLoader()
                .getResourceAsStream("data/schools-v2.json")) {

            if (is == null) {
                throw new RuntimeException("data/schools-v2.json not found");
            }

            List<Map<String, Object>> schools = mapper.readValue(
                    is,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            for (Map<String, Object> school : schools) {
                insertV2(stmt,
                        asString(school.get("schoolName")),
                        asString(school.get("category")),
                        asString(school.get("capacity")),
                        asString(school.get("examDates")),
                        asString(school.get("subjects")),
                        asString(school.get("alternateSubjects")),
                        asString(school.get("interview")),
                        asString(school.get("englishQualificationBenefit")),
                        asString(school.get("notes")),
                        asString(school.get("infoLink"))
                );
            }
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
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
