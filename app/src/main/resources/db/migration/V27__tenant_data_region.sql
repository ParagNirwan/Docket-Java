-- Data residency: where an organization's files live. Existing organizations are in the EU
-- (the only bucket so far). Set once at creation; the application never updates it.
ALTER TABLE tenants
    ADD COLUMN data_region VARCHAR(8) NOT NULL DEFAULT 'EU'
        CONSTRAINT ck_tenants_data_region CHECK (data_region IN ('EU', 'US'));
