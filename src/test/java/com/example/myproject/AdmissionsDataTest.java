package com.example.myproject;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdmissionsDataTest {
    @Test void canonicalAndClasspathDataMatchAndContainCurrentReviewedFacts() throws Exception {
        var json=new ObjectMapper();
        var canonical=json.readTree(Path.of("data/schools-v2.json").toFile());
        try(var resource=getClass().getClassLoader().getResourceAsStream("data/schools-v2.json")) {
            assertNotNull(resource);assertEquals(canonical,json.readTree(resource));
        }
        assertEquals(110,canonical.size());
        assertEquals("2026-11-23, 2026-12-16",canonical.get(96).path("examDates").asText());
        DbInit.init();
        var school=new SchoolV2Repository().findById(97);
        assertNotNull(school);
        assertEquals(canonical.get(96).path("fieldVerification").path("examDates"),school.fieldVerification.get("examDates"));
        assertEquals(AdmissionsPolicy.targetYear(),school.targetAdmissionYear);
        if(AdmissionsPolicy.targetYear()==2027)assertEquals("2026-11-23, 2026-12-16",school.examDates);
    }
}
