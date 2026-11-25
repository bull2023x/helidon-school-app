package com.example.myproject;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdmissionsPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private SchoolV2 checkedSchool() throws Exception {
        SchoolV2 school = new SchoolV2();
        school.schoolName="学校A";school.capacity="30名（共通枠）";school.examDates="2026-11-20";
        school.admissionYear=2027;school.verifiedAt="2026-11-25T11:07:10+09:00";
        var proof=json.readTree("{\"admissionYear\":2027,\"sourceUrl\":\"https://school.example/guide.pdf\",\"verifiedAt\":\"2026-11-25T11:07:10+09:00\"}");
        school.fieldVerification=Map.of("capacity",proof,"examDates",proof);
        return school;
    }
    @Test void cohortDoesNotRollOverInJanuary() {
        assertEquals(2027,AdmissionsPolicy.targetYear(LocalDate.of(2026,11,25)));
        assertEquals(2027,AdmissionsPolicy.targetYear(LocalDate.of(2027,1,1)));
        assertEquals(2028,AdmissionsPolicy.targetYear(LocalDate.of(2027,4,1)));
    }
    @Test void anActualSourceIsRequiredForEveryField() throws Exception {
        SchoolV2 school=checkedSchool();
        school.subjects="前年の科目";school.lastRetrievedAt="2026-11-25T13:00:00+09:00";
        SchoolV2 current=AdmissionsPolicy.forYear(school,2027);
        assertEquals("30名（共通枠）",current.capacity);
        assertEquals("日程・人数を確認済み",current.verificationStatusLabel);
        assertTrue(current.subjects.contains("未確認"));
        assertEquals("前年の科目",school.subjects);
        assertTrue(AdmissionsPolicy.forYear(school,2028).capacity.contains("未確認"));
    }
    @Test void legacyFetchDateCannotValidateOldExamDates() throws Exception {
        SchoolV2 school=json.readValue("{\"schoolName\":\"学校A\",\"examDates\":\"2025-11-20\",\"lastCheckedAt\":\"2026-11-25\"}",SchoolV2.class);
        assertTrue(AdmissionsPolicy.forYear(school,2027).examDates.contains("未確認"));
    }
    @Test void legacyEditorKeepsProofOnlyForUnchangedFacts() throws Exception {
        SchoolV2 original=checkedSchool();
        SchoolV2 edited=new SchoolV2();edited.schoolName=original.schoolName;edited.capacity=original.capacity;edited.examDates="2026-12-01";
        AdmissionsPolicy.inheritUnchangedProofs(edited,original);
        assertTrue(AdmissionsPolicy.verified(edited,"capacity",2027));
        assertFalse(AdmissionsPolicy.verified(edited,"examDates",2027));
        SchoolV2 copied=json.convertValue(original,SchoolV2.class);copied.capacity="999名";
        AdmissionsPolicy.inheritUnchangedProofs(copied,original);
        assertFalse(AdmissionsPolicy.verified(copied,"capacity",2027));
    }
    @Test void deviationReferenceSurvivesUnverifiedNotesButExpiresWithTheCohort() throws Exception {
        SchoolV2 original=checkedSchool();
        original.deviationReference=json.readTree("{\"admissionYear\":2027,\"entries\":[{\"value\":54}]}");
        SchoolV2 current=AdmissionsPolicy.forYear(original,2027);
        assertTrue(current.notes.contains("未確認"));
        assertEquals(54,current.deviationReference.path("entries").get(0).path("value").asInt());
        assertNull(AdmissionsPolicy.forYear(original,2028).deviationReference);
        SchoolV2 edited=new SchoolV2();edited.schoolName=original.schoolName;
        AdmissionsPolicy.inheritUnchangedProofs(edited,original);
        assertEquals(original.deviationReference,edited.deviationReference);
    }
}
