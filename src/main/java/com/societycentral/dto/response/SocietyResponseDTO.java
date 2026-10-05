package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Full society detail returned after register/update, or for the
 * societies list table on the SDO management page.
 */
@Data
@Builder
public class SocietyResponseDTO {

    private String societyID;
    private String societyName;
    private String acronym;
    private String societyType;
    private String faculty;
    private String school;
    private String description;
    private String vision;
    private String mission;
    private Integer yearEstablished;
    private String contactNumber;
    private String email;
    private String logoUrl;
    private String bannerUrl;
    private String facebookURL;
    private String instagramURL;
    private String tiktokURL;
    private String campus;
    private Boolean activeStatus;
    private Boolean isFlagged;
    private Integer numberOfMembers;
    private String sdoStaffNumber;

    // Financial fields
    private BigDecimal membershipFee;
    private BigDecimal annualBudgetAllocation;
    private BigDecimal currentBalance;
}

