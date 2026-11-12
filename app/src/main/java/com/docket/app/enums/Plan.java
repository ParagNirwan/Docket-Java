package com.docket.app.enums;

import lombok.Getter;

// What each plan allows. A null limit means unlimited. These only apply where billing is
// switched on: a self-hosted installation without Stripe has no limits.
@Getter
public enum Plan {
    FREE(gigabytes(1), 3, 50, false),
    PRO(gigabytes(100), 25, 2_000, false),
    BUSINESS(gigabytes(1_000), null, 20_000, true);

    private final long storageBytes;
    private final Integer seats;
    private final int aiQueriesPerMonth;
    // Compliance locks can't be undone by anyone, so they are kept for the plan meant for regulated customers.
    private final boolean complianceLock;

    Plan(long storageBytes, Integer seats, int aiQueriesPerMonth, boolean complianceLock) {
        this.storageBytes = storageBytes;
        this.seats = seats;
        this.aiQueriesPerMonth = aiQueriesPerMonth;
        this.complianceLock = complianceLock;
    }

    private static long gigabytes(long count) {
        return count * 1024 * 1024 * 1024;
    }
}
