package com.eliza.aicompetition.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeEvalScorerTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonNode campus() throws Exception {
        return NoticeEvalGold.load().path("samples").get(0);
    }

    private String correct() {
        return """
            {"title":"关于开展 2027 年校园创新项目申报的通知",
             "organizer":"校园创新实践中心","deadline":"2027-06-30 18:00",
             "targetGroup":"全日制在校本科生团队","keyPoints":"项目申报",
             "materials":[
               {"name":"项目申报书","description":"项目方案","isRequired":true},
               {"name":"指导教师意见表","description":"教师意见","isRequired":true}]}
            """;
    }

    @Test
    void goldIsVersionedAndMatchesOriginalFileBytes() throws Exception {
        assertEquals(3, NoticeEvalGold.load().path("samples").size());
    }

    @Test
    void exactGoldHasPerfectBusinessScoreAndPreservesEighteenHundred() throws Exception {
        JsonNode report = NoticeEvalScorer.score(campus(), correct(), "MODEL");
        assertTrue(report.path("jsonValid").asBoolean());
        assertTrue(report.path("schemaValid").asBoolean());
        assertEquals("PASS", report.path("fields").path("title").path("status").asText());
        assertEquals("PASS", report.path("fields").path("organizer").path("status").asText());
        assertEquals("PASS", report.path("fields").path("deadline").path("status").asText());
        assertEquals(1.0, report.path("materials").path("precision").asDouble());
        assertEquals(1.0, report.path("materials").path("recall").asDouble());
        assertEquals(1.0, report.path("materials").path("requiredAccuracy").asDouble());
    }

    @Test
    void invalidJsonAndMissingFieldsDoNotLookLikeValidStructure() throws Exception {
        JsonNode invalid = NoticeEvalScorer.score(campus(), "{broken", "MODEL");
        assertFalse(invalid.path("jsonValid").asBoolean());
        assertFalse(invalid.path("schemaValid").asBoolean());
        assertEquals(1, invalid.path("modelQualityDenominator").asInt());
        assertFalse(NoticeEvalScorer.score(campus(), correct() + " trailing", "MODEL")
            .path("jsonValid").asBoolean());

        JsonNode missing = NoticeEvalScorer.score(campus(), "{\"title\":\"x\",\"materials\":[]}", "MODEL");
        assertTrue(missing.path("jsonValid").asBoolean());
        assertFalse(missing.path("schemaValid").asBoolean());
        assertTrue(missing.path("schemaIssues").toString().contains("MISSING_FIELD:deadline"));
    }

    @Test
    void wrongClockTimeAndImpossibleDateFailSeparately() throws Exception {
        JsonNode wrongTime = NoticeEvalScorer.score(campus(),
            correct().replace("18:00", "23:59"), "MODEL");
        assertEquals("DATE_OR_TIME_MISMATCH",
            wrongTime.path("fields").path("deadline").path("reason").asText());
        assertTrue(wrongTime.path("fields").path("deadline").path("validDateTime").asBoolean());

        JsonNode impossible = NoticeEvalScorer.score(campus(),
            correct().replace("2027-06-30", "2027-02-30"), "MODEL");
        assertEquals("INVALID_DATE_TIME",
            impossible.path("fields").path("deadline").path("reason").asText());
    }

    @Test
    void missingDuplicateAndInventedMaterialsChangePrecisionAndRecall() throws Exception {
        ObjectNode missing = (ObjectNode) JSON.readTree(correct());
        ((ArrayNode) missing.path("materials")).remove(1);
        JsonNode missingScore = NoticeEvalScorer.score(campus(), missing.toString(), "MODEL").path("materials");
        assertEquals(1.0, missingScore.path("precision").asDouble());
        assertEquals(0.5, missingScore.path("recall").asDouble());
        assertEquals(1, missingScore.path("falseNegative").asInt());

        ObjectNode duplicate = (ObjectNode) JSON.readTree(correct());
        ((ArrayNode) duplicate.path("materials")).add(duplicate.path("materials").get(0));
        JsonNode duplicateScore = NoticeEvalScorer.score(campus(), duplicate.toString(), "MODEL").path("materials");
        assertEquals(2, duplicateScore.path("truePositive").asInt());
        assertEquals(1, duplicateScore.path("falsePositive").asInt());

        ObjectNode invented = (ObjectNode) JSON.readTree(correct());
        ((ArrayNode) invented.path("materials")).add(JSON.readTree(
            "{\"name\":\"身份证复印件\",\"description\":\"\",\"isRequired\":true}"));
        JsonNode inventedScore = NoticeEvalScorer.score(campus(), invented.toString(), "MODEL").path("materials");
        assertEquals(1, inventedScore.path("falsePositive").asInt());
        assertTrue(inventedScore.path("extra").toString().contains("身份证复印件"));
    }

    @Test
    void requiredFlagMismatchAndWrongTypesAreVisible() throws Exception {
        ObjectNode prediction = (ObjectNode) JSON.readTree(correct());
        ((ObjectNode) prediction.path("materials").get(0)).put("isRequired", false);
        ((ObjectNode) prediction.path("materials").get(1)).put("description", 42);
        JsonNode report = NoticeEvalScorer.score(campus(), prediction.toString(), "MODEL");
        assertFalse(report.path("schemaValid").asBoolean());
        assertEquals(0.5, report.path("materials").path("requiredAccuracy").asDouble());
        assertTrue(report.path("schemaIssues").toString().contains("INVALID_MATERIAL:1"));
    }

    @Test
    void fallbackAndNoneNeverEnterModelQualityDenominator() throws Exception {
        assertEquals(0, NoticeEvalScorer.score(campus(), correct(), "FALLBACK")
            .path("modelQualityDenominator").asInt());
        assertEquals(0, NoticeEvalScorer.score(campus(), null, "NONE")
            .path("modelQualityDenominator").asInt());
    }

    @Test
    void normalizationDoesNotAcceptSubstringsOrEditDistance() throws Exception {
        assertEquals("西门子杯", NoticeEvalScorer.normalize(" 西 门 子 杯 "));
        assertEquals("FAIL", NoticeEvalScorer.score(campus(),
            correct().replace("关于开展 2027 年校园创新项目申报的通知", "校园创新项目申报"), "MODEL")
            .path("fields").path("title").path("status").asText());
    }

    @Test
    void writesCompactClearlySyntheticOfflineExample() throws Exception {
        ObjectNode prediction = (ObjectNode) JSON.readTree(correct().replace("18:00", "23:59"));
        ((ArrayNode) prediction.path("materials")).remove(1);
        ObjectNode report = NoticeEvalScorer.score(campus(), prediction.toString(), "MODEL");
        report.put("exampleType", "SYNTHETIC_OFFLINE_NOT_DASHSCOPE");
        report.put("expectedVersion", "notice-gold-v1");
        Path output = Path.of("target/ai-eval/offline-example.json");
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        assertEquals("DATE_OR_TIME_MISMATCH",
            report.path("fields").path("deadline").path("reason").asText());
    }
}
