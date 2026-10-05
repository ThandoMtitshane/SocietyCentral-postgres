package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plan of Action,  one per society per year.
 * Unique constraint on (societyID, year) enforced at DB level.
 *
 * PK is UUID,  collision-proof, safe under concurrent requests.
 *
 * Status flow:
 *   DRAFT → SUBMITTED → APPROVED
 *                    → REVISION_REQUESTED → (exec edits) → SUBMITTED
 */
@Entity
@Table(name = "POA",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_POA_society_year",
        columnNames = {"societyID", "year"}
    ))
@Getter
@Setter
@NoArgsConstructor
public class POA {

    @Id
    @Column(name = "poaID", length = 36)
    private String poaID;

    @Column(name = "societyID", length = 20, nullable = false)
    private String societyID;

    @Column(name = "submittedByStudentNumber", length = 20)
    private String submittedByStudentNumber;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private POAStatus status = POAStatus.DRAFT;

    @Column(name = "submittedDate")
    private LocalDate submittedDate;

    @Column(name = "reviewedByStaffNumber", length = 20)
    private String reviewedByStaffNumber;

    @Column(name = "reviewNotes", length = 1000)
    private String reviewNotes;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "lastUpdatedAt")
    private LocalDateTime lastUpdatedAt;

    public static String newID() {
        return UUID.randomUUID().toString();
    }
}