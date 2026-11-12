package com.docket.app.dto;

import com.docket.app.enums.DomainJoinMode;

public record UpdateTenantDomainRequestDTO(DomainJoinMode joinMode, Boolean ssoRequired) {
}
