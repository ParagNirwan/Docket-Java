package com.docket.app.service;

import com.docket.app.enums.DataRegion;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GcsStorageRegionTest {

    @Test
    void routesEachObjectToItsRegionsBucket() {
        GcsStorageService service = new GcsStorageService(mock(Storage.class), "eu-bucket", "us-bucket", "eu-key", "us-key");

        assertEquals("us-bucket", service.targetFor("us/t1/2026/11/a.pdf").bucket());
        assertEquals("us-key", service.targetFor("us/t1/2026/11/a.pdf").kmsKeyName());
        assertEquals("eu-bucket", service.targetFor("eu/t1/2026/11/a.pdf").bucket());
        // Written before residency existed: EU.
        assertEquals("eu-bucket", service.targetFor("1b2c-tenant/2025/1/a.pdf").bucket());
    }

    @Test
    void refusesToStoreUsDataWhenNoUsBucketIsConfigured() {
        GcsStorageService service = new GcsStorageService(mock(Storage.class), "eu-bucket", "", "", "");

        assertThrows(IllegalStateException.class, () -> service.targetFor("us/t1/2026/11/a.pdf"));
    }

    @Test
    void encryptsWithTheRegionsKmsKey() throws Exception {
        Storage storage = mock(Storage.class);
        GcsStorageService service = new GcsStorageService(storage, "eu-bucket", "", "projects/p/locations/europe-west3/keyRings/r/cryptoKeys/k", "");

        service.store(new ByteArrayInputStream(new byte[]{1}), "eu/t1/x.pdf", "application/pdf");

        ArgumentCaptor<BlobInfo> info = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).createFrom(info.capture(), any(InputStream.class), any(Storage.BlobWriteOption.class));
        assertEquals("eu-bucket", info.getValue().getBucket());
    }

    @Test
    void regionPrefixRoundTrips() {
        assertEquals(DataRegion.US, DataRegion.ofStoredName(DataRegion.US.pathPrefix() + "x"));
        assertEquals(DataRegion.EU, DataRegion.ofStoredName("legacy/x"));
    }
}
