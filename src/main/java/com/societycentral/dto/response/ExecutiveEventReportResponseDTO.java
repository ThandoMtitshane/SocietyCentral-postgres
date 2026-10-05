package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for the executive event report.
 * Used in the combined report PDF and API viewing endpoints.
 */
@Data
@Builder
public class ExecutiveEventReportResponseDTO {

    private String reportID;
    private String eventID;
    private String societyID;
    private String societyName;
    private String studentNumber;
    private String executiveName;
    private String executivePosition;
    private String expectations;
    private String expectationsMet;
    private String successAssessment;
    private String successReason;
    private String improvements;
    private String advice;
    private Integer attendeeCount;
    private Integer overallRating;
    private String additionalNotes;
    private LocalDateTime submittedAt;
}
