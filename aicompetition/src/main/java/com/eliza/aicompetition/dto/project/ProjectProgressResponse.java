package com.eliza.aicompetition.dto.project;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectProgressResponse {
    private Long projectId;
    private String projectName;
    private String status;
    private LocalDateTime deadline;
    private Integer requiredTotal;
    private Integer submittedTotal;
    private Integer missingTotal;
    private BigDecimal completionRate;
    private List<String> missingMaterials;
}
