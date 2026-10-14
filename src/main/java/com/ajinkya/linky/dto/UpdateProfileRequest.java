package com.ajinkya.linky.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Name cannot be blank")
    private String name;
    
    @jakarta.validation.constraints.Pattern(regexp = "^$|^/uploads/avatars/[A-Za-z0-9_.-]+$|^https://\\S+$",
            message = "Profile image must be an uploaded avatar or an https URL")
    private String profileImage;
}
