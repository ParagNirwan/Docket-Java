package com.docket.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiFeedbackRequestDTO {
    @Min(-1)
    @Max(1)
    private short rating;

    @Size(max = 100)
    private String reason;

    @Size(max = 2000)
    private String comment;
}
