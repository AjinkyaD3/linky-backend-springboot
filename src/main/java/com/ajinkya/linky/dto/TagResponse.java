package com.ajinkya.linky.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TagResponse {
    private Long id;
    private String name;
}
