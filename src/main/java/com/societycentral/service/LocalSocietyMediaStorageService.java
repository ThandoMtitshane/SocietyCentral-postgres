package com.societycentral.service;

import com.societycentral.config.EventMediaProperties;
import com.societycentral.exception.SocietyMediaException;
import com.societycentral.model.SocietyImageType;
import com.societycentral.utils.StoredSocietyMedia;
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
 * Stores society images below the configured media root.
 */
@Service
public class LocalSocietyMediaStorageService
        implements SocietyMediaStorageService {

    private static final Pattern GENERATED_FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final EventMediaProperties properties;

    public LocalSocietyMediaStorageService(EventMediaProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredSocietyMedia store(
            MultipartFile file,
            SocietyImageType imageType,
            String verifiedMimeType,
            int width,
            int height) {
        String fileName = UUID.randomUUID()
                + extensionFor(verifiedMimeType);
        Path directory = directoryFor(imageType);
        Path target = directory.resolve(fileName).normalize();
        if (!target.getParent().equals(directory)) {
            throw SocietyMediaException.storageFailure(
                    new IllegalStateException(
                            "Generated media path escaped its directory."));
        }

        try {
            Files.createDirectories(directory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }

            return new StoredSocietyMedia(
                    buildPublicUrl(imageType, fileName),
                    fileName,
                    imageType,
                    verifiedMimeType,
                    width,
                    height,
                    Files.size(target),
                    Instant.now());
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw SocietyMediaException.storageFailure(ex);
        }
    }

    @Override
    public void delete(
            SocietyImageType imageType,
            String storedFileName) {
        String normalisedName = storedFileName == null
                ? ""
                : storedFileName.toLowerCase(Locale.ROOT);
        if (!GENERATED_FILE_NAME.matcher(normalisedName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid generated society-media filename.");
        }

        Path directory = directoryFor(imageType);
        Path target = directory.resolve(normalisedName).normalize();
        if (!target.getParent().equals(directory)) {
            throw new IllegalArgumentException(
                    "Invalid generated society-media path.");
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw SocietyMediaException.storageFailure(ex);
        }
    }

    private Path directoryFor(SocietyImageType imageType) {
        Path root = properties.uploadDir().toAbsolutePath().normalize();
        Path directory = root.resolve("societies")
                .resolve(imageType.getFolderName())
                .normalize();
        if (!directory.startsWith(root)) {
            throw SocietyMediaException.storageFailure(
                    new IllegalStateException(
                            "Configured media folder escaped its root."));
        }
        return directory;
    }

    private String extensionFor(String verifiedMimeType) {
        return switch (verifiedMimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw SocietyMediaException.unsupportedMediaType();
        };
    }

    private String buildPublicUrl(
            SocietyImageType imageType,
            String fileName) {
        // Store an origin-independent URL. The frontend resolves this path
        // against its configured API origin, so uploaded media continues to
        // work outside a localhost development environment.
        return "/media/societies/"
                + imageType.getFolderName()
                + "/"
                + fileName;
    }
}
