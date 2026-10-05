package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Public active-society summary returned by the student browse endpoint.
 *
 * Internal information such as member counts, performance data and
 * financial information is intentionally excluded.
 */
@Getter
@Builder
public class SocietyBrowseSummaryResponseDTO {
    private String societyID;
    private String societyName;
    private String acronym;
    private String description;
    private String societyType;
    private String logoUrl;
    private String campus;
    private String faculty;
    private String school;
    private BigDecimal membershipFee;
}
