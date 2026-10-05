package com.societycentral.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Highlight projection used by the carousel, editor, and article page.
 * Article is omitted from list projections and included only where needed.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SocietyHighlightResponseDTO {
    private String highlightID;
    private String societyName;
    private String headline;
    private String caption;
    private String article;
    private String coverImageUrl;
    private String category;
    private Integer sortOrder;
    private LocalDateTime publishedAt;
    private Boolean activeStatus;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
