package com.eliza.aicompetition.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMemberRequest {
    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "memberRole 不能为空")
    private String memberRole; // "member" or "advisor"
}
