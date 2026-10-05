package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a collaboration invitation sent to another society
 * when an executive submits an event proposal.
 *
 * Mirrors the POAEventCoHost pattern but at the event level.
 * Status flow: PENDING → ACCEPTED or DECLINED.
 */
@Entity
@Table(name = "EventCoHostInvitation")
@Getter
@Setter
@NoArgsConstructor
public class EventCoHostInvitation {

    @Id
    @Column(name = "invitationID", length = 36)
    private String invitationID;

    @Column(name = "eventID", length = 20, nullable = false)
    private String eventID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eventID", insertable = false, updatable = false)
    private Event event;

    @Column(name = "invitedSocietyID", length = 20, nullable = false)
    private String invitedSocietyID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitedSocietyID", insertable = false, updatable = false)
    private Society invitedSociety;

    @Column(name = "invitedByStudentNumber", length = 20, nullable = false)
    private String invitedByStudentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private EventCoHostStatus status = EventCoHostStatus.PENDING;

    @Column(name = "respondedAt")
    private LocalDateTime respondedAt;

    @Column(name = "respondedByStudentNumber", length = 20)
    private String respondedByStudentNumber;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public static String newID() {
        return UUID.randomUUID().toString();
    }
}
