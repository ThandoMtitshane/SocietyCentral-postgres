package com.societycentral.model;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "Announcement")
@Data
public class Announcement {

    @Id
    @Column(name = "announcementID", length = 20)
    private String announcementID;

    @Enumerated(EnumType.STRING)
    @Column(name = "targetType", length = 20)
    private TargetType targetType;

    /**
     * Target society for this announcement.
     *
     * Business rules:
     * - Executive announcements are automatically associated with the
     *   executive's own society.
     * - SDO announcements may target a specific supervised society.
     * - A null value indicates all societies the sender is authorised
     *   to communicate with.
     */
    @ManyToOne
    @JoinColumn(name = "societyID")
    private Society society;

    @Column(name = "sentBy", length = 100, nullable = false)
    private String sentBy;

    @ManyToOne
    @JoinColumn(name = "sentBy", referencedColumnName = "email", insertable = false, updatable = false)
    private User sentByUser;

    @Column(name = "subject", length = 100, nullable = false)
    private String subject;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "datePosted")
    private LocalDateTime datePosted;

    @Column(name = "publishAt")
    private LocalDateTime publishAt;

    @Column(name = "expireDate")
    private LocalDateTime expireDate;

    @Column(name = "isRemoved", nullable = false)
    private boolean removed;
    @Column(name = "removedAt")
    private LocalDateTime removedAt;
    @Column(name = "removedBy", length = 100)
    private String removedBy;

    public Announcement() {
    }
}
