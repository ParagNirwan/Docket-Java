package com.docket.app.enums;

import java.util.Locale;
import java.util.Optional;

// Where an organization's files are stored. Chosen when the organization is created and
// never changed afterwards: moving data between jurisdictions is an operational project,
// not a settings toggle.
public enum DataRegion {
    EU,
    US;

    // Stored object names start with this, so storage can route each object to its
    // region's bucket and key without a database lookup.
    public String pathPrefix() {
        return name().toLowerCase(Locale.ROOT) + "/";
    }

    // Names written before residency existed have no prefix; they live in the EU bucket.
    public static DataRegion ofStoredName(String storedName) {
        return fromPrefix(storedName).orElse(EU);
    }

    public static Optional<DataRegion> fromPrefix(String storedName) {
        if (storedName == null) return Optional.empty();
        for (DataRegion region : values()) {
            if (storedName.startsWith(region.pathPrefix())) return Optional.of(region);
        }
        return Optional.empty();
    }
}
