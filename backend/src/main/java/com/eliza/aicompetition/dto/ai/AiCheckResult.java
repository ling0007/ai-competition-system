package com.eliza.aicompetition.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured result from the material-check LLM call.
 */
public record AiCheckResult(
    @JsonProperty("reviewResult") String reviewResult,
    @JsonProperty("reviewComment") String reviewComment
) {
    // reviewResult is one of: "pass", "warning", "reject"
    // reviewComment is detailed Chinese feedback text
}
