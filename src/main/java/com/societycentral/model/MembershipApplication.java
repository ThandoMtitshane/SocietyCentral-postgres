package com.societycentral.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stores a student's application to join a society before executive review.
 *
 * <p>This entity is deliberately separate from {@link SocietyMember}, which
 * continues to represent approved membership only.</p>
 */
@Entity
@Table(
        name = "MembershipApplication",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_MembershipApplication_TrackingReference",
                columnNames = "trackingReference"),
        indexes = @Index(
                name = "IX_MembershipApplication_Student_Society_Status",
                columnList = "studentNumber,societyID,status")
)
@Getter
@Setter
@NoArgsConstructor
public class MembershipApplication {

    @Id
    @Column(name = "applicationID", length = 36)
    private String applicationID;

    @Column(name = "trackingReference", length = 20, nullable = false)
    private String trackingReference;

    @Column(name = "studentNumber", length = 20, nullable = false)
    private String studentNumber;

    @Column(name = "societyID", length = 20, nullable = false)
    private String societyID;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MembershipApplicationStatus status;

    @Column(name = "motivation", length = 500, nullable = false)
    private String motivation;

    @Column(name = "applicationDate", nullable = false)
    private LocalDateTime applicationDate;

    @Column(name = "lastUpdatedAt", nullable = false)
    private LocalDateTime lastUpdatedAt;

    @Column(name = "reviewedAt")
    private LocalDateTime reviewedAt;

    /** Student number of the executive who reviewed the application. */
    @Column(name = "reviewedBy", length = 20)
    private String reviewedBy;

    @Column(name = "rejectionReason", length = 500)
    private String rejectionReason;

    @Column(name = "withdrawnAt")
    private LocalDateTime withdrawnAt;
}
