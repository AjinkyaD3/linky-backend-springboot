package com.ajinkya.linky.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnlockRequest {
    @NotBlank(message = "Password is required")
    private String password;
}
