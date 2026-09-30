package com.eliza.aicompetition.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AddMemberRequest {
    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "memberRole 不能为空")
    @Pattern(regexp = "member|advisor", message = "memberRole 只能为 member 或 advisor")
    private String memberRole; // "member" or "advisor"
}
