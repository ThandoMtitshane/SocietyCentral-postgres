package com.societycentral.utils;

import com.societycentral.model.EventImageType;

import java.time.Instant;

/**
 * Storage-layer metadata for an event image.
 *
 * @param fileUrl public URL used to retrieve the image
 * @param fileName generated filename within the image-type folder
 * @param imageType event image type
 * @param mimeType verified MIME type derived from decoded image contents
 * @param width decoded width in pixels
 * @param height decoded height in pixels
 * @param sizeBytes stored file size in bytes
 * @param createdAt storage creation time, retained for future orphan cleanup
 */
public record StoredEventMedia(
        String fileUrl,
        String fileName,
        EventImageType imageType,
        String mimeType,
        int width,
        int height,
        long sizeBytes,
        Instant createdAt) {
}
