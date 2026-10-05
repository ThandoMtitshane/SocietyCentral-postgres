package com.societycentral.dto.response;

import com.societycentral.model.EventImageType;

import java.time.Instant;

/**
 * Describes an event image that was validated and stored successfully.
 *
 * @param fileUrl public URL used by the frontend to retrieve the image
 * @param fileName generated storage filename
 * @param imageType event image type
 * @param width decoded image width in pixels
 * @param height decoded image height in pixels
 * @param sizeBytes uploaded file size in bytes
 * @param createdAt time at which the stored-media record was created
 */
public record EventMediaUploadResponseDTO(
        String fileUrl,
        String fileName,
        EventImageType imageType,
        int width,
        int height,
        long sizeBytes,
        Instant createdAt) {
}
