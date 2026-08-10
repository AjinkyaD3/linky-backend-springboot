package com.ajinkya.linky.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ClickOverTime {
    private LocalDate date;
    private long count;
}
