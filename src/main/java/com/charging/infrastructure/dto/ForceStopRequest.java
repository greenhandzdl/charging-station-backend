package com.charging.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForceStopRequest {

    @NotBlank(message = "强制终止原因不能为空")
    @Size(max = 200, message = "原因长度不能超过200个字符")
    private String reason;
}