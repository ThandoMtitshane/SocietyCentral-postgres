package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class POAResponseDTO {
    private String poaID;
    private String societyID;
    private String societyName;
    private String submittedByStudentNumber;
    private Integer year;
    private String status;
    private LocalDate submittedDate;
    private String reviewedByStaffNumber;
    private String reviewNotes;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdatedAt;
    private List<POAEventResponseDTO> events;
}