package com.eliza.aicompetition.eval;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic, test-only business evaluator. No production parsing rules are changed. */
final class NoticeEvalScorer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FIELDS = List.of(
        "title", "organizer", "deadline", "targetGroup", "keyPoints", "materials");
    private static final DateTimeFormatter MINUTE = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd HH:mm").toFormatter().withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SECOND = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd HH:mm:ss").toFormatter().withResolverStyle(ResolverStyle.STRICT);

    private NoticeEvalScorer() {}

    static ObjectNode score(JsonNode sample, String rawJson, String origin) {
        ObjectNode report = JSON.createObjectNode();
        report.put("sampleId", sample.path("id").asText());
        report.put("sampleVersion", sample.path("sampleVersion").asText());
        report.put("origin", origin);
        report.put("modelQualityDenominator", "MODEL".equals(origin) ? 1 : 0);
        report.put("baselineOcrPages", sample.path("baselineOcrPages").asInt(0));
        ObjectNode fields = report.putObject("fields");
        ObjectNode materials = report.putObject("materials");
        ArrayNode schemaIssues = report.putArray("schemaIssues");

        JsonNode actual;
        try (JsonParser parser = JSON.createParser(rawJson == null ? "" : rawJson)) {
            actual = JSON.readTree(parser);
            if (actual == null || !actual.isObject()) throw new IllegalArgumentException("ROOT_NOT_OBJECT");
            if (parser.nextToken() != null) throw new IllegalArgumentException("TRAILING_CONTENT");
            report.put("jsonValid", true);
        } catch (Exception invalid) {
            report.put("jsonValid", false);
            report.put("schemaValid", false);
            schemaIssues.add("INVALID_JSON_OR_ROOT");
            unavailable(fields, materials, sample);
            return report;
        }

        for (String field : FIELDS) {
            JsonNode node = actual.get(field);
            if (node == null) {
                schemaIssues.add("MISSING_FIELD:" + field);
            } else if (field.equals("materials")) {
                if (!node.isArray()) schemaIssues.add("WRONG_TYPE:" + field);
            } else if (!node.isNull() && !node.isTextual()) {
                schemaIssues.add("WRONG_TYPE:" + field);
            }
        }
        if (actual.path("materials").isArray()) {
            int index = 0;
            for (JsonNode material : actual.path("materials")) {
                if (!material.isObject()
                    || !material.path("name").isTextual()
                    || material.path("name").asText().isBlank()
                    || !material.has("description")
                    || !(material.path("description").isNull() || material.path("description").isTextual())
                    || !material.path("isRequired").isBoolean()) {
                    schemaIssues.add("INVALID_MATERIAL:" + index);
                }
                index++;
            }
        }
        report.put("schemaValid", schemaIssues.isEmpty());
        compareString(fields, "title", sample.path("expected").path("title"), actual.get("title"));
        compareString(fields, "organizer", sample.path("expected").path("organizer"), actual.get("organizer"));
        compareDeadline(fields, sample.path("expected").path("deadline"), actual.get("deadline"));
        compareMaterials(materials, sample.path("expected").path("materials"), actual.get("materials"));
        return report;
    }

    private static void unavailable(ObjectNode fields, ObjectNode materials, JsonNode sample) {
        for (String field : List.of("title", "organizer", "deadline")) {
            fields.putObject(field).put("status", "FAIL").put("reason", "UNAVAILABLE");
        }
        materials.put("truePositive", 0);
        materials.put("falsePositive", 0);
        materials.put("falseNegative", sample.path("expected").path("materials").size());
        materials.put("precision", 0.0);
        materials.put("recall", 0.0);
        materials.put("f1", 0.0);
        materials.putNull("requiredAccuracy");
        materials.putArray("missing");
        materials.putArray("extra");
    }

    private static void compareString(ObjectNode fields, String name, JsonNode expected, JsonNode actual) {
        ObjectNode out = fields.putObject(name);
        if (expected.path("value").isNull()) {
            out.put("status", "UNSCORED").put("reason", "GOLD_UNCERTAIN");
            return;
        }
        if (actual == null || actual.isNull() || !actual.isTextual()) {
            out.put("status", "FAIL").put("reason", "MISSING_OR_WRONG_TYPE");
            return;
        }
        Set<String> accepted = new HashSet<>();
        accepted.add(normalize(expected.path("value").asText()));
        expected.path("aliases").forEach(alias -> accepted.add(normalize(alias.asText())));
        boolean match = accepted.contains(normalize(actual.asText()));
        out.put("status", match ? "PASS" : "FAIL");
        out.put("reason", match ? "EXACT_OR_DECLARED_ALIAS" : "VALUE_MISMATCH");
    }

    private static void compareDeadline(ObjectNode fields, JsonNode expected, JsonNode actual) {
        ObjectNode out = fields.putObject("deadline");
        if (expected.path("value").isNull()) {
            out.put("status", "UNSCORED").put("reason", "GOLD_UNCERTAIN");
            return;
        }
        if (actual == null || !actual.isTextual()) {
            out.put("status", "FAIL").put("reason", "MISSING_OR_WRONG_TYPE");
            out.put("validDateTime", false);
            return;
        }
        LocalDateTime parsed = parseDateTime(actual.asText());
        out.put("validDateTime", parsed != null);
        if (parsed == null) {
            out.put("status", "FAIL").put("reason", "INVALID_DATE_TIME");
            return;
        }
        LocalDateTime gold = parseDateTime(expected.path("value").asText());
        boolean match = parsed.equals(gold);
        out.put("status", match ? "PASS" : "FAIL");
        out.put("reason", match ? "EXACT_DATE_TIME" : "DATE_OR_TIME_MISMATCH");
    }

    private static LocalDateTime parseDateTime(String value) {
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 16) return LocalDateTime.parse(trimmed, MINUTE);
            if (trimmed.length() == 19) return LocalDateTime.parse(trimmed, SECOND);
        } catch (Exception ignored) {
            // Strict invalid dates/times are scored as failures.
        }
        return null;
    }

    private static void compareMaterials(ObjectNode out, JsonNode gold, JsonNode predicted) {
        if (predicted == null || !predicted.isArray()) {
            out.put("truePositive", 0);
            out.put("falsePositive", 0);
            out.put("falseNegative", gold.size());
            out.put("precision", 0.0);
            out.put("recall", 0.0);
            out.put("f1", 0.0);
            out.putNull("requiredAccuracy");
            ArrayNode missing = out.putArray("missing");
            gold.forEach(item -> missing.add(item.path("name").asText()));
            out.putArray("extra");
            return;
        }
        Map<String, Integer> names = new HashMap<>();
        for (int i = 0; i < gold.size(); i++) {
            JsonNode item = gold.get(i);
            names.put(normalize(item.path("name").asText()), i);
            for (JsonNode alias : item.path("aliases")) {
                names.put(normalize(alias.asText()), i);
            }
        }
        Set<Integer> matched = new HashSet<>();
        List<String> extraNames = new ArrayList<>();
        ArrayNode requiredErrors = out.putArray("requiredErrors");
        int requiredCorrect = 0;
        for (JsonNode item : predicted) {
            String name = item.path("name").isTextual() ? item.path("name").asText() : "<invalid-name>";
            Integer index = names.get(normalize(name));
            if (index == null || !matched.add(index)) {
                extraNames.add(name.length() > 80 ? name.substring(0, 80) : name);
                continue;
            }
            if (item.path("isRequired").isBoolean()) {
                if (item.path("isRequired").asBoolean() == gold.get(index).path("isRequired").asBoolean()) {
                    requiredCorrect++;
                } else {
                    requiredErrors.add(gold.get(index).path("name").asText());
                }
            } else {
                requiredErrors.add(gold.get(index).path("name").asText());
            }
        }
        int tp = matched.size();
        int fp = predicted.size() - tp;
        int fn = gold.size() - tp;
        double precision = predicted.isEmpty() ? (gold.isEmpty() ? 1.0 : 0.0) : (double) tp / predicted.size();
        double recall = gold.isEmpty() ? 1.0 : (double) tp / gold.size();
        out.put("truePositive", tp);
        out.put("falsePositive", fp);
        out.put("falseNegative", fn);
        out.put("precision", precision);
        out.put("recall", recall);
        out.put("f1", precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall));
        if (tp == 0) out.putNull("requiredAccuracy");
        else out.put("requiredAccuracy", (double) requiredCorrect / tp);
        ArrayNode missing = out.putArray("missing");
        for (int i = 0; i < gold.size(); i++) {
            if (!matched.contains(i)) missing.add(gold.get(i).path("name").asText());
        }
        ArrayNode extra = out.putArray("extra");
        extraNames.forEach(extra::add);
    }

    /** NFKC, then remove Unicode whitespace only. No token overlap, edit distance or substring matching. */
    static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder compact = new StringBuilder();
        normalized.codePoints().filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp))
            .forEach(compact::appendCodePoint);
        return compact.toString();
    }
}
