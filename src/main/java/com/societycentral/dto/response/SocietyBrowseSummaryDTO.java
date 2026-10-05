package com.societycentral.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SocietyBrowseSummaryDTO {
    private String societyID;
    private String name;
    private String description;
    private String societyType;
    private String logoUrl;
    private int numberOfMembers;
    private boolean submittedPOA;
    private boolean atRisk;
    private Integer pendingTasks=0;
    private boolean flagged;
}
