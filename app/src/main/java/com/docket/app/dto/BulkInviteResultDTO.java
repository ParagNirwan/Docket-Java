package com.docket.app.dto;

import java.util.List;

public record BulkInviteResultDTO(int sent, int skipped, int errors, List<BulkInviteRowDTO> rows) {
}
