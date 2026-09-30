package com.eliza.aicompetition.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateRoleRequest {

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "student|teacher|admin", message = "角色只能为student、teacher或admin")
    private String role;
}
