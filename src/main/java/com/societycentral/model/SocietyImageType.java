package com.societycentral.model;

import lombok.Getter;

/**
 * Supported society profile image types and upload limits.
 */
@Getter
public enum SocietyImageType {
    LOGO("logos", 2L * 1024L * 1024L),
    BANNER("banners", 5L * 1024L * 1024L),
    GALLERY_IMAGE("gallery", 5L * 1024L * 1024L),
    HIGHLIGHT_COVER("highlights", 5L * 1024L * 1024L);

    private final String folderName;
    private final long maximumSizeBytes;

    SocietyImageType(String folderName, long maximumSizeBytes) {
        this.folderName = folderName;
        this.maximumSizeBytes = maximumSizeBytes;
    }
}
