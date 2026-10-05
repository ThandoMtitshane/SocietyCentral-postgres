package com.societycentral.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compact executive representation used in assignee-selection
 * dropdowns (e.g. Assign Task).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveSummaryDTO {
    private String studentNumber;
    private String firstName;
    private String lastName;
    private String position;
}