package com.societycentral.dto.request;

import com.societycentral.model.Campus;
import com.societycentral.model.Faculty;
import com.societycentral.model.School;
import com.societycentral.model.SocietyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Request payload for registering or updating a single society.
 * Used by:
 *   POST /api/sdo/societies/register
 *   PUT  /api/sdo/societies/{societyID}
 *
 * sdoStaffNumber is resolved server-side from the authenticated JWT.
 */
@Data
public class SocietyRequestDTO {

    // Only required on UPDATE - null on register (ID is generated server-side)
    private String societyID;

    @NotBlank(message = "Society name is required")
    private String societyName;

    private String acronym;
    private SocietyType societyType;
    private Faculty faculty;
    private School school;
    @Size(max = 2500, message = "Description cannot exceed 2500 characters.")
    private String description;
    private String vision;
    private String mission;
    private Integer yearEstablished;
    @Size(max = 10, message = "Contact number must not exceed 10 characters.")
    @Pattern(regexp = "\\d{1,10}", message = "Contact number must contain numeric digits only.")
    private String contactNumber;
    private String email;
    private String logoUrl;
    private String bannerUrl;
    private String facebookURL;
    private String instagramURL;
    private String tiktokURL;
    private Campus campus;
    private Boolean activeStatus;

    // Financial fields - mandatory at registration (frontend pre-fills 0.0)
    private BigDecimal annualBudgetAllocation;
    private BigDecimal membershipFee;

    /**
     * Only relevant on UPDATE when annualBudgetAllocation changes.
     * true  = apply difference to currentBalance immediately + record FundTransaction
     * false = save new annualBudgetAllocation only, currentBalance unchanged
     *         (takes effect at next year-start rollover)
     */
    private Boolean effectiveImmediately;
}
