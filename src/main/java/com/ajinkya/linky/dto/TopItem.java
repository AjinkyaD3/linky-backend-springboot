package com.ajinkya.linky.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopItem {
    private String name;
    private long count;
}
