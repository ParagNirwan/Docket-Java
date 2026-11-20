package com.docket.app.service;

import com.docket.app.enums.DataRegion;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.EnumMap;
import java.util.Map;

// Stores each object in its organization's region (data residency), encrypted with that
// region's Cloud KMS key (CMEK, envelope encryption: GCS encrypts each object with a data key,
// which KMS wraps with the key you control and can disable or rotate).
//
// The region comes from the object name's prefix (eu/…, us/…, see DataRegion); names
// without a prefix predate residency and live in the EU bucket.
@Service
@org.springframework.context.annotation.Profile("gcp")
public class GcsStorageService implements StorageService {

    record RegionTarget(String bucket, String kmsKeyName) {
    }

    private final Storage storage;
    private final Map<DataRegion, RegionTarget> targets = new EnumMap<>(DataRegion.class);

    public GcsStorageService(
            @Value("${docket.storage.gcs.bucket}") String euBucket,
            @Value("${docket.storage.gcs.bucket-us:}") String usBucket,
            @Value("${docket.storage.gcs.kms-key-eu:}") String euKey,
            @Value("${docket.storage.gcs.kms-key-us:}") String usKey) {
        this(StorageOptions.getDefaultInstance().getService(), euBucket, usBucket, euKey, usKey);
    }

    GcsStorageService(Storage storage, String euBucket, String usBucket, String euKey, String usKey) {
        this.storage = storage;
        targets.put(DataRegion.EU, new RegionTarget(euBucket, blankToNull(euKey)));
        if (usBucket != null && !usBucket.isBlank()) {
            targets.put(DataRegion.US, new RegionTarget(usBucket, blankToNull(usKey)));
        }
    }

    // Fails closed: a US organization's file is never written to the EU bucket just
    // because the US bucket wasn't configured.
    RegionTarget targetFor(String storedFilename) {
        DataRegion region = DataRegion.ofStoredName(storedFilename);
        RegionTarget target = targets.get(region);
        if (target == null) {
            throw new IllegalStateException("No storage bucket configured for region " + region
                    + " (set docket.storage.gcs.bucket-" + region.name().toLowerCase() + ")");
        }
        return target;
    }

    @Override
    public String store(InputStream data, String storedFilename, String contentType) {
        RegionTarget target = targetFor(storedFilename);
        BlobInfo blobInfo = BlobInfo.newBuilder(target.bucket(), storedFilename)
                .setContentType(contentType)
                .build();
        try {
            if (target.kmsKeyName() != null) {
                storage.createFrom(blobInfo, data, Storage.BlobWriteOption.kmsKeyName(target.kmsKeyName()));
            } else {
                // Falls back to the bucket's default key (set one with scripts/setup-cmek.sh).
                storage.createFrom(blobInfo, data);
            }
            return storedFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to GCS: " + storedFilename, e);
        }
    }

    @Override
    public InputStream retrieve(String storedFilename) {
        var blob = storage.get(blobId(storedFilename));
        if (blob == null) {
            throw new RuntimeException("File not found in GCS: " + storedFilename);
        }
        return Channels.newInputStream(blob.reader());
    }

    @Override
    public void delete(String storedFilename) {
        storage.delete(blobId(storedFilename));
    }

    @Override
    public boolean exists(String storedFilename) {
        var blob = storage.get(blobId(storedFilename));
        return blob != null && blob.exists();
    }

    @Override
    public void setHold(String storedFilename, boolean hold) {
        var blob = storage.get(blobId(storedFilename));
        if (blob == null) {
            throw new RuntimeException("File not found in GCS: " + storedFilename);
        }
        BlobInfo updated = blob.toBuilder().setTemporaryHold(hold).build();
        storage.update(updated);
    }

    private BlobId blobId(String storedFilename) {
        return BlobId.of(targetFor(storedFilename).bucket(), storedFilename);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
