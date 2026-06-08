package com.eliza.aicompetition.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured result from the notice-parse LLM call.
 * Jackson deserializes the LLM JSON response into this record.
 */
public record AiParseResult(
    @JsonProperty("organizer") String organizer,
    @JsonProperty("deadline") String deadline,
    @JsonProperty("targetGroup") String targetGroup,
    @JsonProperty("keyPoints") String keyPoints,
    @JsonProperty("materials") List<AiMaterialRequirement> materials
) {
    public record AiMaterialRequirement(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("isRequired") boolean isRequired
    ) {}
}
