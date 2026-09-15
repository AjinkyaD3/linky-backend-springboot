package com.ajinkya.linky.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Name cannot be blank")
    private String name;
    
    private String profileImage;
}
