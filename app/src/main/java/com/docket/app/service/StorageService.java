package com.docket.app.service;

import java.io.InputStream;

// This is a contract (interface) that says: "Any file storage system must
// be able to do these 4 things." Right now we store files on the computer's
// hard drive, but later we could store them in the cloud (like Amazon S3)
// without changing any other code — we'd just write a new class that
// follows this same contract.
public interface StorageService {

    // Save a file to storage. Takes the file's data stream, a safe internal
    // name (not the user's original filename), and what kind of file it is
    // (like "application/pdf"). Returns the path where the file was saved.
    String store(InputStream data, String storedFilename, String contentType);

    // Get a file back from storage so we can send it to the user for download.
    // Returns a stream of the file's data.
    InputStream retrieve(String storedFilename);

    // Permanently remove a file from storage.
    void delete(String storedFilename);

    // Check if a file exists in storage. Returns true if found, false if not.
    boolean exists(String storedFilename);
}
