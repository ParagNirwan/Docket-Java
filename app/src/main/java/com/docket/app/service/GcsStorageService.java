package com.docket.app.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;

@Service
@org.springframework.context.annotation.Profile("gcp")
public class GcsStorageService implements StorageService {

    private final Storage storage;
    private final String bucketName;

    public GcsStorageService(
            @Value("${docket.storage.gcs.bucket}") String bucketName) {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucketName = bucketName;
    }

    @Override
    public String store(InputStream data, String storedFilename, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, storedFilename)
                .setContentType(contentType)
                .build();
        try {
            storage.createFrom(blobInfo, data);
            return storedFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to GCS: " + storedFilename, e);
        }
    }

    @Override
    public InputStream retrieve(String storedFilename) {
        var blob = storage.get(BlobId.of(bucketName, storedFilename));
        if (blob == null) {
            throw new RuntimeException("File not found in GCS: " + storedFilename);
        }
        return Channels.newInputStream(blob.reader());
    }

    @Override
    public void delete(String storedFilename) {
        storage.delete(BlobId.of(bucketName, storedFilename));
    }

    @Override
    public boolean exists(String storedFilename) {
        var blob = storage.get(BlobId.of(bucketName, storedFilename));
        return blob != null && blob.exists();
    }
}
