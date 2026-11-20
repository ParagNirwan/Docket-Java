package com.docket.app.service;

import com.docket.app.dto.AiSettingsDTO;
import com.docket.app.entity.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRetrievalTuningTest {

    private static AiSettingsDTO tuning(Double max, Double fallback, Double rerank, Boolean reset) {
        return new AiSettingsDTO(true, null, null, max, fallback, rerank, reset);
    }

    @Test
    void storesValidOverrides() {
        Tenant tenant = new Tenant();
        assertTrue(AiSettingsService.applyTuning(tenant, tuning(0.4, 0.55, 0.25, null)));
        assertEquals(0.4, tenant.getAiMaxDistance());
        assertEquals(0.55, tenant.getAiFallbackMaxDistance());
        assertEquals(0.25, tenant.getAiRerankMinScore());
        assertFalse(AiSettingsService.applyTuning(tenant, tuning(null, null, null, null)));
    }

    @Test
    void rejectsOutOfRangeOrInconsistentValues() {
        Tenant tenant = new Tenant();
        assertThrows(ResponseStatusException.class, () -> AiSettingsService.applyTuning(tenant, tuning(3.0, null, null, null)));
        assertThrows(ResponseStatusException.class, () -> AiSettingsService.applyTuning(tenant, tuning(0.5, 0.4, null, null)));
        assertThrows(ResponseStatusException.class, () -> AiSettingsService.applyTuning(tenant, tuning(null, null, 1.5, null)));
    }

    @Test
    void resetGoesBackToServerDefaults() {
        Tenant tenant = new Tenant();
        AiSettingsService.applyTuning(tenant, tuning(0.4, 0.5, 0.2, null));
        assertTrue(AiSettingsService.applyTuning(tenant, tuning(null, null, null, true)));
        assertNull(tenant.getAiMaxDistance());
    }
}
