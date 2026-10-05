package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Society entity.
 *
 * NOTE: @Setter is NOT applied to 'school' at class level because
 * setSchool() has a side-effect: when school != NONE it auto-fills
 * faculty = school.getFaculty(). That custom setter is written manually below.
 * All other fields use Lombok-generated setters.
 */
@Entity
@Table(name = "Society")
@Getter
@Setter
@NoArgsConstructor
public class Society {

    @Id
    @Column(name = "societyID", length = 20)
    private String societyID;

    @Column(name = "societyName", length = 100, nullable = false)
    private String societyName;

    @Column(name = "acronym", length = 10)
    private String acronym;

    @Column(name = "sdoStaffNumber", length = 20, nullable = false)
    private String sdoStaffNumber;

    @ManyToOne
    @JoinColumn(name = "sdoStaffNumber", referencedColumnName = "staffNumber",
            insertable = false, updatable = false)
    private SDO sdo;

    @Column(name = "numberOfMembers")
    private Integer numberOfMembers = 0;

    @Column(name = "activeStatus")
    private Boolean activeStatus = false;

    @Column(name = "isFlagged")
    private Boolean isFlagged = false;

    @Column(name = "description", length = 2500)
    private String description;

    @Column(name = "vision", length = 500)
    private String vision;

    @Column(name = "mission", length = 500)
    private String mission;

    @Column(name = "yearEstablished")
    private Integer yearEstablished;

    @Column(name = "contactNumber", length = 10)
    private String contactNumber;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "logoUrl", length = 255)
    private String logoUrl;

    @Column(name = "bannerUrl", length = 500)
    private String bannerUrl;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "flaggedDate", nullable=true)
    private LocalDateTime flaggedDate;

    @Column(name = "membershipFee", precision = 10, scale = 2)
    private BigDecimal membershipFee;

    /**
     * The guaranteed annual grant set by the SDO.
     * Only an SDO can update this value.
     * When updated with effectiveImmediately=true, the difference is
     * added/subtracted from currentBalance and a FundTransaction is recorded.
     * Mandatory at registration (defaults to 0.0 in the frontend form).
     */
    @Column(name = "annualBudgetAllocation", precision = 10, scale = 2)
    private BigDecimal annualBudgetAllocation = BigDecimal.ZERO;

    /**
     * Running available funds for this society.
     * Starts equal to annualBudgetAllocation at registration.
     * Increases: new member joins (+ membershipFee), annual allocation added.
     * Decreases: BudgetRequest approved (- amount).
     * Never resets to 0,  unspent funds carry forward.
     * Every change is recorded in FundTransaction.
     */
    @Column(name = "currentBalance", precision = 10, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "campus", length = 30)
    private Campus campus;

    @Enumerated(EnumType.STRING)
    @Column(name = "societyType", length = 40)
    private SocietyType societyType;

    // school has a custom setter - suppress Lombok's generated one
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "school", length = 60)
    private School school = School.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "faculty", length = 60)
    private Faculty faculty = Faculty.NONE;

    @Column(name = "tiktokURL", length = 200)
    private String tiktokURL;

    @Column(name = "facebookURL", length = 200)
    private String facebookURL;

    @Column(name = "instagramURL", length = 200)
    private String instagramURL;

    /**
     * Custom setter: when school != NONE, auto-fills faculty to match.
     * To scope a society to a FACULTY ONLY (e.g. a Law faculty society
     * that is not tied to one specific department), pass School.NONE here
     * and call setFaculty() separately with the desired faculty.
     */
    public void setSchool(School school) {
        this.school = school;
        if (school != null && school != School.NONE) {
            this.faculty = school.getFaculty();
        }
    }

    /** Convenience alias used in services and DTOs. */
    public String getName() {
        return societyName;
    }
}
