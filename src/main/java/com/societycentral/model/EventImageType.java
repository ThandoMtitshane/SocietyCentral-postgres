package com.societycentral.model;

/**
 * Identifies a processed event image and defines its required final
 * dimensions and storage folder.
 */
public enum EventImageType {

    POSTER(1080, 1350, "posters"),
    BANNER(1500, 500, "banners");

    private final int requiredWidth;
    private final int requiredHeight;
    private final String folderName;

    EventImageType(int requiredWidth, int requiredHeight, String folderName) {
        this.requiredWidth = requiredWidth;
        this.requiredHeight = requiredHeight;
        this.folderName = folderName;
    }

    /**
     * Returns the exact width required for this image type.
     *
     * @return required width in pixels
     */
    public int getRequiredWidth() {
        return requiredWidth;
    }

    /**
     * Returns the exact height required for this image type.
     *
     * @return required height in pixels
     */
    public int getRequiredHeight() {
        return requiredHeight;
    }

    /**
     * Returns the controlled local-storage folder for this image type.
     *
     * @return relative folder name
     */
    public String getFolderName() {
        return folderName;
    }
}
