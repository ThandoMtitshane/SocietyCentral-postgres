package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class POAEventResponseDTO {
    private String poaEventID;
    private String organizationName;
    private String month;
    private String theme;
    private String programName;
    private String eventDate;
    private String venue;
    private String attendance;
    private String purpose;
    private BigDecimal projectedIncomeFromAccount;
    private BigDecimal projectedIncomeSponsorship;
    private BigDecimal expensePromoMaterial;
    private BigDecimal expenseDataAirtime;
    private BigDecimal expenseGifts;
    private BigDecimal expenseOther;
    private String expenseOtherSpecification;
    private String sdoEventComment;
    private Integer sortOrder;
    private List<CoHostDTO> coHosts;

    @Data
    @Builder
    public static class CoHostDTO {
        private String coHostID;
        private String societyID;
        private String societyName;
        private String status; // PENDING, ACCEPTED, DECLINED
    }
}