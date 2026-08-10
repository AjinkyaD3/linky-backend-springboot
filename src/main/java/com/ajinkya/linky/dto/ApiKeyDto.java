package com.ajinkya.linky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyDto {
    private Long id;
    private String name;
    private String keyValue; // Might be masked on list, but full on create
    private LocalDateTime createdAt;
}
