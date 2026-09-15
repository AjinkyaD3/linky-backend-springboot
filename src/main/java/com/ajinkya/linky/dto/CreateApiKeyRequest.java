package com.ajinkya.linky.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateApiKeyRequest {
    @NotBlank(message = "Key name is required")
    private String name;
}
