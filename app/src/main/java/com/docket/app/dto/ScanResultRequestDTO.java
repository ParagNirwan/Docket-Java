package com.docket.app.dto;
import com.docket.app.enums.ScanStatus;
import jakarta.validation.constraints.NotNull;
public record ScanResultRequestDTO(@NotNull ScanStatus status) { }
