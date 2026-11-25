package com.example.myproject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** The April entry cohort and a checked official source establish currency, not a fetch date. */
public final class AdmissionsPolicy {
    public static final List<String> FIELDS = List.of("capacity", "examDates", "subjects", "alternateSubjects",
            "interview", "englishQualificationBenefit", "notes");
    private static final ObjectMapper JSON = new ObjectMapper();
    private AdmissionsPolicy() { }

    public static int targetYear() { return targetYear(LocalDate.now(ZoneId.of("Asia/Tokyo"))); }
    public static int targetYear(LocalDate now) { return now.getYear() + (now.getMonthValue() >= 4 ? 1 : 0); }

    public static boolean verified(SchoolV2 school, String field, int year) {
        if (school.fieldVerification == null) return false;
        JsonNode proof = school.fieldVerification.get(field);
        if (proof == null || proof.path("admissionYear").asInt() != year) return false;
        try {
            URI uri = URI.create(proof.path("sourceUrl").asText(""));
            OffsetDateTime.parse(proof.path("verifiedAt").asText(""));
            return uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()));
        } catch (RuntimeException invalidProof) { return false; }
    }

    public static SchoolV2 current(SchoolV2 source) { return forYear(source, targetYear()); }

    /** Legacy editors may omit metadata; retain proof only for facts they did not change. */
    public static void inheritUnchangedProofs(SchoolV2 incoming, SchoolV2 existing) {
        if (existing == null) return;
        var proofs = new java.util.LinkedHashMap<String, JsonNode>();
        if (incoming.fieldVerification != null) proofs.putAll(incoming.fieldVerification);
        JsonNode before = JSON.valueToTree(existing), after = JSON.valueToTree(incoming);
        for (String field : FIELDS) {
            JsonNode oldProof = existing.fieldVerification == null ? null : existing.fieldVerification.get(field);
            if (before.path(field).equals(after.path(field))) {
                if (!proofs.containsKey(field) && oldProof != null) proofs.put(field, oldProof);
            } else if (oldProof != null && oldProof.equals(proofs.get(field))) {
                proofs.remove(field);
            }
        }
        incoming.fieldVerification = proofs;
        if (incoming.admissionYear == null) incoming.admissionYear = existing.admissionYear;
        if (incoming.verifiedAt == null) incoming.verifiedAt = existing.verifiedAt;
        if (incoming.admissionsSources == null || incoming.admissionsSources.isEmpty()) incoming.admissionsSources = existing.admissionsSources;
    }

    public static SchoolV2 forYear(SchoolV2 source, int year) {
        var row = JSON.valueToTree(source);
        int count = 0;
        for (String field : FIELDS) {
            if (verified(source, field, year)) count++;
            else ((com.fasterxml.jackson.databind.node.ObjectNode) row).put(field, year + "年度の公式情報を未確認");
        }
        SchoolV2 result = JSON.convertValue(row, SchoolV2.class);
        result.targetAdmissionYear = year;
        result.verificationStatusLabel = verified(source,"examDates",year) && verified(source,"capacity",year)
                ? "日程・人数を確認済み" : count > 0 ? "一部確認済み" : "未確認";
        result.verifiedAt = FIELDS.stream().filter(field -> verified(source,field,year))
                .map(field -> OffsetDateTime.parse(source.fieldVerification.get(field).path("verifiedAt").asText()))
                .max(java.util.Comparator.naturalOrder()).map(OffsetDateTime::toString).orElse(null);
        if (count > 0) result.admissionYear = year;
        if (count == 0 && (source.admissionYear == null || source.admissionYear != year)) {
            result.verificationNote = year + "年度の該当入試区分を公式資料で確定できていません。未確認は未公表を意味しません。";
        }
        result.admissionsSources = source.admissionsSources == null ? List.of() : source.admissionsSources.stream()
                .filter(s -> s.path("admissionYear").asInt() == year).toList();
        return result;
    }
}
