package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Metadata submitted with a highlight cover image. */
@Getter
@Setter
@NoArgsConstructor
public class SocietyHighlightRequestDTO {

    @NotBlank(message = "Headline is required.")
    private String headline;

    @NotBlank(message = "Caption is required.")
    private String caption;

    @NotBlank(message = "Article is required.")
    private String article;

    @Size(max = 80, message = "Category must not exceed 80 characters.")
    private String category;

    private boolean activeStatus = true;
}
