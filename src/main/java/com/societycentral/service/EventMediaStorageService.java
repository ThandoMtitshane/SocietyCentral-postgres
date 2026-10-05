package com.societycentral.service;

import com.societycentral.model.EventImageType;
import com.societycentral.utils.StoredEventMedia;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction for storing validated event images.
 */
public interface EventMediaStorageService {

    /**
     * Stores a validated event image using storage-controlled paths and names.
     *
     * @param file uploaded image
     * @param imageType validated event image type
     * @param verifiedMimeType MIME type verified from the decoded contents
     * @param width decoded image width
     * @param height decoded image height
     * @return metadata for the stored image
     */
    StoredEventMedia store(
            MultipartFile file,
            EventImageType imageType,
            String verifiedMimeType,
            int width,
            int height);

    /**
     * Deletes a generated stored image. This supports future rollback and
     * orphan-cleanup workflows.
     *
     * @param imageType folder containing the image
     * @param storedFileName generated filename returned by {@link #store}
     */
    void delete(EventImageType imageType, String storedFileName);
}
