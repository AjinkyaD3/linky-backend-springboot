package com.ajinkya.linky.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkCreateRequest {

    /** Up to 100 links per call. Each item is validated individually so one bad row does not fail the batch. */
    @NotEmpty(message = "Provide at least one link")
    @Size(max = 100, message = "At most 100 links per request")
    private List<CreateUrlRequest> links;
}
