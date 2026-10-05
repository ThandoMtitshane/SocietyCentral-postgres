package com.societycentral.service;

import com.societycentral.config.EventMediaProperties;
import com.societycentral.exception.EventMediaException;
import com.societycentral.model.EventImageType;
import com.societycentral.utils.StoredEventMedia;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores event images below the configured local event-media directory.
 */
@Service
public class LocalEventMediaStorageService implements EventMediaStorageService {

    private static final Pattern GENERATED_FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final EventMediaProperties properties;

    /**
     * Creates the local storage implementation.
     *
     * @param properties configured storage directory and public base URL
     */
    public LocalEventMediaStorageService(EventMediaProperties properties) {
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StoredEventMedia store(
            MultipartFile file,
            EventImageType imageType,
            String verifiedMimeType,
            int width,
            int height) {

        String extension = extensionFor(verifiedMimeType);
        String fileName = UUID.randomUUID() + extension;
        Path directory = directoryFor(imageType);
        Path target = directory.resolve(fileName).normalize();

        // SECURITY RULE: Storage paths are built exclusively from a controlled
        // enum folder and a server-generated UUID filename.
        if (!target.getParent().equals(directory)) {
            throw EventMediaException.storageFailure(
                    new IllegalStateException("Generated media path escaped its storage directory."));
        }

        try {
            Files.createDirectories(directory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }

            long storedSize = Files.size(target);
            return new StoredEventMedia(
                    buildPublicUrl(imageType, fileName),
                    fileName,
                    imageType,
                    verifiedMimeType,
                    width,
                    height,
                    storedSize,
                    Instant.now());
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw EventMediaException.storageFailure(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(EventImageType imageType, String storedFileName) {
        String normalisedName = storedFileName == null
                ? ""
                : storedFileName.toLowerCase(Locale.ROOT);

        // SECURITY RULE: Cleanup accepts only the UUID filename format that
        // this service itself generates; path fragments are rejected.
        if (!GENERATED_FILE_NAME.matcher(normalisedName).matches()) {
            throw new IllegalArgumentException("Invalid generated event-media filename.");
        }

        Path directory = directoryFor(imageType);
        Path target = directory.resolve(normalisedName).normalize();
        if (!target.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid generated event-media path.");
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw EventMediaException.storageFailure(ex);
        }
    }

    private Path directoryFor(EventImageType imageType) {
        Path root = properties.uploadDir().toAbsolutePath().normalize();
        Path directory = root.resolve(imageType.getFolderName()).normalize();
        if (!directory.startsWith(root)) {
            throw EventMediaException.storageFailure(
                    new IllegalStateException("Configured media folder escaped its storage root."));
        }
        return directory;
    }

    private String extensionFor(String verifiedMimeType) {
        return switch (verifiedMimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw EventMediaException.unsupportedMediaType(
                    "Unsupported event image format.");
        };
    }

    private String buildPublicUrl(EventImageType imageType, String fileName) {
        String baseUrl = properties.mediaBaseUrl().toString();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl
                + "/media/events/"
                + imageType.getFolderName()
                + "/"
                + fileName;
    }
}
