package com.societycentral.dto.response;

import com.societycentral.model.SocietyImageType;

import java.time.Instant;

/**
 * Describes a validated and stored society profile image.
 */
public record SocietyMediaUploadResponseDTO(
        String fileUrl,
        String fileName,
        SocietyImageType imageType,
        int width,
        int height,
        long sizeBytes,
        Instant createdAt) {
}
