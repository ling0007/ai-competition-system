package com.eliza.aicompetition.dto.project;

import lombok.Data;

@Data
public class ProjectMemberView {
    private Long memberId;
    private Long userId;
    private String realName;
    private String memberRole;
}
