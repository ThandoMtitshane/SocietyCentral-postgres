package com.societycentral.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class POAEventDTO {
    private String poaEventID;      // null on create, set on update
    private String organizationName;
    private String month;
    private String theme;
    private String programName;
    private String eventDate;       // accepts "TBC"
    private String venue;
    private String attendance;      // "MEMBERS_ONLY" or "EVERY_STUDENT"
    private String purpose;
    private BigDecimal projectedIncomeFromAccount;
    private BigDecimal projectedIncomeSponsorship;
    private BigDecimal expensePromoMaterial;
    private BigDecimal expenseDataAirtime;
    private BigDecimal expenseGifts;
    private BigDecimal expenseOther;
    private String expenseOtherSpecification;
    private Integer sortOrder;
    // Co-host society IDs selected in the UI (invited on save)
    private java.util.List<String> coHostSocietyIDs;
}