package com.societycentral.service;

import com.societycentral.model.SocietyImageType;
import com.societycentral.utils.StoredSocietyMedia;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores validated society profile images.
 */
public interface SocietyMediaStorageService {

    /**
     * Stores a society image that has already passed content validation.
     */
    StoredSocietyMedia store(
            MultipartFile file,
            SocietyImageType imageType,
            String verifiedMimeType,
            int width,
            int height);

    /**
     * Removes a previously generated society-media file.
     */
    void delete(SocietyImageType imageType, String storedFileName);
}
