package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Stable page response for society-scoped membership applications.
 */
@Data
@Builder
public class ExecutiveMembershipApplicationPageDTO {

    private List<ExecutiveMembershipApplicationListItemDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
