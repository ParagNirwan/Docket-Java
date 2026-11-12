package com.docket.app.dto;

import com.docket.app.enums.Roles;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// maxUses and expiresInDays are optional: left out, the link has no limit of that kind.
public record CreateInviteLinkRequestDTO(
        @NotNull(message = "Role is required") Roles role,
        @Min(value = 1, message = "A link needs at least one use")
        @Max(value = 10000, message = "A link can have at most 10,000 uses") Integer maxUses,
        @Min(value = 1, message = "A link lasts at least a day")
        @Max(value = 365, message = "A link lasts at most a year") Integer expiresInDays
) {
}
