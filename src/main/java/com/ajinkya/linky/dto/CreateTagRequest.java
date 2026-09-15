package com.ajinkya.linky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTagRequest {

    @NotBlank(message = "Tag name is required")
    @Size(max = 50, message = "Tag name must be at most 50 characters")
    private String name;
}
