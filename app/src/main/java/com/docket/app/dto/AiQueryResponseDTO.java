package com.docket.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiQueryResponseDTO {

    private String answer;
    private List<AiSourceChunkDTO> sources;
}
