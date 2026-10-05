package com.societycentral.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Editable metadata for one society gallery image.
 */
@Getter
@Setter
public class UpdateSocietyGalleryMediaRequestDTO {

    @Size(max = 200, message = "Gallery caption must not exceed 200 characters.")
    private String caption;
}
