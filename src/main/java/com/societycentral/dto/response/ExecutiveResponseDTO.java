// com.societycentral.dto.response.ExecutiveResponseDTO.java
package com.societycentral.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExecutiveResponseDTO {
    private String studentNumber;
    private String societyID;
    private String societyName;
    private String fullName;
    private String email;
    private String position;
    private LocalDate termStartDate;
    private LocalDate termEndDate;
    private Boolean isActive;

    public Boolean getIsActive() {
        return termEndDate == null || termEndDate.isAfter(LocalDate.now());
    }
}
