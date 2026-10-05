// com.societycentral.dto.request.RegisterExecutiveRequest.java
package com.societycentral.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterExecutiveRequestDTO {
    private String studentNumber;
    private String societyID;
    private String position;
    private LocalDate termStartDate;
    private LocalDate termEndDate;
}
