package com.example.myproject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SchoolV2 {
    public long id;
    public String schoolName;
    public String category;
    public String capacity;
    public String examDates;
    public String subjects;
    public String alternateSubjects;
    public String interview;
    public String englishQualificationBenefit;
    public String notes;
    public String infoLink;
    public Integer admissionYear;
    public Integer targetAdmissionYear;
    public String verifiedAt;
    public String lastRetrievedAt;
    public String verificationStatusLabel;
    public String verificationNote;
    public Map<String, JsonNode> fieldVerification = Map.of();
    public List<JsonNode> admissionsSources = List.of();

    public SchoolV2() {
        // Jackson 用
    }

    public SchoolV2(long id,
                    String schoolName,
                    String category,
                    String capacity,
                    String examDates,
                    String subjects,
                    String alternateSubjects,
                    String interview,
                    String englishQualificationBenefit,
                    String notes,
                    String infoLink) {
        this.id = id;
        this.schoolName = schoolName;
        this.category = category;
        this.capacity = capacity;
        this.examDates = examDates;
        this.subjects = subjects;
        this.alternateSubjects = alternateSubjects;
        this.interview = interview;
        this.englishQualificationBenefit = englishQualificationBenefit;
        this.notes = notes;
        this.infoLink = infoLink;
    }
}
