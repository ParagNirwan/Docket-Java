package com.docket.app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

// This is the actual implementation that saves files to the computer's hard drive.
// It reads the folder location from application.properties so you can change
// where files are saved without touching any code.
@Service
@org.springframework.context.annotation.Profile("!gcp")
public class LocalFileSystemStorageService implements StorageService {

    // The root folder where all uploaded files will be saved.
    // Configured in application.properties as: docket.storage.local.root-dir
    private final Path rootDir;

    // When the app starts, Spring reads the folder path from settings
    // and makes sure the folder exists (creates it if missing).
    public LocalFileSystemStorageService(
            @Value("${docket.storage.local.root-dir:./uploads}") String rootDir) {
        this.rootDir = Paths.get(rootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create the upload folder: " + this.rootDir, e);
        }
    }

    // Save a file to disk. The storedFilename already includes the full
    // sub-path like "tenant-id/2026/10/some-uuid.pdf" so we just append
    // it to our root folder and copy the file data there.
    @Override
    public String store(InputStream data, String storedFilename, String contentType) {
        try {
            Path targetPath = rootDir.resolve(storedFilename).normalize();

            // Safety check: make sure nobody is trying to write outside
            // our upload folder using tricks like "../../etc/passwords"
            if (!targetPath.startsWith(rootDir)) {
                throw new SecurityException("Cannot store file outside the upload directory");
            }

            // Create any sub-folders that don't exist yet
            // (e.g., the tenant-id/year/month folders)
            Files.createDirectories(targetPath.getParent());

            // Copy the file data to the target location.
            // REPLACE_EXISTING means if a file with the same name somehow
            // exists, overwrite it rather than throwing an error.
            Files.copy(data, targetPath, StandardCopyOption.REPLACE_EXISTING);

            return storedFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + storedFilename, e);
        }
    }

    // Read a file back from disk so we can send it to the user for download.
    @Override
    public InputStream retrieve(String storedFilename) {
        try {
            Path filePath = rootDir.resolve(storedFilename).normalize();

            if (!filePath.startsWith(rootDir)) {
                throw new SecurityException("Cannot read file outside the upload directory");
            }

            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + storedFilename, e);
        }
    }

    // Delete a file from disk permanently.
    @Override
    public void delete(String storedFilename) {
        try {
            Path filePath = rootDir.resolve(storedFilename).normalize();

            if (!filePath.startsWith(rootDir)) {
                throw new SecurityException("Cannot delete file outside the upload directory");
            }

            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + storedFilename, e);
        }
    }

    // Check whether a file exists on disk.
    @Override
    public boolean exists(String storedFilename) {
        Path filePath = rootDir.resolve(storedFilename).normalize();

        if (!filePath.startsWith(rootDir)) {
            return false;
        }

        return Files.exists(filePath);
    }
}
