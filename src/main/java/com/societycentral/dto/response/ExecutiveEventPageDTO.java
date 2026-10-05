package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Stable page-shaped response for society-scoped executive event results.
 *
 * <p>This avoids exposing Spring Data's internal {@code Page}
 * serialization format to frontend consumers.</p>
 */
@Data
@Builder
public class ExecutiveEventPageDTO {

    private List<EventListItemDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
