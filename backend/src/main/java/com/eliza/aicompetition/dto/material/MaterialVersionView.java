package com.eliza.aicompetition.dto.material;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 材料历史版本只读视图。历史审核绑定具体版本，不参与当前版本状态推导。
 */
@Data
public class MaterialVersionView {
    private Long materialId;
    private Long requirementId;
    private String requirementName;
    private Long fileId;
    private String fileName;
    private String fileExt;
    private Long fileSize;
    private Integer versionNo;
    private String remark;
    private LocalDateTime submittedAt;
    private Boolean currentVersion;
    private String reviewStatus;
    private String reviewComment;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
}
