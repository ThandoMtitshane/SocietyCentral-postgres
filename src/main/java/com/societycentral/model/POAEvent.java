package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "POAEvent")
@Getter
@Setter
@NoArgsConstructor
public class POAEvent {

    @Id
    @Column(name = "poaEventID", length = 36)
    private String poaEventID;

    @Column(name = "poaID", length = 36, nullable = false)
    private String poaID;

    @Column(name = "organizationName", length = 200)
    private String organizationName;

    @Column(name = "month", length = 20)
    private String month;

    @Column(name = "theme", length = 200)
    private String theme;

    @Column(name = "programName", length = 100)
    private String programName;

    // VARCHAR,  accepts "TBC"
    @Column(name = "eventDate", length = 30)
    private String eventDate;

    @Column(name = "venue", length = 100)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance", length = 20)
    private AttendingType attendance;

    @Column(name = "purpose", length = 1000)
    private String purpose;

    @Column(name = "projectedIncomeFromAccount", precision = 10, scale = 2)
    private BigDecimal projectedIncomeFromAccount = BigDecimal.ZERO;

    @Column(name = "projectedIncomeSponsorship", precision = 10, scale = 2)
    private BigDecimal projectedIncomeSponsorship = BigDecimal.ZERO;

    @Column(name = "expensePromoMaterial", precision = 10, scale = 2)
    private BigDecimal expensePromoMaterial = BigDecimal.ZERO;

    @Column(name = "expenseDataAirtime", precision = 10, scale = 2)
    private BigDecimal expenseDataAirtime = BigDecimal.ZERO;

    @Column(name = "expenseGifts", precision = 10, scale = 2)
    private BigDecimal expenseGifts = BigDecimal.ZERO;

    @Column(name = "expenseOther", precision = 10, scale = 2)
    private BigDecimal expenseOther = BigDecimal.ZERO;

    @Column(name = "expenseOtherSpecification", length = 200)
    private String expenseOtherSpecification;

    @Column(name = "sdoEventComment", length = 500)
    private String sdoEventComment;

    @Column(name = "sortOrder")
    private Integer sortOrder = 0;

    public static String newID() {
        return UUID.randomUUID().toString();
    }
}