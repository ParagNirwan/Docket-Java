package com.docket.app.dto;

// Every field but summariesEnabled is optional on update: null leaves it unchanged.
// The retrieval values override the server defaults for this organization; send
// resetRetrievalTuning=true to go back to the defaults.
public record AiSettingsDTO(
        boolean summariesEnabled,
        Boolean classificationEnabled,
        Boolean autoApplyTags,
        Double maxDistance,
        Double fallbackMaxDistance,
        Double rerankMinScore,
        Boolean resetRetrievalTuning
) {
    public AiSettingsDTO(boolean summariesEnabled) {
        this(summariesEnabled, null, null, null, null, null, null);
    }
}
