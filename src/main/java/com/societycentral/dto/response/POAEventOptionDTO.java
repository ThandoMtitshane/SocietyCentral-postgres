package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Lightweight POA event option for the "Create from POA" dropdown.
 * Contains enough data to prefill the event proposal form.
 */
@Data
@Builder
public class POAEventOptionDTO {

    private String poaEventID;
    private String programName;
    private String month;
    private String theme;
    private String eventDate;
    private String venue;
    private String attendance;
    private String purpose;

    // Budget prefill
    private BigDecimal projectedIncomeFromAccount;
    private BigDecimal projectedIncomeSponsorship;
    private BigDecimal expensePromoMaterial;
    private BigDecimal expenseDataAirtime;
    private BigDecimal expenseGifts;
    private BigDecimal expenseOther;
    private String expenseOtherSpecification;

    // Co-hosts from POA
    private java.util.List<String> coHostSocietyIDs;
}
