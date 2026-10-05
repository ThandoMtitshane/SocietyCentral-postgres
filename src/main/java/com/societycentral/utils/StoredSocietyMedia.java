package com.societycentral.utils;

import com.societycentral.model.SocietyImageType;

import java.time.Instant;

/**
 * Storage metadata for a validated society profile image.
 */
public record StoredSocietyMedia(
        String fileUrl,
        String fileName,
        SocietyImageType imageType,
        String mimeType,
        int width,
        int height,
        long sizeBytes,
        Instant createdAt) {
}
