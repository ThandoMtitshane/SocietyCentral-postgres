package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Complete ordered list of gallery identifiers for one society.
 */
@Getter
@Setter
public class ReorderSocietyGalleryMediaRequestDTO {

    @NotEmpty(message = "At least one gallery image is required.")
    private List<@NotBlank(message = "Gallery media ID is required.") String>
            mediaIDs;
}
