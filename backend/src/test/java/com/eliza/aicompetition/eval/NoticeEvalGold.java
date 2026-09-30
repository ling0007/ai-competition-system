package com.eliza.aicompetition.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Validates the hand-reviewed gold before any paid call. Paths are repo-relative. */
final class NoticeEvalGold {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path GOLD = REPO.resolve("docs/testdata/eval/notice-gold-v1.json");

    private NoticeEvalGold() {}

    static JsonNode load() throws Exception {
        JsonNode catalog = JSON.readTree(Files.readString(GOLD));
        if (catalog.path("schemaVersion").asInt() != 1
            || !"notice-gold-v1".equals(catalog.path("expectedVersion").asText())
            || catalog.path("samples").size() != 3) {
            throw new IllegalStateException("Gold schema/version/sample count changed");
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode sample : catalog.path("samples")) {
            String id = sample.path("id").asText();
            if (!ids.add(id) || sample.path("sampleVersion").asText().isBlank()
                || sample.path("evaluatedScope").asText().isBlank()) {
                throw new IllegalStateException("Invalid sample metadata: " + id);
            }
            Path file = REPO.resolve(sample.path("path").asText()).normalize();
            if (!file.startsWith(REPO.resolve("docs/testdata")) || !Files.isRegularFile(file)) {
                throw new IllegalStateException("Sample path missing or outside testdata: " + id);
            }
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file)));
            if (!actual.equalsIgnoreCase(sample.path("sha256").asText())) {
                throw new IllegalStateException("Sample SHA-256 changed: " + id);
            }
            if (!sample.path("sampleVersion").asText().equalsIgnoreCase("sha256:" + actual.substring(0, 12))) {
                throw new IllegalStateException("Sample version disagrees with SHA-256: " + id);
            }
            JsonNode expected = sample.path("expected");
            for (String field : new String[] {"title", "organizer", "deadline"}) {
                if (!expected.path(field).has("value")
                    || expected.path(field).path("evidence").asText().isBlank()) {
                    throw new IllegalStateException("Missing gold evidence: " + id + "/" + field);
                }
            }
            Set<String> names = new HashSet<>();
            for (JsonNode material : expected.path("materials")) {
                if (!material.path("isRequired").isBoolean()
                    || material.path("evidence").asText().isBlank()) {
                    throw new IllegalStateException("Invalid gold material: " + id);
                }
                if (!names.add(NoticeEvalScorer.normalize(material.path("name").asText()))) {
                    throw new IllegalStateException("Duplicate gold material: " + id);
                }
                for (JsonNode alias : material.path("aliases")) {
                    String normalized = NoticeEvalScorer.normalize(alias.asText());
                    if (!names.add(normalized)) {
                        throw new IllegalStateException("Duplicate gold alias: " + id);
                    }
                }
            }
        }
        return catalog;
    }

    static Path samplePath(JsonNode sample) {
        return REPO.resolve(sample.path("path").asText()).normalize();
    }
}
