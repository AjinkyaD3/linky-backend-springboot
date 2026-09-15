package com.ajinkya.linky.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class CreateUrlRequest {
    
    @NotBlank(message = "URL is required")
    @URL(message = "Must be a valid URL")
    private String originalUrl;

    private String customAlias;

    private String visibility;

    private String password;

    private Boolean isOneTime;
}
