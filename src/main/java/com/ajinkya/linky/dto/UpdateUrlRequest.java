package com.ajinkya.linky.dto;

import lombok.Data;
import java.util.List;

@Data
public class UpdateUrlRequest {
    private String title;
    private String description;
    private List<String> tags;
}
