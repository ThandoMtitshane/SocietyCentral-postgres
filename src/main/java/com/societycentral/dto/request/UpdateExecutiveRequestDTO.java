// com.societycentral.dto.request.UpdateExecutiveRequestDTO.java
package com.societycentral.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateExecutiveRequestDTO {
    private String studentNumber;
    private String societyID;
    private LocalDate termStartDate;
    private String position;
    private LocalDate termEndDate;
}
