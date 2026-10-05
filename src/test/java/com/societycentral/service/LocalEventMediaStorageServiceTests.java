package com.societycentral.service;

import com.societycentral.config.EventMediaProperties;
import com.societycentral.model.EventImageType;
import com.societycentral.utils.StoredEventMedia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalEventMediaStorageServiceTests {

    @TempDir
    private Path tempDirectory;

    @Test
    void storesWithUuidNameInControlledPosterDirectoryAndCanDelete() throws Exception {
        EventMediaProperties properties = new EventMediaProperties(
                tempDirectory,
                URI.create("http://localhost:8080/"));
        LocalEventMediaStorageService storage =
                new LocalEventMediaStorageService(properties);
        byte[] contents = new byte[]{10, 20, 30};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../malicious.exe",
                "image/png",
                contents);

        StoredEventMedia stored = storage.store(
                file,
                EventImageType.POSTER,
                "image/png",
                1080,
                1350);

        assertTrue(stored.fileName().matches(
                "^[0-9a-f-]{36}\\.png$"));
        assertFalse(stored.fileName().contains("malicious"));
        assertTrue(stored.fileUrl().startsWith(
                "http://localhost:8080/media/events/posters/"));
        Path storedPath = tempDirectory
                .resolve("posters")
                .resolve(stored.fileName());
        assertTrue(Files.exists(storedPath));
        assertArrayEquals(contents, Files.readAllBytes(storedPath));

        storage.delete(EventImageType.POSTER, stored.fileName());

        assertFalse(Files.exists(storedPath));
    }

    @Test
    void rejectsPathTraversalDuringCleanup() {
        LocalEventMediaStorageService storage =
                new LocalEventMediaStorageService(new EventMediaProperties(
                        tempDirectory,
                        URI.create("http://localhost:8080")));

        assertThrows(
                IllegalArgumentException.class,
                () -> storage.delete(
                        EventImageType.POSTER,
                        "../outside.png"));
    }
}
